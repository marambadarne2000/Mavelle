package com.maramigo.engagement;

import com.maramigo.support.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL/HTTP, private support threads, durable scoped receipts. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","maramigo.login-limit=400","DEMO_ADMIN_EMAIL=owner@example.test","DEMO_ADMIN_PASSWORD=Owner-test-password-2026"})
class EngagementIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
 static final String PASSWORD="Support-test-password-2026";String hash;
 record User(SessionClient client,long id,String email){}
 User user(String role)throws Exception{if(hash==null)hash=encoder.encode(PASSWORD);String email="support-"+UUID.randomUUID()+"@example.test";long id=jdbc.queryForObject("INSERT INTO users(email,password_hash,role,email_verified) VALUES (?,?,?,true) RETURNING id",Long.class,email,hash,role);var client=new SessionClient(port,json);client.login(email,PASSWORD);return new User(client,id,email);}
 SessionClient owner()throws Exception{var c=new SessionClient(port,json);c.login("owner@example.test","Owner-test-password-2026");return c;}
 long ownerId(){return jdbc.queryForObject("SELECT id FROM users WHERE role='ADMIN'",Long.class);}
 JsonNode get(SessionClient c,String path)throws Exception{var r=c.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 JsonNode ticket(User user)throws Exception{var r=user.client().send("POST","/api/support/tickets",Map.of("subject","Synthetic assistance","category","TECHNICAL","body","Private support text"),true);assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json.readTree(r.body());}
 String path(JsonNode detail){return "/api/support/tickets/"+detail.get("ticket").get("id").asLong();}

 @Test void supportAndUpdatesRequireAuthenticationAndCsrf()throws Exception{
  var guest=new SessionClient(port,json);assertThat(guest.get("/api/support/tickets").statusCode()).isEqualTo(401);assertThat(guest.get("/api/notifications").statusCode()).isEqualTo(401);
  var c=user("CUSTOMER");assertThat(c.client().send("POST","/api/support/tickets",Map.of("subject","Test","category","OTHER","body","Test"),false).statusCode()).isEqualTo(403);
  assertThat(c.client().send("POST","/api/notifications/read",Map.of("keys",List.of("order:1")),false).statusCode()).isEqualTo(403);
 }
 @Test void eachNonOwnerRoleCanCreateButCannotReadReplyOrChangeAnotherPersonsTicket()throws Exception{
  var a=user("CUSTOMER");var detail=ticket(a);String path=path(detail);
  for(String role:List.of("CUSTOMER","MERCHANT","EMPLOYEE")){
   var b=user(role);var own=ticket(b);assertThat(get(b.client(),"/api/support/tickets").get("items").toString()).contains("\"id\":"+own.get("ticket").get("id")).doesNotContain("\"id\":"+detail.get("ticket").get("id")+",");
   assertThat(b.client().get(path).statusCode()).isEqualTo(404);
   assertThat(b.client().send("POST",path+"/messages",Map.of("body","Forbidden"),true).statusCode()).isEqualTo(404);
   assertThat(b.client().send("PATCH",path,Map.of("status","RESOLVED","version",0),true).statusCode()).isEqualTo(404);
  }
  assertThat(get(owner(),path).get("messages").get(0).get("body").asText()).isEqualTo("Private support text");
 }
 @Test void ownerReviewReplyResolveReopenAndVersionChecksWork()throws Exception{
  var c=user("CUSTOMER");var d=ticket(c);String p=path(d);var admin=owner();
  assertThat(c.client().send("PATCH",p,Map.of("status","IN_PROGRESS","version",0),true).statusCode()).isEqualTo(403);
  assertThat(admin.send("PATCH",p,Map.of("status","IN_PROGRESS","version",0),true).statusCode()).isEqualTo(200);
  assertThat(admin.send("POST",p+"/messages",Map.of("body","A private platform reply"),true).statusCode()).isEqualTo(200);
  assertThat(get(c.client(),p).get("messages").get(1).get("senderRole").asText()).isEqualTo("ADMIN");
  assertThat(c.client().send("PATCH",p,Map.of("status","RESOLVED","version",0),true).statusCode()).isEqualTo(409);
  assertThat(c.client().send("PATCH",p,Map.of("status","RESOLVED","version",1),true).statusCode()).isEqualTo(200);
  assertThat(admin.send("POST",p+"/messages",Map.of("body","Too late"),true).statusCode()).isEqualTo(409);
  assertThat(c.client().send("PATCH",p,Map.of("status","OPEN","version",2),true).statusCode()).isEqualTo(200);
  assertThat(c.client().send("POST",p+"/messages",Map.of("body","A follow-up"),true).statusCode()).isEqualTo(200);
  assertThat(get(c.client(),p).get("ticket").get("version").asInt()).isEqualTo(3);
 }
 @Test void supportRejectsInvalidFieldsAndPagination()throws Exception{
  var c=user("CUSTOMER");
  for(var data:List.of(Map.of("subject"," ","category","OTHER","body","Test"),Map.of("subject","Test","category","ADMIN","body","Test"),Map.of("subject","Test","category","OTHER","body","x".repeat(2001))))assertThat(c.client().send("POST","/api/support/tickets",data,true).statusCode()).isEqualTo(400);
  assertThat(c.client().get("/api/support/tickets?page=-1").statusCode()).isEqualTo(400);
  assertThat(c.client().get("/api/support/tickets?status=UNKNOWN").statusCode()).isEqualTo(400);
  assertThat(c.client().send("POST","/api/notifications/read",Map.of("keys",List.of()),true).statusCode()).isEqualTo(400);
 }
 @Test void supportListsAndOldMessagesArePagedWithoutLosingTheLatestThread()throws Exception{
  var c=user("CUSTOMER");var d=ticket(c);long id=d.get("ticket").get("id").asLong();
  for(int i=0;i<21;i++)jdbc.update("INSERT INTO support_tickets(requester_id,subject,category,status,created_at,updated_at) VALUES (?,'Old request','OTHER','RESOLVED',CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP-INTERVAL '1 day')",c.id());
  assertThat(get(c.client(),"/api/support/tickets").get("items").size()).isEqualTo(20);assertThat(get(c.client(),"/api/support/tickets?page=1").get("items").size()).isEqualTo(2);
  for(int i=0;i<51;i++)jdbc.update("INSERT INTO support_messages(ticket_id,sender_id,body,created_at) VALUES (?,?,'Old platform reply',CURRENT_TIMESTAMP-INTERVAL '1 hour')",id,ownerId());
  var latest=get(c.client(),path(d));assertThat(latest.get("messages").size()).isEqualTo(50);assertThat(latest.get("hasOlder").asBoolean()).isTrue();
  var older=get(c.client(),path(d)+"?before="+latest.get("messages").get(0).get("id").asLong());assertThat(older.get("messages").size()).isEqualTo(2);assertThat(older.get("hasOlder").asBoolean()).isFalse();
 }
 @Test void supportQuotasPreventUnboundedTicketAndMessageSpam()throws Exception{
  var c=user("CUSTOMER");var d=ticket(c);
  for(int i=0;i<9;i++)ticket(c);
  assertThat(c.client().send("POST","/api/support/tickets",Map.of("subject","Excess","category","OTHER","body","Test"),true).statusCode()).isEqualTo(429);
  for(int i=0;i<10;i++)assertThat(c.client().send("POST",path(d)+"/messages",Map.of("body","Follow-up"),true).statusCode()).isEqualTo(200);
  assertThat(c.client().send("POST",path(d)+"/messages",Map.of("body","Excess"),true).statusCode()).isEqualTo(429);
 }
 @Test void notificationsArePrivateAndReadReceiptsPersistAcrossSessions()throws Exception{
  var a=user("CUSTOMER");var b=user("CUSTOMER");var d=ticket(a);var admin=owner();admin.send("POST",path(d)+"/messages",Map.of("body","Private reply for A"),true);
  var inbox=get(a.client(),"/api/notifications");assertThat(inbox.get("unread").asInt()).isEqualTo(1);String key=inbox.get("items").get(0).get("key").asText();
  assertThat(get(b.client(),"/api/notifications").get("items").size()).isZero();assertThat(b.client().send("POST","/api/notifications/read",Map.of("keys",List.of(key)),true).statusCode()).isEqualTo(404);
  for(int i=0;i<2;i++)assertThat(a.client().send("POST","/api/notifications/read",Map.of("keys",List.of(key)),true).statusCode()).isEqualTo(200);
  var another=new SessionClient(port,json);another.login(a.email(),PASSWORD);assertThat(get(another,"/api/notifications").get("unread").asInt()).isZero();
  assertThat(get(a.client(),"/api/notifications").toString()).doesNotContain("Private reply for A").doesNotContain(b.email());
 }
 @Test void notificationsStayBoundedAndAckStillWorksWhenANewUpdateDisplacesTheOldest()throws Exception{
  var c=user("CUSTOMER");var d=ticket(c);long id=d.get("ticket").get("id").asLong();
  for(int i=0;i<101;i++)jdbc.update("INSERT INTO support_messages(ticket_id,sender_id,body,created_at) VALUES (?,?,'Earlier response',CURRENT_TIMESTAMP-INTERVAL '1 hour')",id,ownerId());
  var inbox=get(c.client(),"/api/notifications");assertThat(inbox.get("items").size()).isEqualTo(100);var keys=new ArrayList<String>();inbox.get("items").forEach(n->keys.add(n.get("key").asText()));
  assertThat(owner().send("POST",path(d)+"/messages",Map.of("body","Newest response"),true).statusCode()).isEqualTo(200);
  assertThat(c.client().send("POST","/api/notifications/read",Map.of("keys",keys),true).statusCode()).isEqualTo(200);
  assertThat(get(c.client(),"/api/notifications").get("unread").asInt()).isEqualTo(1);
  jdbc.update("UPDATE support_messages SET created_at=CURRENT_TIMESTAMP-INTERVAL '91 days' WHERE ticket_id=?",id);
  assertThat(get(c.client(),"/api/notifications").get("items").size()).isZero();
 }
 @Test void actualCheckoutAndDeliveryProduceUpdatesWithoutLeakingOrderTotalsToStaff()throws Exception{
  var c=user("CUSTOMER");var employee=user("EMPLOYEE");
  var product=json.readTree(c.client().get("/api/products/1").body());
  var response=c.client().send("POST","/api/orders",Map.of("items",List.of(Map.of("productId",1,"quantity",1,"expectedPrice",product.get("price").asText())),"shipping",Map.of("fullName","Support Buyer","phone","0500000000","address","1 Test St","city","Test","postalCode","0000000","country","IL")),true,UUID.randomUUID().toString());
  assertThat(response.statusCode()).as(response.body()).isEqualTo(200);String number=json.readTree(response.body()).get("orderNumber").asText();
  assertThat(get(c.client(),"/api/notifications").toString()).contains(number).contains("Order received");
  var staffInbox=get(employee.client(),"/api/notifications");assertThat(staffInbox.toString()).contains(number).doesNotContain("total_price").doesNotContain("amount").doesNotContain("Support Buyer");
  assertThat(get(employee.client(),"/api/notifications").get("items").findValuesAsText("target")).contains("#staff-orders");
  assertThat(employee.client().send("PATCH","/api/staff/orders/"+number+"/status",Map.of("status","SHIPPED","version",0,"reason","Synthetic shipment"),true).statusCode()).isEqualTo(200);
  assertThat(get(c.client(),"/api/notifications").toString()).contains("Delivery status updated").contains("SHIPPED");
 }
 @Test void accountExportAndReviewedClosureIncludeAndRedactSupportData()throws Exception{
  var c=user("CUSTOMER");var d=ticket(c);owner().send("POST",path(d)+"/messages",Map.of("body","Private platform details"),true);
  var data=get(c.client(),"/api/account/export");assertThat(data.get("supportRequests").size()).isEqualTo(1);assertThat(data.get("supportMessages").size()).isEqualTo(2);
  assertThat(c.client().send("POST","/api/account/privacy",Map.of("currentPassword",PASSWORD,"reason","Close synthetic account"),true).statusCode()).isEqualTo(200);
  long request=jdbc.queryForObject("SELECT id FROM privacy_requests WHERE user_id=?",Long.class,c.id());
  assertThat(owner().send("POST","/api/admin/privacy/"+request,Map.of("status","COMPLETED","reason","Synthetic closure reviewed"),true).statusCode()).isEqualTo(200);
  assertThat(jdbc.queryForList("SELECT body FROM support_messages WHERE ticket_id=?",d.get("ticket").get("id").asLong()).toString()).doesNotContain("Private support text").doesNotContain("Private platform details");
  assertThat(c.client().get("/api/notifications").statusCode()).isEqualTo(401);
 }
}
