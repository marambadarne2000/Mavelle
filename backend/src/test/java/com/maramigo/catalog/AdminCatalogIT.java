package com.maramigo.catalog;

import com.maramigo.support.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.servlet.session.cookie.secure=false","maramigo.login-limit=100",
    "DEMO_ADMIN_EMAIL=products-admin@example.test","DEMO_ADMIN_PASSWORD=Product-admin-test-password"})
class AdminCatalogIT {
    static final TestPostgres db=new TestPostgres();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws Exception { db.configure(properties); }
    @AfterAll static void cleanup() throws Exception { db.close(); }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    SessionClient admin() throws Exception { var client=new SessionClient(port,json); client.login("products-admin@example.test","Product-admin-test-password"); return client; }
    Map<String,Object> data(String sku,boolean active) { return Map.of("sku",sku,"name","QA Silk Piece","description","Integration test product","category","Living","price","35.50","active",active); }
    JsonNode create(SessionClient client,int stock) throws Exception {
        var response=client.send("POST","/api/admin/products",Map.of("product",data("QA-"+UUID.randomUUID(),true),"initialStock",stock),true);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();
        return json.readTree(response.body());
    }
    @Test void administratorOnlyAndCsrfRequired() throws Exception {
        var anonymous=new SessionClient(port,json);
        assertThat(anonymous.get("/api/admin/products").statusCode()).isEqualTo(401);
        assertThat(anonymous.send("POST","/api/admin/products",Map.of(),true).statusCode()).isEqualTo(401);
        String email=UUID.randomUUID()+"@example.test";
        assertThat(anonymous.send("POST","/api/auth/register",Map.of("email",email,"password","Customer-product-test-password"),true).statusCode()).isEqualTo(201);
        anonymous.login(email,"Customer-product-test-password");
        assertThat(anonymous.get("/api/admin/products").statusCode()).isEqualTo(403);
        for (String method : List.of("POST","PUT","PATCH")) {
            String path=method.equals("POST")?"/api/admin/products":method.equals("PUT")?"/api/admin/products/1":"/api/admin/products/1/stock";
            assertThat(anonymous.send(method,path,Map.of(),true).statusCode()).isEqualTo(403);
        }
        assertThat(admin().send("POST","/api/admin/products",Map.of(),false).statusCode()).isEqualTo(403);
    }
    @Test void createsEditsAndDeactivatesWithoutChangingStock() throws Exception {
        var client=admin(); var created=create(client,3); long id=created.get("id").asLong();
        assertThat(created.get("stockQuantity").asInt()).isEqualTo(3);
        String sku=created.get("sku").asText();
        var edit=new HashMap<String,Object>();
        var fields=new HashMap<String,Object>(data(sku,false)); fields.put("name","Changed title"); fields.put("price","49.25");
        edit.put("product",fields); edit.put("version",created.get("version").asLong());
        edit.put("stockQuantity",999); // Over-posting cannot alter stock through metadata edits.
        var response=client.send("PUT","/api/admin/products/"+id,edit,true);
        assertThat(response.statusCode()).isEqualTo(200);
        var updated=json.readTree(response.body());
        assertThat(updated.get("stockQuantity").asInt()).isEqualTo(3);
        assertThat(updated.get("price").decimalValue()).isEqualByComparingTo("49.25");
        assertThat(updated.get("version").asLong()).isGreaterThan(created.get("version").asLong());
        assertThat(new SessionClient(port,json).get("/api/products/"+id).statusCode()).isEqualTo(404);
        var stored=json.readTree(client.get("/api/admin/products/"+id).body());
        assertThat(stored.get("name").asText()).isEqualTo("Changed title");
        assertThat(stored.get("active").asBoolean()).isFalse();
        assertThat(client.send("DELETE","/api/admin/products/"+id,Map.of(),true).statusCode()).isEqualTo(405);
        assertThat(client.send("PUT","/api/admin/products/"+id,edit,true).statusCode()).isEqualTo(409);
    }
    @Test void validatesProductsAndRejectsDuplicateSkuAndFractionalQuantities() throws Exception {
        var client=admin(); var existing=create(client,0);
        var duplicate=Map.of("product",data(existing.get("sku").asText().toLowerCase(Locale.ROOT),true),"initialStock",0);
        assertThat(client.send("POST","/api/admin/products",duplicate,true).statusCode()).isEqualTo(409);
        for (Object badStock : List.of(-1,1.5,1000001)) {
            assertThat(client.send("POST","/api/admin/products",Map.of("product",data("QA-"+UUID.randomUUID(),true),"initialStock",badStock),true).statusCode()).isEqualTo(400);
        }
        for (String badPrice : List.of("0","-1","1.001","10000000000")) {
            var fields=new HashMap<String,Object>(data("QA-"+UUID.randomUUID(),true)); fields.put("price",badPrice);
            assertThat(client.send("POST","/api/admin/products",Map.of("product",fields,"initialStock",0),true).statusCode()).isEqualTo(400);
        }
        var fields=new HashMap<String,Object>(data("QA-VALIDATION",true)); fields.put("name","   ");
        assertThat(client.send("POST","/api/admin/products",Map.of("product",fields,"initialStock",0),true).statusCode()).isEqualTo(400);
        assertThat(client.get("/api/admin/products?size=500").statusCode()).isEqualTo(400);
        assertThat(client.get("/api/admin/products/999999").statusCode()).isEqualTo(404);
    }
    @Test void stockAdjustmentsAreAuditedAndInvalidChangesLeaveNoMovement() throws Exception {
        var client=admin(); var product=create(client,2); long id=product.get("id").asLong();
        var response=client.send("PATCH","/api/admin/products/"+id+"/stock",Map.of("delta",-1,"reason","Damaged demo unit","version",product.get("version").asLong()),true);
        assertThat(response.statusCode()).isEqualTo(200); var updated=json.readTree(response.body());
        assertThat(updated.get("stockQuantity").asInt()).isEqualTo(1);
        long version=updated.get("version").asLong();
        assertThat(client.send("PATCH","/api/admin/products/"+id+"/stock",Map.of("delta",-2,"reason","Invalid change","version",version),true).statusCode()).isEqualTo(409);
        assertThat(client.send("PATCH","/api/admin/products/"+id+"/stock",Map.of("delta",0,"reason","Zero","version",version),true).statusCode()).isEqualTo(400);
        assertThat(client.send("PATCH","/api/admin/products/"+id+"/stock",Map.of("delta",1,"reason"," ","version",version),true).statusCode()).isEqualTo(400);
        var history=json.readTree(client.get("/api/admin/products/"+id+"/inventory-movements").body());
        assertThat(history.get("total").asInt()).isEqualTo(2);
        assertThat(history.get("items").get(0).get("delta").asInt()).isEqualTo(-1);
        assertThat(history.get("items").get(0).get("actorId").asLong()).isPositive();
        assertThat(history.get("items").get(1).get("reason").asText()).isEqualTo("Initial stock");
        assertThat(json.readTree(client.get("/api/admin/products/"+id).body()).get("stockQuantity").asInt()).isEqualTo(1);
    }
    @Test void simultaneousStockChangesWithSameVersionHaveOnlyOneWinner() throws Exception {
        var first=admin(); var second=admin(); var product=create(first,1); long id=product.get("id").asLong();
        var change=Map.of("delta",-1,"reason","Concurrent stock correction","version",product.get("version").asLong());
        var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> one=()-> { gate.await(); return first.send("PATCH","/api/admin/products/"+id+"/stock",change,true).statusCode(); };
            Callable<Integer> two=()-> { gate.await(); return second.send("PATCH","/api/admin/products/"+id+"/stock",change,true).statusCode(); };
            var a=pool.submit(one); var b=pool.submit(two); gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(json.readTree(first.get("/api/admin/products/"+id).body()).get("stockQuantity").asInt()).isZero();
            assertThat(json.readTree(first.get("/api/admin/products/"+id+"/inventory-movements").body()).get("total").asInt()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
}
