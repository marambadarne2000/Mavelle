package com.maramigo.accounts;

import com.maramigo.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Real HTTP, CSRF, PostgreSQL locks and password/session checks; only mail transport is replaced. @author Maram Abbas */
@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "server.servlet.session.cookie.secure=false","maramigo.login-limit=100","maramigo.verification-required=true",
 "DEMO_ADMIN_EMAIL=lifecycle-owner@example.test","DEMO_ADMIN_PASSWORD=Test-admin-password-2026"})
class AccountLifecycleIT {
 static final TestPostgres db=new TestPostgres();
 @DynamicPropertySource static void configure(DynamicPropertyRegistry p)throws Exception{db.configure(p);}
 @AfterAll static void cleanup()throws Exception{db.close();}
 @LocalServerPort int port; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
 @MockitoBean AccountMail mail; @Autowired AccountLifecycle lifecycle;
 final Map<String,String> delivered=new ConcurrentHashMap<>();
 @BeforeEach void transport(){when(mail.mode()).thenReturn("file");doAnswer(call->{delivered.put(call.getArgument(0)+":"+call.getArgument(1),call.getArgument(2));return null;}).when(mail).send(anyString(),anyString(),anyString());}
 SessionClient client(){return new SessionClient(port,json);}
 String register()throws Exception{String email=UUID.randomUUID()+"@example.test";assertThat(client().send("POST","/api/auth/register",Map.of("email",email,"password","Original-password-2026"),true).statusCode()).isEqualTo(201);return email;}
 String request(String email,String kind)throws Exception{assertThat(client().send("POST",kind.equals("RESET")?"/api/auth/forgot-password":"/api/auth/verification-request",Map.of("email",email),true).statusCode()).isEqualTo(202);for(int i=0;i<200&&!delivered.containsKey(email+":"+kind);i++)Thread.sleep(25);String token=delivered.get(email+":"+kind);for(int i=0;i<200&&token!=null&&jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE token_hash=?",Integer.class,OwnerController.hash(token))==0;i++)Thread.sleep(25);return token;}
 int consume(String token,String kind)throws Exception{return client().send("POST",kind.equals("RESET")?"/api/auth/reset-password":"/api/auth/verify-email",kind.equals("RESET")?Map.of("token",token,"password","Updated-password-2026"):Map.of("token",token),true).statusCode();}
 @Test void verificationIsExplicitSingleUsePurposeBoundAndRequiredForLogin()throws Exception{
  String email=register();assertThat(client().loginPortal(email,"Original-password-2026","").statusCode()).isEqualTo(401);
  String token=request(email,"VERIFY");assertThat(token).hasSize(43);
  assertThat(jdbc.queryForObject("SELECT token_hash FROM account_tokens WHERE user_id=(SELECT id FROM users WHERE email=?)",String.class,email)).isEqualTo(OwnerController.hash(token)).isNotEqualTo(token);
  assertThat(consume(token,"RESET")).isEqualTo(400);
  assertThat(consume(token,"VERIFY")).isEqualTo(204);assertThat(consume(token,"VERIFY")).isEqualTo(400);
  var session=client();session.login(email,"Original-password-2026");assertThat(json.readTree(session.get("/api/auth/security").body()).get("emailVerified").asBoolean()).isTrue();
 }
 @Test void resetRevokesAllOldSessionsAndTokensWithoutAutomaticallyVerifyingEmail()throws Exception{
  String email=register();jdbc.update("UPDATE users SET email_verified=true WHERE email=?",email);
  var first=client();var second=client();first.login(email,"Original-password-2026");second.login(email,"Original-password-2026");
  String token=request(email,"RESET");assertThat(consume(token,"RESET")).isEqualTo(204);
  assertThat(first.get("/api/auth/me").statusCode()).isEqualTo(401);assertThat(second.get("/api/auth/me").statusCode()).isEqualTo(401);
  assertThat(client().loginPortal(email,"Original-password-2026","").statusCode()).isEqualTo(401);
  client().login(email,"Updated-password-2026");assertThat(consume(token,"RESET")).isEqualTo(400);
  assertThat(passwords.matches("Updated-password-2026",jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,email))).isTrue();
  String unverified=register();String verify=request(unverified,"VERIFY");assertThat(consume(request(unverified,"RESET"),"RESET")).isEqualTo(204);
  assertThat(consume(verify,"VERIFY")).isEqualTo(400);assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email=?",Boolean.class,unverified)).isFalse();
 }
 @Test void expiredReplacedAndDisabledAccountLinksAreRejected()throws Exception{
  String email=register();String old=request(email,"RESET");jdbc.update("UPDATE account_tokens SET created_at=CURRENT_TIMESTAMP-INTERVAL '2 minutes' WHERE token_hash=?",OwnerController.hash(old));
  delivered.remove(email+":RESET");String replacement=request(email,"RESET");assertThat(replacement).isNotEqualTo(old);assertThat(consume(old,"RESET")).isEqualTo(400);
  jdbc.update("UPDATE account_tokens SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE token_hash=?",OwnerController.hash(replacement));assertThat(consume(replacement,"RESET")).isEqualTo(400);
  String verify=request(email,"VERIFY");jdbc.update("UPDATE users SET enabled=false WHERE email=?",email);assertThat(consume(verify,"VERIFY")).isEqualTo(400);
 }
 @Test void requestsDoNotEnumerateAccountsAndCooldownDoesNotInvalidateTheFirstLink()throws Exception{
  String email=register();String token=request(email,"RESET");assertThat(request(email,"RESET")).isEqualTo(token);verify(mail,times(1)).send(eq(email),eq("RESET"),anyString());
  var known=client().send("POST","/api/auth/forgot-password",Map.of("email",email),true);var unknown=client().send("POST","/api/auth/forgot-password",Map.of("email",UUID.randomUUID()+"@example.test"),true);
  assertThat(known.statusCode()).isEqualTo(unknown.statusCode()).isEqualTo(202);assertThat(known.body()).isEqualTo(unknown.body()).doesNotContain(token);
  assertThat(consume(token,"RESET")).isEqualTo(204);
 }
 @Test void concurrentConsumptionHasExactlyOneWinner()throws Exception{
  String token=request(register(),"RESET");var pool=Executors.newFixedThreadPool(2);
  try{var gate=new CountDownLatch(1);Callable<Integer> attempt=()->{gate.await();return consume(token,"RESET");};var a=pool.submit(attempt);var b=pool.submit(attempt);gate.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);}finally{pool.shutdownNow();}
 }
 @Test void csrfMalformedTokensAndWeakPasswordsDoNotConsumeValidLinks()throws Exception{
  String token=request(register(),"RESET");assertThat(client().send("POST","/api/auth/reset-password",Map.of("token",token,"password","Updated-password-2026"),false).statusCode()).isEqualTo(403);
  assertThat(client().send("POST","/api/auth/reset-password",Map.of("token",token,"password","short"),true).statusCode()).isEqualTo(400);
  var malformed=client().send("POST","/api/auth/reset-password",Map.of("token","invalid","password","Updated-password-2026"),true);assertThat(malformed.statusCode()).isEqualTo(400);assertThat(malformed.body()).contains("Check the required fields").doesNotContain("Updated-password-2026");assertThat(consume(token,"RESET")).isEqualTo(204);
 }
 @Test void transportFailureRollsBackTheTokenSoAnImmediateRetryCanSucceed()throws Exception{
  String email=register();doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Delivery unavailable")).when(mail).send(eq(email),eq("RESET"),anyString());
  org.assertj.core.api.Assertions.assertThatThrownBy(()->lifecycle.request(email,"RESET")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE user_id=(SELECT id FROM users WHERE email=?)",Integer.class,email)).isZero();
  transport();assertThat(consume(request(email,"RESET"),"RESET")).isEqualTo(204);
 }
}
