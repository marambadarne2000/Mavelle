package com.maramigo.accounts;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
/** Periodic recovery resumes committed requests after a restart. @author Maram Abbas */
@Component @EnableScheduling
@ConditionalOnProperty(name="maramigo.mail.worker.enabled",havingValue="true",matchIfMissing=true)
public class AccountMailPoller {
 private final AccountRequestQueue queue;public AccountMailPoller(AccountRequestQueue queue){this.queue=queue;}
 @Scheduled(fixedDelay=10000,initialDelay=10000) public void poll(){queue.recover();}
}
