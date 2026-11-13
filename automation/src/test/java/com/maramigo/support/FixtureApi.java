package com.maramigo.support;

import com.fasterxml.jackson.databind.*;
import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.Duration;import java.util.*;

/** Fixture writes are permitted only by the isolated full-suite launcher. @author Maram Abbas */
public final class FixtureApi {
 private final String base;private final ObjectMapper json=new ObjectMapper();
 private final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
 public record Product(long id,String name,String sku){}
 public static final String PASSWORD="E2e-customer-password-2026";
 public FixtureApi(String base)throws Exception{
  if(!Optional.ofNullable(System.getenv("E2E_ISOLATED_SCHEMA")).orElse("").matches("e2e_[a-f0-9]{32}"))throw new IllegalStateException("Full E2E requires the isolated launcher. Run Test-Automation.ps1 -Full.");
  this.base=base;var token=get("/api/auth/csrf");
  var request=HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/x-www-form-urlencoded").header(token.get("headerName").asText(),token.get("token").asText()).POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(adminEmail(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(adminPassword(),StandardCharsets.UTF_8))).build();
  var response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Isolated fixture login failed: HTTP "+response.statusCode());
 }
 public static String adminEmail(){return Objects.requireNonNull(System.getenv("E2E_ADMIN_EMAIL"),"Isolated administrator required");}
 public static String adminPassword(){return Objects.requireNonNull(System.getenv("E2E_ADMIN_PASSWORD"),"Isolated administrator required");}
 public JsonNode get(String path)throws Exception{return result(client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString()));}
 public JsonNode send(String method,String path,Object body)throws Exception{
  var token=get("/api/auth/csrf");return result(client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").header(token.get("headerName").asText(),token.get("token").asText()).method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString()));
 }
 private JsonNode result(HttpResponse<String> response)throws Exception{if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Fixture API failed: HTTP "+response.statusCode());return json.readTree(response.body());}
 public Product product(int stock)throws Exception{
  String suffix=UUID.randomUUID().toString().substring(0,12),name="E2E item "+suffix,sku="E2E-"+suffix.toUpperCase(Locale.ROOT);
  var created=send("POST","/api/admin/products",Map.of("product",Map.of("sku",sku,"name",name,"description","Isolated browser fixture","category","Living","price","10.00","active",true,"companyId",1),"initialStock",stock));return new Product(created.get("id").asLong(),name,sku);
 }
 public String customer()throws Exception{String email="e2e-"+UUID.randomUUID()+"@example.test";send("POST","/api/auth/register",Map.of("email",email,"password",PASSWORD));return email;}
 public void price(Product p,String price)throws Exception{edit(p,price,true);}
 public void inactive(Product p)throws Exception{edit(p,get("/api/admin/products/"+p.id()).get("price").asText(),false);}
 private void edit(Product p,String price,boolean active)throws Exception{var current=get("/api/admin/products/"+p.id());send("PUT","/api/admin/products/"+p.id(),Map.of("version",current.get("version").asLong(),"product",Map.of("sku",p.sku(),"name",p.name(),"description","Isolated browser fixture","category","Living","price",price,"active",active,"companyId",1)));}
 public void stock(Product p,int quantity)throws Exception{var current=get("/api/admin/products/"+p.id());int delta=quantity-current.get("stockQuantity").asInt();if(delta!=0)send("PATCH","/api/admin/products/"+p.id()+"/stock",Map.of("delta",delta,"version",current.get("version").asLong(),"reason","Isolated Selenium fixture"));}
 public int stock(Product p)throws Exception{return get("/api/admin/products/"+p.id()).get("stockQuantity").asInt();}
}
