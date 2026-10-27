package com.maramigo.accounts;

import com.maramigo.support.TestPostgres;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP cookies and CSRF, real password hashes, real PostgreSQL. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "server.servlet.session.cookie.secure=false", "maramigo.login-limit=100",
    "DEMO_ADMIN_EMAIL=admin@example.test", "DEMO_ADMIN_PASSWORD=Test-admin-password-2026"})
class AuthIT {
    static final TestPostgres db = new TestPostgres();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws Exception { db.configure(properties); }
    @AfterAll static void cleanup() throws Exception { db.close(); }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    class Session {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode token() throws Exception { return json.readTree(get("/api/auth/csrf").body()); }
        HttpResponse<String> post(String path, String body, String type, JsonNode csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> register(String email, String password) throws Exception {
            return post("/api/auth/register", json.writeValueAsString(java.util.Map.of("email",email,"password",password)), "application/json", token());
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/api/auth/login", "username="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8), "application/x-www-form-urlencoded", token());
        }
        String sessionId() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).map(HttpCookie::getValue).findFirst().orElse(""); }
    }
    String uniqueEmail() { return UUID.randomUUID()+"@example.test"; }

    @Test void anonymousAndMissingCsrfRequestsAreRejected() throws Exception {
        Session session = new Session();
        assertThat(session.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(session.get("/api/admin/identity").statusCode()).isEqualTo(401);
        assertThat(session.post("/api/auth/register", "{}", "application/json", null).statusCode()).isEqualTo(403);
        assertThat(session.post("/api/auth/login", "username=x&password=y", "application/x-www-form-urlencoded", null).statusCode()).isEqualTo(403);
    }

    @Test void registrationNormalizesEmailProtectsRoleAndHashesPasswords() throws Exception {
        Session session = new Session(); String email = uniqueEmail();
        var response = session.post("/api/auth/register", json.writeValueAsString(java.util.Map.of(
            "email","  "+email.toUpperCase(java.util.Locale.ROOT)+"  ", "password","Test-customer-password", "role","ADMIN")), "application/json", session.token());
        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(response.body()).doesNotContain("password");
        String hash = jdbc.queryForObject("SELECT password_hash FROM "+db.schema+".users WHERE email=?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo("Test-customer-password");
        assertThat(session.register(email,"Test-customer-password").statusCode()).isEqualTo(409);
        assertThat(session.register(uniqueEmail(),"short").statusCode()).isEqualTo(400);
        assertThat(session.register(uniqueEmail(),"א".repeat(37)).statusCode()).isEqualTo(400);
        assertThat(session.register("invalid-address","Test-customer-password").statusCode()).isEqualTo(400);
    }

    @Test void loginRotatesSessionEnforcesRoleAndLogoutInvalidatesAccess() throws Exception {
        Session session = new Session(); String email = uniqueEmail();
        assertThat(session.register(email,"Test-customer-password").statusCode()).isEqualTo(201);
        JsonNode oldCsrf = session.token(); String oldSession = session.sessionId();
        assertThat(session.login(email,"wrong-password").statusCode()).isEqualTo(401);
        var login = session.login(email,"Test-customer-password");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(session.sessionId()).isNotBlank().isNotEqualTo(oldSession);
        assertThat(session.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(session.get("/api/admin/identity").statusCode()).isEqualTo(403);
        assertThat(session.post("/api/auth/logout","","application/json",null).statusCode()).isEqualTo(403);
        assertThat(session.post("/api/auth/logout","","application/json",oldCsrf).statusCode()).isEqualTo(403);
        assertThat(session.post("/api/auth/logout","","application/json",session.token()).statusCode()).isEqualTo(204);
        assertThat(session.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test void explicitDemoAdminHasServerSideAccess() throws Exception {
        Session session = new Session();
        assertThat(session.login("admin@example.test","Test-admin-password-2026").statusCode()).isEqualTo(200);
        var identity = session.get("/api/admin/identity");
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(json.readTree(identity.body()).get("role").asText()).isEqualTo("ADMIN");
        assertThat(identity.body()).doesNotContain("password");
    }
}
