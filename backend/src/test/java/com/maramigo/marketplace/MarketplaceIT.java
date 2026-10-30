package com.maramigo.marketplace;
import com.maramigo.support.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Two independent stores, real HTTP sessions, PostgreSQL and mixed fulfilment. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","maramigo.login-limit=300","DEMO_ADMIN_EMAIL=owner@example.test","DEMO_ADMIN_PASSWORD=Owner-test-password-2026"})
class MarketplaceIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 static final String PASSWORD="Market-test-password-2026";
 SessionClient owner()throws Exception{var c=new SessionClient(port,json);c.login("owner@example.test","Owner-test-password-2026");return c;}
 Map<String,Object> form(String email){return Map.of("ownerName","Synthetic Merchant","businessName","Synthetic shop "+UUID.randomUUID(),"businessType","Makeup","registrationNumber","DEMO-ONLY","phone","0500000000","email",email,"description","Synthetic integration store","password",PASSWORD,"confirmPassword",PASSWORD,"termsAccepted",true);}
 record Merchant(SessionClient client,long company,String email){}
 Merchant merchant(boolean approve)throws Exception{String email="merchant-"+UUID.randomUUID()+"@example.test";var c=new SessionClient(port,json);var registered=c.send("POST","/api/auth/business-register",form(email),true);assertThat(registered.statusCode()).as(registered.body()).isEqualTo(201);c.login(email,PASSWORD);long id=json.readTree(c.get("/api/merchant/store").body()).get("companyId").asLong();if(approve)assertThat(owner().send("PATCH","/api/admin/stores/"+id,Map.of("status","APPROVED","reason","Synthetic approval"),true).statusCode()).isEqualTo(200);return new Merchant(c,id,email);}
 SessionClient customer()throws Exception{var c=new SessionClient(port,json);String email="buyer-"+UUID.randomUUID()+"@example.test";assertThat(c.send("POST","/api/auth/register",Map.of("email",email,"password",PASSWORD),true).statusCode()).isEqualTo(201);c.login(email,PASSWORD);return c;}
 Map<String,Object> productData(long company){return Map.of("sku","MR-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT),"name","Synthetic marketplace item","description","Test product","category","Makeup","price","10.00","active",true,"companyId",company);}
 JsonNode product(Merchant m,long forgedCompany)throws Exception{var r=m.client().send("POST","/api/merchant/products",Map.of("product",productData(forgedCompany),"initialStock",5),true);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 String checkout(SessionClient c,JsonNode... products)throws Exception{var lines=Arrays.stream(products).map(p->Map.of("productId",p.get("id").asLong(),"quantity",1,"expectedPrice","10.00")).toList();var shipping=Map.of("fullName","Synthetic Buyer","phone","0500000000","address","1 Test St","city","Test","postalCode","0000000","country","IL");var r=c.send("POST","/api/orders",Map.of("shipping",shipping,"items",lines),true,UUID.randomUUID().toString());assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body()).get("orderNumber").asText();}
 @Test void publicApplicationRequiresCsrfValidConfirmationAndTermsAndCreatesPendingMerchantOnly()throws Exception{
  var guest=new SessionClient(port,json);var data=new HashMap<>(form("join-"+UUID.randomUUID()+"@example.test"));
  assertThat(guest.send("POST","/api/auth/business-register",data,false).statusCode()).isEqualTo(403);
  data.put("confirmPassword","Wrong-confirmation");assertThat(guest.send("POST","/api/auth/business-register",data,true).statusCode()).isEqualTo(400);data.put("confirmPassword",PASSWORD);data.put("termsAccepted",false);assertThat(guest.send("POST","/api/auth/business-register",data,true).statusCode()).isEqualTo(400);data.put("termsAccepted",true);data.put("role","ADMIN");var response=guest.send("POST","/api/auth/business-register",data,true);assertThat(response.statusCode()).isEqualTo(201);assertThat(json.readTree(response.body()).get("role").asText()).isEqualTo("MERCHANT");guest.login((String)data.get("email"),PASSWORD);var store=json.readTree(guest.get("/api/merchant/store").body());assertThat(store.get("status").asText()).isEqualTo("PENDING");assertThat(guest.get("/api/companies").body()).doesNotContain(store.get("slug").asText());
  assertThat(guest.send("POST","/api/merchant/products",Map.of("product",productData(1),"initialStock",2),true).statusCode()).isEqualTo(403);
  assertThat(guest.get("/api/admin/stores").statusCode()).isEqualTo(403);assertThat(guest.get("/api/customer/favorites").statusCode()).isEqualTo(403);
 }
 @Test void duplicateEmailDoesNotLeaveAnOrphanStore()throws Exception{var m=merchant(false);long count=jdbc.queryForObject("SELECT count(*) FROM companies",Long.class);assertThat(new SessionClient(port,json).send("POST","/api/auth/business-register",form(m.email()),true).statusCode()).isEqualTo(409);assertThat(jdbc.queryForObject("SELECT count(*) FROM companies",Long.class)).isEqualTo(count);}
 @Test void productsAreTenantScopedEvenWhenCompanyIdIsForged()throws Exception{
  var a=merchant(true);var b=merchant(true);var p=product(a,b.company());long id=p.get("id").asLong();assertThat(p.get("companyId").asLong()).isEqualTo(a.company());
  assertThat(b.client().get("/api/merchant/products").body()).doesNotContain(p.get("sku").asText());
  assertThat(b.client().send("PUT","/api/merchant/products/"+id,Map.of("product",productData(b.company()),"version",0),true).statusCode()).isEqualTo(404);
  assertThat(b.client().send("PATCH","/api/merchant/products/"+id+"/stock",Map.of("delta",1,"reason","Forbidden","version",0),true).statusCode()).isEqualTo(404);
  assertThat(a.client().send("PATCH","/api/merchant/products/"+id+"/stock",Map.of("delta",2,"reason","Test delivery","version",0),true).statusCode()).isEqualTo(200);
  assertThat(a.client().send("PATCH","/api/merchant/products/"+id+"/stock",Map.of("delta",1,"reason","Stale","version",0),true).statusCode()).isEqualTo(409);
 }
 @Test void suspensionRemovesEveryPublicCatalogPathAndBlocksAnExistingBasket()throws Exception{
  var m=merchant(true);var p=product(m,1);long id=p.get("id").asLong();assertThat(new SessionClient(port,json).get("/api/products/"+id).statusCode()).isEqualTo(200);
  owner().send("PATCH","/api/admin/stores/"+m.company(),Map.of("status","SUSPENDED","reason","Synthetic suspension"),true);
  var guest=new SessionClient(port,json);assertThat(guest.get("/api/companies").body()).doesNotContain("\"id\":"+m.company()+",");assertThat(guest.get("/api/products/"+id).statusCode()).isEqualTo(404);assertThat(guest.get("/api/products/lookup?ids="+id).body()).isEqualTo("[]");assertThat(json.readTree(guest.get("/api/products?companyId="+m.company()).body()).get("total").asInt()).isZero();
  var c=customer();var shipping=Map.of("fullName","Test","phone","0500000000","address","Test","city","Test","postalCode","0000000","country","IL");var r=c.send("POST","/api/orders",Map.of("shipping",shipping,"items",List.of(Map.of("productId",id,"quantity",1,"expectedPrice","10.00"))),true,UUID.randomUUID().toString());assertThat(r.statusCode()).isEqualTo(409);assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,id)).isEqualTo(5);
 }
 @Test void mixedOrderExposesOnlyOwnLinesAndAggregatesStoreDeliveryWithoutLeakingOtherCustomers()throws Exception{
  var a=merchant(true);var b=merchant(true);var outsider=merchant(true);var pa=product(a,1);var pb=product(b,1);var c=customer();String number=checkout(c,pa,pb),path="/api/merchant/orders/"+number;
  var detail=a.client().get(path);assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).contains(pa.get("sku").asText()).doesNotContain(pb.get("sku").asText(),"\"total\":20");assertThat(outsider.client().get(path).statusCode()).isEqualTo(404);
  assertThat(a.client().send("PATCH",path+"/status",Map.of("status","SHIPPED","version",0,"reason","A dispatch"),true).statusCode()).isEqualTo(200);
  assertThat(a.client().send("PATCH",path+"/status",Map.of("status","DELIVERED","version",0,"reason","Stale"),true).statusCode()).isEqualTo(409);
  a.client().send("PATCH",path+"/status",Map.of("status","DELIVERED","version",1,"reason","A delivered"),true);
  assertThat(json.readTree(c.get("/api/orders/"+number).body()).get("status").asText()).isEqualTo("SHIPPED");
  b.client().send("PATCH",path+"/status",Map.of("status","SHIPPED","version",0,"reason","B dispatch"),true);b.client().send("PATCH",path+"/status",Map.of("status","DELIVERED","version",1,"reason","B delivered"),true);
  assertThat(json.readTree(c.get("/api/orders/"+number).body()).get("status").asText()).isEqualTo("DELIVERED");assertThat(json.readTree(c.get("/api/customer/deliveries/"+number).body()).size()).isEqualTo(2);assertThat(customer().get("/api/customer/deliveries/"+number).body()).isEqualTo("[]");
  jdbc.update("UPDATE products SET company_id=? WHERE id=?",b.company(),pa.get("id").asLong());assertThat(a.client().get(path).body()).contains(pa.get("sku").asText());
 }
 @Test void favoritesAndProfileArePrivateAndCanBeUpdatedRemovedAndCleared()throws Exception{
  var a=customer();var b=customer();assertThat(a.send("PUT","/api/customer/profile",Map.of("fullName","Private Test","phone","0500000000","address","Test street","city","Test city"),true).statusCode()).isEqualTo(200);assertThat(b.get("/api/customer/profile").body()).doesNotContain("Private Test");
  assertThat(a.send("PUT","/api/customer/favorites/1",null,true).statusCode()).isEqualTo(200);assertThat(a.get("/api/customer/favorites").body()).contains("The Silk Pillow");assertThat(b.get("/api/customer/favorites").body()).isEqualTo("[]");a.send("DELETE","/api/customer/favorites/1",null,true);assertThat(a.get("/api/customer/favorites").body()).isEqualTo("[]");a.send("PUT","/api/customer/favorites/1",null,true);a.send("DELETE","/api/customer/favorites",null,true);assertThat(a.get("/api/customer/favorites").body()).isEqualTo("[]");
 }
 @Test void conversationsRequirePurchaseAndAreBoundToMerchantAndCustomer()throws Exception{
  var a=merchant(true);var b=merchant(true);var c=customer();long cid=json.readTree(c.get("/api/auth/me").body()).get("id").asLong();String customerPath="/api/customer/stores/"+a.company()+"/messages",merchantPath="/api/merchant/conversations/"+cid;
  assertThat(c.send("POST",customerPath,Map.of("body","Not a buyer"),true).statusCode()).isEqualTo(403);checkout(c,product(a,1));assertThat(c.send("POST",customerPath,Map.of("body","Synthetic delivery question"),true).statusCode()).isEqualTo(200);assertThat(a.client().get(merchantPath).body()).contains("Synthetic delivery question");assertThat(b.client().get(merchantPath).statusCode()).isEqualTo(403);assertThat(customer().get(customerPath).statusCode()).isEqualTo(403);assertThat(a.client().send("POST",merchantPath,Map.of("body","Synthetic reply"),true).statusCode()).isEqualTo(200);assertThat(c.get(customerPath).body()).contains("Synthetic reply");
 }
 @Test void reviewsRequireOwnDeliveredPurchaseAndSupportUpdateAndDelete()throws Exception{
  var m=merchant(true);var c=customer();String path="/api/customer/stores/"+m.company()+"/review";var review=Map.of("rating",5,"body","Synthetic review");assertThat(c.send("PUT",path,review,true).statusCode()).isEqualTo(403);String number=checkout(c,product(m,1));assertThat(c.send("PUT",path,review,true).statusCode()).isEqualTo(403);m.client().send("PATCH","/api/merchant/orders/"+number+"/status",Map.of("status","SHIPPED","version",0,"reason","Test"),true);m.client().send("PATCH","/api/merchant/orders/"+number+"/status",Map.of("status","DELIVERED","version",1,"reason","Test"),true);assertThat(c.send("PUT",path,review,true).statusCode()).isEqualTo(200);assertThat(c.send("PUT",path,Map.of("rating",4,"body","Updated"),true).statusCode()).isEqualTo(200);assertThat(c.get(path).body()).contains("Updated");assertThat(m.client().get("/api/merchant/reviews").body()).contains("Updated");assertThat(merchant(true).client().get("/api/merchant/reviews").body()).isEqualTo("[]");c.send("DELETE",path,null,true);assertThat(c.get(path).body()).isEqualTo("[]");
 }
 @Test void customerOffersOnlyIncludeActiveApprovedPurchasedStoresAndMerchantCannotDeleteAnotherOffer()throws Exception{
  var a=merchant(true);var b=merchant(true);var c=customer();checkout(c,product(a,1));var data=Map.of("title","Synthetic buyer offer","body","Announcement only","startsAt","2026-01-01T00:00:00Z","endsAt","2099-01-01T00:00:00Z");assertThat(a.client().send("POST","/api/merchant/offers",data,true).statusCode()).isEqualTo(200);assertThat(c.get("/api/customer/offers").body()).contains("Synthetic buyer offer");assertThat(customer().get("/api/customer/offers").body()).isEqualTo("[]");long id=json.readTree(a.client().get("/api/merchant/offers").body()).get(0).get("id").asLong();assertThat(b.client().send("DELETE","/api/merchant/offers/"+id,null,true).statusCode()).isEqualTo(404);jdbc.update("UPDATE store_offers SET ends_at=CURRENT_TIMESTAMP-interval '1 second',starts_at=CURRENT_TIMESTAMP-interval '1 day' WHERE id=?",id);assertThat(c.get("/api/customer/offers").body()).isEqualTo("[]");
  var summary=json.readTree(a.client().get("/api/merchant/summary").body());assertThat(summary.get("billingModel").asText()).isEqualTo("SUBSCRIPTION_AND_COMMISSION");assertThat(summary.get("paymentsConnected").asBoolean()).isFalse();
 }
}
