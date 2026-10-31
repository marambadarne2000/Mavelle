package com.maramigo.accounts;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.concurrent.*;

/** Uniform public request handling: mail/database lookup runs outside HTTP response timing. @author Maram Abbas */
@Component
public class AccountRequestQueue {
 private final AccountLifecycle lifecycle; private final AccountMail mail;
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"account-mail");t.setDaemon(true);return t;});
 public AccountRequestQueue(AccountLifecycle lifecycle,AccountMail mail){this.lifecycle=lifecycle;this.mail=mail;}
 public void submit(String email,String purpose){
  mail.available();
  try{workers.execute(()->{try{lifecycle.request(email,purpose);}catch(Exception failure){org.slf4j.LoggerFactory.getLogger(AccountRequestQueue.class).warn("Account email request failed; retry is permitted. No account or token data is logged.");}});}
  catch(RejectedExecutionException full){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account email is busy. Please try again later.");}
 }
 @PreDestroy void stop(){workers.shutdownNow();}
}
