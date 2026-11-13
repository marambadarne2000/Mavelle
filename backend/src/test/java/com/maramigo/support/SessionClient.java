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
    // Spring creates its session token lazily after login. Serialize only token acquisition,
    // so concurrent test mutations share a fully established session but still race at the API.
    private synchronized JsonNode csrfToken() throws Exception { return json.readTree(get("/api/auth/csrf").body()); }
    public HttpResponse<String> send(String method, String path, Object data, boolean csrf) throws Exception {
        return send(method,path,data,csrf,null);
    }
    public HttpResponse<String> send(String method,String path,Object data,boolean csrf,String requestKey) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json")
            .method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        if(requestKey!=null)request.header("Idempotency-Key",requestKey);
        if (csrf) {
            var token=csrfToken();
            request.header(token.get("headerName").asText(),token.get("token").asText());
        }
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    public void login(String email,String password) throws Exception {
        var token=csrfToken();
        var request=HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
            .header("Content-Type","application/x-www-form-urlencoded")
            .header(token.get("headerName").asText(),token.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200) throw new AssertionError("Test login failed with HTTP "+response.statusCode());
    }

    public HttpResponse<String> upload(String path,byte[] bytes,boolean csrf) throws Exception {
        String boundary="fixture-"+java.util.UUID.randomUUID();
        var body=new java.io.ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fixture.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if(csrf){var token=json.readTree(get("/api/auth/csrf").body());request.header(token.get("headerName").asText(),token.get("token").asText());}
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    public HttpResponse<String> loginPortal(String email,String password,String portal)throws Exception{
        var token=csrfToken();
        return http.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
            .header("Content-Type","application/x-www-form-urlencoded")
            .header(token.get("headerName").asText(),token.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8)+"&portal="+portal)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
