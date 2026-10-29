package com.maramigo.accounts;

import com.maramigo.support.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP/session/CSRF and PostgreSQL owner/employee boundaries. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","maramigo.login-limit=200","DEMO_ADMIN_EMAIL=owner@example.test","DEMO_ADMIN_PASSWORD=Owner-test-password-2026"})
class OwnerStaffIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 static final String PASSWORD="Staff-test-password-2026";
 SessionClient owner()throws Exception{var client=new SessionClient(port,json);client.login("owner@example.test","Owner-test-password-2026");return client;}
 String email(){return "work-"+UUID.randomUUID()+"@example.test";}
 JsonNode invite(SessionClient owner,String email)throws Exception{var response=owner.send("POST","/api/admin/owner/invitations",Map.of("email",email,"fullName","Synthetic employee"),true);assertThat(response.statusCode()).isEqualTo(200);return json.readTree(response.body());}
 Map<String,Object> accept(JsonNode invite,String email){return Map.of("token",invite.get("token").asText(),"email",email,"password",PASSWORD,"role","ADMIN");}
 SessionClient staff()throws Exception{var manager=owner();String email=email();var invite=invite(manager,email);var client=new SessionClient(port,json);assertThat(client.send("POST","/api/auth/staff-register",accept(invite,email),true).statusCode()).isEqualTo(201);client.login(email,PASSWORD);return client;}
 @Test void onlyOneOwnerAndPublicRegistrationCannotCreatePrivilegedAccounts()throws Exception{
  assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMIN'",Long.class)).isEqualTo(1);
  assertThatThrownBy(()->jdbc.update("INSERT INTO users(email,password_hash,role) VALUES (?,'synthetic','ADMIN')",email())).isInstanceOf(DataIntegrityViolationException.class);
  var visitor=new SessionClient(port,json);var response=visitor.send("POST","/api/auth/register",Map.of("email",email(),"password",PASSWORD,"role","ADMIN"),true);
  assertThat(response.statusCode()).isEqualTo(201);assertThat(json.readTree(response.body()).get("role").asText()).isEqualTo("CUSTOMER");
  visitor.login(json.readTree(response.body()).get("email").asText(),PASSWORD);
  assertThat(visitor.get("/api/admin/owner/employees").statusCode()).isEqualTo(403);
  assertThat(visitor.send("POST","/api/admin/owner/invitations",Map.of("email",email(),"fullName","Not allowed"),true).statusCode()).isEqualTo(403);
 }
 @Test void invitationsAreHashedBoundToEmailAndSingleUseEvenWithForgedRole()throws Exception{
  var manager=owner();String email=email();var invitation=invite(manager,email);String token=invitation.get("token").asText();
  assertThat(jdbc.queryForObject("SELECT token_hash FROM staff_invitations WHERE id=?",String.class,invitation.get("id").asLong())).isEqualTo(OwnerController.hash(token)).isNotEqualTo(token);
  assertThat(manager.get("/api/admin/owner/invitations").body()).doesNotContain(token,"token_hash");
  var guest=new SessionClient(port,json);
  assertThat(guest.send("POST","/api/auth/staff-register",accept(invitation,email()),true).statusCode()).isEqualTo(400);
  assertThat(guest.send("POST","/api/auth/staff-register",accept(invitation,email),false).statusCode()).isEqualTo(403);
  var registered=guest.send("POST","/api/auth/staff-register",accept(invitation,email),true);assertThat(registered.statusCode()).isEqualTo(201);
  assertThat(json.readTree(registered.body()).get("role").asText()).isEqualTo("EMPLOYEE");assertThat(registered.body()).doesNotContain("password");
  assertThat(guest.send("POST","/api/auth/staff-register",accept(invitation,email),true).statusCode()).isEqualTo(400);
 }
 @Test void expiredRevokedAndExistingCustomerInvitationsCannotGrantEmployeeAccess()throws Exception{
  var manager=owner();var guest=new SessionClient(port,json);String expiredEmail=email();var expired=invite(manager,expiredEmail);
  jdbc.update("UPDATE staff_invitations SET expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE id=?",expired.get("id").asLong());
  assertThat(guest.send("POST","/api/auth/staff-register",accept(expired,expiredEmail),true).statusCode()).isEqualTo(400);
  String revokedEmail=email();var revoked=invite(manager,revokedEmail);assertThat(manager.send("DELETE","/api/admin/owner/invitations/"+revoked.get("id").asLong(),null,true).statusCode()).isEqualTo(200);
  assertThat(guest.send("POST","/api/auth/staff-register",accept(revoked,revokedEmail),true).statusCode()).isEqualTo(400);
  String customerEmail=email();assertThat(guest.send("POST","/api/auth/register",Map.of("email",customerEmail,"password",PASSWORD),true).statusCode()).isEqualTo(201);
  assertThat(manager.send("POST","/api/admin/owner/invitations",Map.of("email",customerEmail,"fullName","Already customer"),true).statusCode()).isEqualTo(409);
 }
 @Test void concurrentInvitationAcceptanceCreatesExactlyOneEmployee()throws Exception{
  String email=email();var invitation=invite(owner(),email);var a=new SessionClient(port,json);var b=new SessionClient(port,json);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->a.send("POST","/api/auth/staff-register",accept(invitation,email),true).statusCode());var y=pool.submit(()->b.send("POST","/api/auth/staff-register",accept(invitation,email),true).statusCode());assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(201,400);}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=? AND role='EMPLOYEE'",Long.class,email)).isEqualTo(1);
 }
 @Test void portalMismatchDoesNotLeaveAnAuthenticatedOwnerSession()throws Exception{
  var client=new SessionClient(port,json);assertThat(client.loginPortal("owner@example.test","Owner-test-password-2026","customer").statusCode()).isEqualTo(403);assertThat(client.get("/api/auth/me").statusCode()).isEqualTo(401);
  assertThat(client.loginPortal("owner@example.test","Owner-test-password-2026","owner").statusCode()).isEqualTo(200);
  var worker=staff();String email=json.readTree(worker.get("/api/auth/me").body()).get("email").asText();
  assertThat(new SessionClient(port,json).loginPortal(email,PASSWORD,"owner").statusCode()).isEqualTo(403);
 }
 @Test void ownerProfileAndEmployeeRevocationProtectExistingSessions()throws Exception{
  var manager=owner();var profile=Map.of("ownerName","Synthetic Owner","businessName","Synthetic Store","registrationNumber","DEMO-ONLY","contactEmail","business@example.test","phone","0500000000");
  assertThat(manager.send("PUT","/api/admin/owner/profile",profile,true).statusCode()).isEqualTo(200);assertThat(json.readTree(manager.get("/api/admin/owner/profile").body()).get("businessName").asText()).isEqualTo("Synthetic Store");
  var worker=staff();var me=json.readTree(worker.get("/api/auth/me").body());long id=me.get("id").asLong();String email=me.get("email").asText();
  assertThat(worker.get("/api/admin/owner/profile").statusCode()).isEqualTo(403);
  assertThat(manager.send("PATCH","/api/admin/owner/employees/"+id,Map.of("enabled",false),true).statusCode()).isEqualTo(200);
  var pool=Executors.newFixedThreadPool(4);try{var requests=new ArrayList<Future<Integer>>();for(int i=0;i<4;i++)requests.add(pool.submit(()->worker.get("/api/staff/orders").statusCode()));for(var request:requests)assertThat(request.get()).isEqualTo(401);}finally{pool.shutdownNow();}
  assertThat(worker.get("/api/auth/me").statusCode()).isEqualTo(401);
  assertThat(new SessionClient(port,json).loginPortal(email,PASSWORD,"staff").statusCode()).isEqualTo(401);
  assertThat(manager.send("PATCH","/api/admin/owner/employees/"+json.readTree(manager.get("/api/auth/me").body()).get("id").asLong(),Map.of("enabled",false),true).statusCode()).isEqualTo(404);
 }
 @Test void staffCanOnlyFulfilOrdersAndNeverReceivesMoneyFields()throws Exception{
  var worker=staff();var customer=new SessionClient(port,json);String email=email();customer.send("POST","/api/auth/register",Map.of("email",email,"password",PASSWORD),true);customer.login(email,PASSWORD);
  long product=jdbc.queryForObject("INSERT INTO products(sku,name,description,category,price,stock_quantity,active,company_id) VALUES (?,'Synthetic item','Test','Living',10,5,true,1) RETURNING id",Long.class,"WORK-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
  var shipping=Map.of("fullName","Synthetic Customer","phone","0500000000","address","1 Synthetic Street","city","Demo City","postalCode","0000000","country","IL");
  var placed=customer.send("POST","/api/orders",Map.of("shipping",shipping,"items",List.of(Map.of("productId",product,"quantity",1,"expectedPrice","10.00"))),true,UUID.randomUUID().toString());assertThat(placed.statusCode()).isEqualTo(200);
  String number=json.readTree(placed.body()).get("orderNumber").asText(),path="/api/staff/orders/"+number;
  var list=json.readTree(worker.get("/api/staff/orders?search="+number).body());assertThat(list.get("items").get(0).has("total")).isFalse();
  var detail=worker.get(path);assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).doesNotContain("\"total\"","\"unitPrice\"","\"lineTotal\"","\"currency\"");
  for(String forbidden:List.of("/api/admin/dashboard","/api/admin/products","/api/admin/orders","/api/admin/owner/employees","/api/orders"))assertThat(worker.get(forbidden).statusCode()).isEqualTo(403);
  assertThat(worker.send("PATCH",path+"/status",Map.of("status","CANCELLED","version",0,"reason","Forbidden"),true).statusCode()).isEqualTo(400);
  assertThat(worker.send("PATCH",path+"/status",Map.of("status","SHIPPED","version",0,"reason","Synthetic dispatch"),true).statusCode()).isEqualTo(200);
  assertThat(worker.send("PATCH",path+"/status",Map.of("status","DELIVERED","version",0,"reason","Stale"),true).statusCode()).isEqualTo(409);
  assertThat(worker.send("PATCH",path+"/status",Map.of("status","DELIVERED","version",1,"reason","Synthetic delivery"),true).statusCode()).isEqualTo(200);
  long actor=json.readTree(worker.get("/api/auth/me").body()).get("id").asLong();assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history h JOIN orders o ON o.id=h.order_id WHERE o.order_number=? AND h.actor_id=?",Long.class,number,actor)).isEqualTo(2);
 }
}
