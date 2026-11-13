package com.maramigo.accounts;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.*;
import java.util.*;
/** Durable requests, bounded workers and generic public responses. @author Maram Abbas */
@Component
public class AccountRequestQueue {
 private final AccountLifecycle lifecycle;private final AccountMail mail;private final JdbcTemplate db;private final TransactionTemplate transactions;
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"account-mail");t.setDaemon(true);return t;});
 public AccountRequestQueue(AccountLifecycle lifecycle,AccountMail mail,JdbcTemplate db,PlatformTransactionManager manager){this.lifecycle=lifecycle;this.mail=mail;this.db=db;transactions=new TransactionTemplate(manager);}
 public void submit(String email,String purpose){mail.available();transactions.executeWithoutResult(status->{db.queryForList("SELECT pg_advisory_xact_lock(731903021)");if(db.queryForObject("SELECT count(*) FROM account_mail_jobs WHERE status IN ('PENDING','RUNNING')",Long.class)>=1000)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account email is busy. Please try again later.");db.update("INSERT INTO account_mail_jobs(id,email,purpose) VALUES (?,?,?)",UUID.randomUUID(),AccountService.normalize(email),purpose);});kick();}
 public void kick(){if(workers.isShutdown())return;try{workers.execute(this::drain);}catch(RejectedExecutionException busy){/* Persisted work will be picked up by the poller. */}}
 public void recover(){db.update("UPDATE account_mail_jobs SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,next_attempt=CURRENT_TIMESTAMP WHERE status='RUNNING' AND claimed_at<CURRENT_TIMESTAMP-INTERVAL '90 seconds'");db.update("DELETE FROM account_mail_jobs WHERE status IN ('COMPLETED','FAILED') AND created_at<CURRENT_TIMESTAMP-INTERVAL '7 days'");kick();}
 private void drain(){for(int batch=0;batch<20;batch++){
  var job=transactions.execute(status->{var rows=db.queryForList("SELECT id,email,purpose,attempts FROM account_mail_jobs WHERE status='PENDING' AND next_attempt<=CURRENT_TIMESTAMP ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1");if(rows.isEmpty())return null;var row=rows.get(0);db.update("UPDATE account_mail_jobs SET status='RUNNING',attempts=attempts+1,claimed_at=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));return row;});if(job==null)return;
  try{lifecycle.request((String)job.get("email"),(String)job.get("purpose"));db.update("UPDATE account_mail_jobs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?",job.get("id"));}
  catch(Exception failure){boolean exhausted=((Number)job.get("attempts")).intValue()+1>=3;db.update("UPDATE account_mail_jobs SET status=?,next_attempt=CURRENT_TIMESTAMP+INTERVAL '1 minute' WHERE id=?",exhausted?"FAILED":"PENDING",job.get("id"));org.slf4j.LoggerFactory.getLogger(AccountRequestQueue.class).warn("Account email delivery failed; a bounded retry is scheduled when permitted.");}
 }}
 @PreDestroy void stop(){workers.shutdownNow();}
}
