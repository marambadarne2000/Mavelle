package com.maramigo.support;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

/** Browser-like real HTTP cookie store for integration tests. @author Maram Abbas */
public final class SessionClient {
    private final String base;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    public SessionClient(int port, ObjectMapper json) { this.base="http://127.0.0.1:"+port; this.json=json; }
    public HttpResponse<String> get(String path) throws Exception { return http.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    public HttpResponse<String> send(String method, String path, Object data, boolean csrf) throws Exception {
        return send(method,path,data,csrf,null);
    }
    public HttpResponse<String> send(String method,String path,Object data,boolean csrf,String requestKey) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json")
            .method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        if(requestKey!=null)request.header("Idempotency-Key",requestKey);
        if (csrf) {
            var token=json.readTree(get("/api/auth/csrf").body());
            request.header(token.get("headerName").asText(),token.get("token").asText());
        }
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    public void login(String email,String password) throws Exception {
        var token=json.readTree(get("/api/auth/csrf").body());
        var request=HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
            .header("Content-Type","application/x-www-form-urlencoded")
            .header(token.get("headerName").asText(),token.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200) throw new AssertionError("Test login failed with HTTP "+response.statusCode());
    }
    public HttpResponse<String> loginPortal(String email,String password,String portal)throws Exception{
        var token=json.readTree(get("/api/auth/csrf").body());
        return http.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
            .header("Content-Type","application/x-www-form-urlencoded")
            .header(token.get("headerName").asText(),token.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8)+"&portal="+portal)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
