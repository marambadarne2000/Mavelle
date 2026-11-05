package com.maramigo.catalog;

import com.maramigo.support.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

/** Real manufacturer identity with explicitly synthetic commerce data. @author Maram Abbas */
@ActiveProfiles({"demo","real-catalog"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.servlet.session.cookie.secure=false")
class ManufacturerCatalogIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @Autowired TestRestTemplate http;
 @Autowired JdbcTemplate jdbc;
 @Autowired DataSource datasource;
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

 @Test void realBrandsHaveCompleteProductSourcesAndSeparateCatalogs(){
  var companies=http.getForObject("/api/companies",JsonNode.class);
  assertThat(companies.size()).isEqualTo(8);
  int references=0;
  for(var company:companies){
   if(company.get("sourceUrl").isNull())continue;
   references++;
   assertThat(company.get("demo").asBoolean()).isTrue();
   var catalog=http.getForObject("/api/products?companyId="+company.get("id").asLong(),JsonNode.class);
   assertThat(catalog.get("total").asInt()).isEqualTo(4);
   for(var product:catalog.get("items")){
    assertThat(product.get("companyId").asLong()).isEqualTo(company.get("id").asLong());
    assertThat(product.get("sourceUrl").asText()).startsWith("https://");
    assertThat(product.get("referenceImageUrl").asText()).startsWith("/catalog/ref-");
    assertThat(product.get("specifications").size()).isGreaterThanOrEqualTo(3);
    assertThat(product.get("sourceCheckedOn").asText()).isEqualTo("2026-11-05");
    var detail=http.getForObject("/api/products/"+product.get("id").asLong(),JsonNode.class);
    assertThat(detail.get("specifications")).isEqualTo(product.get("specifications"));
   }
  }
  assertThat(references).isEqualTo(5);
  var featured=http.getForObject("/api/products?size=48",JsonNode.class);
  assertThat(featured.get("total").asInt()).isEqualTo(24);
  assertThat(featured.get("items").get(0).get("sourceUrl").isNull()).isFalse();
  var search=http.getForObject("/api/products?search=10313",JsonNode.class);
  assertThat(search.get("total").asInt()).isEqualTo(1);
  assertThat(search.get("items").get(0).get("specifications").get("Pieces").asText()).isEqualTo("939");
  assertThat(jdbc.queryForObject("SELECT name FROM products WHERE sku='MG-001'",String.class)).isEqualTo("The Silk Pillow");
  assertThat(http.getForObject("/api/products?category=Electronics&size=48",JsonNode.class).get("total").asInt()).isEqualTo(12);
 }

 @Test void realReferenceProductsParticipateInMixedStoreDemoCheckout()throws Exception{
  var customer=new com.maramigo.support.SessionClient(port,json);
  String email="reference-"+java.util.UUID.randomUUID()+"@example.test";
  String password="Reference-test-password-2026";
  assertThat(customer.send("POST","/api/auth/register",java.util.Map.of("displayName","Reference Buyer","email",email,"password",password),true).statusCode()).isEqualTo(201);
  customer.login(email,password);
  var apple=http.getForObject("/api/products?search=iPhone",JsonNode.class).get("items").get(0);
  var lego=http.getForObject("/api/products?search=10313",JsonNode.class).get("items").get(0);
  var items=java.util.stream.Stream.of(apple,lego).map(p->java.util.Map.of("productId",p.get("id").asLong(),"quantity",1,"expectedPrice",p.get("price").asText())).toList();
  var request=java.util.Map.of("items",items,"shipping",java.util.Map.of("fullName","Reference Buyer","phone","0500000000","address","1 Test St","city","Test","postalCode","0000000","country","IL"),"demoPaymentOutcome","SUCCESS");
  var response=customer.send("POST","/api/orders",request,true,java.util.UUID.randomUUID().toString());
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
  String number=json.readTree(response.body()).get("orderNumber").asText();
  assertThat(customer.get("/api/orders/"+number).statusCode()).isEqualTo(200);
  for(var product:java.util.List.of(apple,lego)){
   assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,product.get("id").asLong())).isEqualTo(product.get("stockQuantity").asInt()-1);
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_orders m JOIN orders o ON o.id=m.order_id WHERE o.order_number=?",Integer.class,number)).isEqualTo(2);
 }

 @Test void reseedingNeverResetsStockOrOverwritesAnExistingProduct(){
  String sku="REF-IK-30449908";
  jdbc.update("UPDATE products SET stock_quantity=7,price=51 WHERE sku=?",sku);
  new ResourceDatabasePopulator(new ClassPathResource("db/real-catalog/R__manufacturer_products.sql")).execute(datasource);
  assertThat(jdbc.queryForObject("SELECT stock_quantity FROM products WHERE sku=?",Integer.class,sku)).isEqualTo(7);
  assertThat(jdbc.queryForObject("SELECT price FROM products WHERE sku=?",java.math.BigDecimal.class,sku)).isEqualByComparingTo("51");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE source_url IS NOT NULL",Integer.class)).isEqualTo(20);
  jdbc.update("UPDATE products SET stock_quantity=12,price=49 WHERE sku=?",sku);
 }
}
