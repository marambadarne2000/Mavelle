package com.maramigo.accounts;
import com.maramigo.support.TestPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.test.context.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;

/** A real native Tomcat valve, rather than trusting caller-supplied headers in application code. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","maramigo.login-limit=1"})
class ProxyHeadersIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @Autowired ServletWebServerApplicationContext context;@Autowired ObjectMapper json;
 @Test void spoofedHeadersCannotBypassLimitsAndExplicitlyTrustedPeersSeparateClients()throws Exception{
  var server=(TomcatWebServer)context.getWebServer();String base="http://127.0.0.1:"+server.getPort();
  var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
  var token=json.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
  var valve=java.util.Arrays.stream(server.getTomcat().getEngine().getPipeline().getValves()).filter(v->v instanceof org.apache.catalina.valves.RemoteIpValve).map(v->(org.apache.catalina.valves.RemoteIpValve)v).findFirst().orElseThrow();
  assertThat(valve.getInternalProxies()).isEqualTo("(?!)");
  assertThat(post(client,base,token,"/api/auth/login","198.51.100.1")).isEqualTo(401);
  for(String path:java.util.List.of("/api/auth/register","/api/auth/business-register","/api/auth/staff-register"))assertThat(post(client,base,token,path,"198.51.100.2")).isEqualTo(429);
  try{
   valve.setInternalProxies("127\\.0\\.0\\.1");
   assertThat(post(client,base,token,"/api/auth/login","198.51.100.11")).isEqualTo(401);
   assertThat(post(client,base,token,"/api/auth/login","198.51.100.12")).isEqualTo(401);
   assertThat(post(client,base,token,"/api/auth/login","198.51.100.11")).isEqualTo(429);
  }finally{valve.setInternalProxies("(?!)");}
 }
 int post(HttpClient client,String base,com.fasterxml.jackson.databind.JsonNode token,String path,String forwarded)throws Exception{
  var r=HttpRequest.newBuilder(URI.create(base+path)).header(token.get("headerName").asText(),token.get("token").asText()).header("X-Forwarded-For",forwarded).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username=nonexistent%40example.test&password=Synthetic-test-password"));
  return client.send(r.build(),HttpResponse.BodyHandlers.discarding()).statusCode();
 }
}
