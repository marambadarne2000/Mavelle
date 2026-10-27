package com.maramigo.orders;

import com.maramigo.support.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL transactions and independent HTTP sessions. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "server.servlet.session.cookie.secure=false","maramigo.login-limit=200",
 "DEMO_ADMIN_EMAIL=orders-admin@example.test","DEMO_ADMIN_PASSWORD=Orders-admin-password-2026"})
class OrderIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @LocalServerPort int port;
 @Autowired ObjectMapper json;
 @Autowired JdbcTemplate jdbc;
 final Map<String,Object> shipping=Map.of("fullName","Demo Customer","phone","0500000000","address","10 Demo Street","city","Demo City","postalCode","0000000","country","IL");
 SessionClient customer()throws Exception{var c=new SessionClient(port,json);String email=UUID.randomUUID()+"@example.test";assertThat(c.send("POST","/api/auth/register",Map.of("email",email,"password","Order-customer-password"),true).statusCode()).isEqualTo(201);c.login(email,"Order-customer-password");return c;}
 SessionClient admin()throws Exception{var c=new SessionClient(port,json);c.login("orders-admin@example.test","Orders-admin-password-2026");return c;}
 long product(int stock)throws Exception{var fields=Map.of("sku","ORD-"+UUID.randomUUID(),"name","Order fixture","description","Test only","category","Living","price","10.00","active",true,"companyId",2);var response=admin().send("POST","/api/admin/products",Map.of("product",fields,"initialStock",stock),true);assertThat(response.statusCode()).isEqualTo(201);return json.readTree(response.body()).get("id").asLong();}
 Map<String,Object> line(long id,int quantity,String price){return Map.of("productId",id,"quantity",quantity,"expectedPrice",price);}
 Map<String,Object> payload(List<Map<String,Object>> items){return Map.of("items",items,"shipping",shipping);}
 int stock(long id){return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=?",Integer.class,id);}
 String placed(SessionClient user,long id,int quantity)throws Exception{
  var response=user.send("POST","/api/orders",payload(List.of(line(id,quantity,"10.00"))),true,UUID.randomUUID().toString());
  assertThat(response.statusCode()).isEqualTo(200);return json.readTree(response.body()).get("orderNumber").asText();
 }
 Map<String,Object> change(String status,long version){return Map.of("status",status,"version",version,"reason","Synthetic fulfilment test");}
 @Test void administratorTransitionsAreAuditedAndStaleUpdatesAreRejected()throws Exception{
  var user=customer();var manager=admin();long id=product(4);String number=placed(user,id,1);String path="/api/admin/orders/"+number;
  assertThat(json.readTree(manager.get(path).body()).get("version").asLong()).isZero();
  assertThat(manager.send("PATCH",path+"/status",change("DELIVERED",0),true).statusCode()).isEqualTo(409);
  assertThat(manager.send("PATCH",path+"/status",change("SHIPPED",0),true).statusCode()).isEqualTo(200);
  assertThat(manager.send("PATCH",path+"/status",change("DELIVERED",0),true).statusCode()).isEqualTo(409);
  assertThat(manager.send("PATCH",path+"/status",change("CANCELLED",1),true).statusCode()).isEqualTo(409);
  assertThat(manager.send("PATCH",path+"/status",change("DELIVERED",1),true).statusCode()).isEqualTo(200);
  assertThat(manager.send("PATCH",path+"/status",change("SHIPPED",2),true).statusCode()).isEqualTo(409);
  var detail=json.readTree(manager.get(path).body());assertThat(detail.get("history").size()).isEqualTo(2);assertThat(stock(id)).isEqualTo(3);
  assertThat(json.readTree(user.get("/api/orders/"+number).body()).get("status").asText()).isEqualTo("DELIVERED");
 }
 @Test void adminOrderPermissionsFilteringAndValidation()throws Exception{
  var user=customer();var manager=admin();long id=product(2);String number=placed(user,id,1);String path="/api/admin/orders/"+number;
  assertThat(new SessionClient(port,json).get("/api/admin/orders").statusCode()).isEqualTo(401);
  assertThat(user.get(path).statusCode()).isEqualTo(403);assertThat(user.send("PATCH",path+"/status",change("CANCELLED",0),true).statusCode()).isEqualTo(403);
  assertThat(manager.send("PATCH",path+"/status",change("SHIPPED",0),false).statusCode()).isEqualTo(403);
  assertThat(manager.send("PATCH",path+"/status",Map.of("status","SHIPPED","version",0,"reason"," "),true).statusCode()).isEqualTo(400);
  var list=json.readTree(manager.get("/api/admin/orders?status=PENDING&search="+number).body());assertThat(list.get("total").asInt()).isEqualTo(1);
  assertThat(json.readTree(manager.get("/api/admin/orders?status=DELIVERED&search="+number).body()).get("total").asInt()).isZero();
  assertThat(manager.get("/api/admin/orders?status=INVALID").statusCode()).isEqualTo(400);
 }
 @Test void cancellationRestoresInactiveProductsOnceAndRetainsSnapshot()throws Exception{
  var user=customer();var manager=admin();long id=product(5);String number=placed(user,id,2);jdbc.update("UPDATE products SET active=false WHERE id=?",id);
  String path="/api/admin/orders/"+number+"/status";
  assertThat(manager.send("PATCH",path,change("CANCELLED",0),true).statusCode()).isEqualTo(200);
  assertThat(manager.send("PATCH",path,change("CANCELLED",0),true).statusCode()).isEqualTo(200);assertThat(stock(id)).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=? AND quantity_delta>0 AND order_id IS NOT NULL",Long.class,id)).isEqualTo(1);
  var snapshot=json.readTree(user.get("/api/orders/"+number).body());assertThat(snapshot.get("status").asText()).isEqualTo("CANCELLED");assertThat(snapshot.get("total").decimalValue()).isEqualByComparingTo("20.00");
 }
 @Test void concurrentCancellationAndDispatchHaveOnlyOneWinner()throws Exception{
  var user=customer();var manager=admin();long id=product(3);String number=placed(user,id,2);String path="/api/admin/orders/"+number+"/status";
  var pool=Executors.newFixedThreadPool(2);try{
   var a=pool.submit(()->manager.send("PATCH",path,change("CANCELLED",0),true));var b=pool.submit(()->manager.send("PATCH",path,change("SHIPPED",0),true));
   assertThat(List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
   String status=json.readTree(user.get("/api/orders/"+number).body()).get("status").asText();assertThat(stock(id)).isEqualTo(status.equals("CANCELLED")?3:1);
  }finally{pool.shutdownNow();}
 }
 @Test void concurrentCancellationsRestoreOnceAndOverflowRollsBackEveryLine()throws Exception{
  var user=customer();var manager=admin();long id=product(3);String number=placed(user,id,1);String path="/api/admin/orders/"+number+"/status";
  var pool=Executors.newFixedThreadPool(2);try{
   var a=pool.submit(()->manager.send("PATCH",path,change("CANCELLED",0),true));var b=pool.submit(()->manager.send("PATCH",path,change("CANCELLED",0),true));
   assertThat(a.get().statusCode()).isEqualTo(200);assertThat(b.get().statusCode()).isEqualTo(200);assertThat(stock(id)).isEqualTo(3);
   assertThat(json.readTree(manager.get("/api/admin/orders/"+number).body()).get("history").size()).isEqualTo(1);
  }finally{pool.shutdownNow();}
  long first=product(2),second=product(2);var response=user.send("POST","/api/orders",payload(List.of(line(first,1,"10.00"),line(second,1,"10.00"))),true,UUID.randomUUID().toString());String other=json.readTree(response.body()).get("orderNumber").asText();
  jdbc.update("UPDATE products SET stock_quantity=1000000 WHERE id=?",second);
  assertThat(manager.send("PATCH","/api/admin/orders/"+other+"/status",change("CANCELLED",0),true).statusCode()).isEqualTo(409);
  assertThat(stock(first)).isEqualTo(1);assertThat(stock(second)).isEqualTo(1000000);
  assertThat(json.readTree(user.get("/api/orders/"+other).body()).get("status").asText()).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=? AND quantity_delta>0 AND order_id IS NOT NULL",Long.class,first)).isZero();
 }
 @Test void dashboardUsesJerusalemDateBoundariesAndExcludesCancelledValue()throws Exception{
  var user=customer();var manager=admin();long id=product(10);String a=placed(user,id,1),b=placed(user,id,2),outside=placed(user,id,1);
  jdbc.update("UPDATE orders SET created_at='2020-01-01T22:30:00Z' WHERE order_number=?",a);
  jdbc.update("UPDATE orders SET created_at='2020-01-02T21:59:59Z' WHERE order_number=?",b);
  jdbc.update("UPDATE orders SET created_at='2020-01-02T22:00:00Z' WHERE order_number=?",outside);
  assertThat(manager.send("PATCH","/api/admin/orders/"+a+"/status",change("CANCELLED",0),true).statusCode()).isEqualTo(200);
  var result=json.readTree(manager.get("/api/admin/dashboard?from=2020-01-02&to=2020-01-03&threshold=5").body());
  assertThat(result.get("orders").asLong()).isEqualTo(3);assertThat(result.get("cancelled").asLong()).isEqualTo(1);assertThat(result.get("amount").decimalValue()).isEqualByComparingTo("30");
  var day=result.get("days").get(0);assertThat(day.get("date").asText()).isEqualTo("2020-01-02");assertThat(day.get("orders").asLong()).isEqualTo(2);assertThat(day.get("amount").decimalValue()).isEqualByComparingTo("20");
  assertThat(result.get("timezone").asText()).isEqualTo("Asia/Jerusalem");
 }
 @Test void dashboardIsAdminOnlyValidatesRangesAndZeroFillsEmptyDates()throws Exception{
  var manager=admin();assertThat(customer().get("/api/admin/dashboard").statusCode()).isEqualTo(403);assertThat(new SessionClient(port,json).get("/api/admin/dashboard").statusCode()).isEqualTo(401);
  assertThat(manager.get("/api/admin/dashboard?from=2020-03-02&to=2020-03-01").statusCode()).isEqualTo(400);
  assertThat(manager.get("/api/admin/dashboard?from=2020-01-01&to=2022-01-01").statusCode()).isEqualTo(400);assertThat(manager.get("/api/admin/dashboard?threshold=101").statusCode()).isEqualTo(400);
  var empty=json.readTree(manager.get("/api/admin/dashboard?from=2010-01-01&to=2010-01-03").body());assertThat(empty.get("days").size()).isEqualTo(3);assertThat(empty.get("orders").asLong()).isZero();assertThat(empty.get("amount").decimalValue()).isEqualByComparingTo("0");
 }
 @Test void dashboardStockCountsActiveSoldOutProductsAndUsesBoundedList()throws Exception{
  var manager=admin();long active=product(0),inactive=product(0);jdbc.update("UPDATE products SET active=false WHERE id=?",inactive);
  var result=json.readTree(manager.get("/api/admin/dashboard?threshold=0").body());
  assertThat(result.get("lowStockCount").asLong()).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM products WHERE active=true AND stock_quantity=0",Long.class));
  assertThat(result.get("lowStock").size()).isLessThanOrEqualTo(20);assertThat(result.get("lowStock").toString()).doesNotContain("\"id\":"+inactive+",");assertThat(result.get("lowStock").toString()).contains("\"id\":"+active+",");
 }
 @Test void stockAdjustmentCompetingWithCheckoutCannotSellRemovedUnit()throws Exception{
  var user=customer();var manager=admin();long id=product(1);long version=json.readTree(manager.get("/api/admin/products/"+id).body()).get("version").asLong();
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);try{
   var a=pool.submit(()->{gate.await();return user.send("POST","/api/orders",payload(List.of(line(id,1,"10.00"))),true,UUID.randomUUID().toString()).statusCode();});
   var b=pool.submit(()->{gate.await();return manager.send("PATCH","/api/admin/products/"+id+"/stock",Map.of("delta",-1,"reason","Stock race fixture","version",version),true).statusCode();});gate.countDown();
   assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(200,409);assertThat(stock(id)).isZero();
  }finally{pool.shutdownNow();}
 }
 @Test void checkoutCreatesSnapshotAndOnlyOwnerCanReadIt()throws Exception{
  var user=customer();long id=product(3);String key=UUID.randomUUID().toString();var body=payload(List.of(line(id,2,"10.00")));
  var result=user.send("POST","/api/orders",body,true,key);assertThat(result.statusCode()).isEqualTo(200);var order=json.readTree(result.body());String number=order.get("orderNumber").asText();
  assertThat(order.get("total").decimalValue()).isEqualByComparingTo("20.00");assertThat(order.get("items").get(0).get("companyName").asText()).isEqualTo("Noir Atelier");assertThat(stock(id)).isEqualTo(1);
  jdbc.update("UPDATE products SET price=15,name='Updated after purchase' WHERE id=?",id);
  var snapshot=json.readTree(user.get("/api/orders/"+number).body());assertThat(snapshot.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("10.00");assertThat(snapshot.get("items").get(0).get("name").asText()).isEqualTo("Order fixture");
  assertThat(customer().get("/api/orders/"+number).statusCode()).isEqualTo(404);
  assertThat(json.readTree(user.get("/api/orders").body()).get(0).get("orderNumber").asText()).isEqualTo(number);
  assertThat(user.send("POST","/api/orders",body,true,key).body()).contains(number);assertThat(stock(id)).isEqualTo(1);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(id,1,"10.00"))),true,key).statusCode()).isEqualTo(409);
 }
 @Test void rejectsMissingAuthenticationCsrfAndInvalidInput()throws Exception{
  var anonymous=new SessionClient(port,json);assertThat(anonymous.send("POST","/api/orders",Map.of(),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
  var user=customer();long id=product(2);var body=payload(List.of(line(id,1,"10.00")));
  assertThat(user.send("POST","/api/orders",body,false,UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
  assertThat(user.send("POST","/api/orders",body,true,"short").statusCode()).isEqualTo(400);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(id,0,"10.00"))),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(400);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(id,1,"10.00"),line(id,1,"10.00"))),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(400);assertThat(stock(id)).isEqualTo(2);
 }
 @Test void stalePricesInactiveProductsAndInsufficientStockRollbackTheWholeOrder()throws Exception{
  var user=customer();long one=product(3),two=product(0);long before=jdbc.queryForObject("SELECT count(*) FROM orders",Long.class);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(one,1,"10.00"),line(two,1,"10.00"))),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
  assertThat(stock(one)).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Long.class)).isEqualTo(before);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(one,1,"0.01"))),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
  jdbc.update("UPDATE products SET active=false WHERE id=?",one);
  assertThat(user.send("POST","/api/orders",payload(List.of(line(one,1,"10.00"))),true,UUID.randomUUID().toString()).statusCode()).isEqualTo(409);assertThat(stock(one)).isEqualTo(3);
 }
 @Test void simultaneousPurchasesOfLastUnitHaveOneWinner()throws Exception{
  var first=customer();var second=customer();long id=product(1);var body=payload(List.of(line(id,1,"10.00")));var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->{start.await();return first.send("POST","/api/orders",body,true,UUID.randomUUID().toString()).statusCode();});var b=pool.submit(()->{start.await();return second.send("POST","/api/orders",body,true,UUID.randomUUID().toString()).statusCode();});start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);assertThat(stock(id)).isZero();}finally{pool.shutdownNow();}
 }
 @Test void concurrentIdenticalRetriesCreateOneOrderAndOneStockMovement()throws Exception{
  var user=customer();long id=product(3);var body=payload(List.of(line(id,1,"10.00")));String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{var a=pool.submit(()->{start.await();return user.send("POST","/api/orders",body,true,key);});var b=pool.submit(()->{start.await();return user.send("POST","/api/orders",body,true,key);});start.countDown();var ar=a.get(30,TimeUnit.SECONDS);var br=b.get(30,TimeUnit.SECONDS);assertThat(ar.statusCode()).isEqualTo(200);assertThat(br.statusCode()).isEqualTo(200);assertThat(json.readTree(ar.body()).get("orderNumber")).isEqualTo(json.readTree(br.body()).get("orderNumber"));assertThat(stock(id)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=? AND order_id IS NOT NULL",Long.class,id)).isEqualTo(1);}finally{pool.shutdownNow();}
 }
}

