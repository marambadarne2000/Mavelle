package com.maramigo.accounts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Transport safety is checked without contacting any real mailbox. @author Maram Abbas */
class AccountMailTest {
 @TempDir Path directory;
 MockEnvironment demo(){var env=new MockEnvironment();env.setActiveProfiles("demo");return env;}
 AccountMail create(MockEnvironment env,String mode,String base){return new AccountMail(env,mode,base,directory.toString(),"","",587,"","");}
 @Test void demoWritesPrivateUtf8MessagesWithTrustedLinks()throws Exception{
  var mail=create(demo(),"file","http://127.0.0.1:5173");mail.send("synthetic@example.test","RESET","A".repeat(43));
  try(var files=Files.list(directory)){var paths=files.toList();assertThat(paths).hasSize(1);String text=Files.readString(paths.get(0));assertThat(text).contains("To: synthetic@example.test","/#reset-password/"+"A".repeat(43),"בתוקף", "صالح");}
 }
 @Test void productionRejectsLocalDeliveryAndUntrustedUrlForms(){
  assertThatThrownBy(()->create(new MockEnvironment(),"file","https://example.test")).isInstanceOf(IllegalArgumentException.class);
  for(String url:java.util.List.of("http://example.test","https://example.test/#fragment","https://name@example.test","https://example.test/?q=1"))
   assertThatThrownBy(()->create(demo(),"disabled",url)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void orderConfirmationContainsOrderDetailsAndClearlyIdentifiesDemo()throws Exception{
  create(demo(),"file","http://127.0.0.1:5173").sendOrder("synthetic@example.test","MG-test","Headphones × 2 — 20.00 ILS");
  try(var files=Files.list(directory)){String text=Files.readString(files.findFirst().orElseThrow());assertThat(text).contains("To: synthetic@example.test","Headphones × 2","/#order/MG-test","not a tax invoice","לא נגבה תשלום");}
 }
 @Test void missingDeliveryFailsClosedAndIncompleteSmtpCannotStart(){
  assertThatThrownBy(()->create(new MockEnvironment(),"disabled","https://example.test").available()).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->create(demo(),"smtp","https://example.test")).isInstanceOf(IllegalArgumentException.class);
 }
}
