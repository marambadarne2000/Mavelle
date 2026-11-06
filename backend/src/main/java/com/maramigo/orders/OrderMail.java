package com.maramigo.orders;

import com.maramigo.accounts.AccountMail;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.math.BigDecimal;

/** Durable confirmation outbox: checkout commits before delivery; no card or address data in mail. @author Maram Abbas */
@Component
public class OrderMail {
 private final JdbcTemplate db;private final AccountMail mail;private final TransactionTemplate transactions;private final boolean enabled;
 public OrderMail(JdbcTemplate db,AccountMail mail,PlatformTransactionManager manager,@org.springframework.beans.factory.annotation.Value("${maramigo.mail.worker.enabled:true}") boolean enabled){this.db=db;this.mail=mail;transactions=new TransactionTemplate(manager);this.enabled=enabled;}
 public record Status(String status,String deliveryMode){}
 public Status status(long userId,String number){
  var rows=db.query("SELECT j.status,j.delivery_mode FROM order_mail_jobs j JOIN orders o ON o.id=j.order_id WHERE o.user_id=? AND o.order_number=?",(r,n)->new Status(r.getString(1),r.getString(2)),userId,number);
  return rows.isEmpty()?new Status("NOT_REQUESTED",null):rows.get(0);
 }
 @Scheduled(fixedDelay=10000,initialDelay=15000) public void deliverPending(){
  if(!enabled||!java.util.Set.of("smtp","file").contains(mail.mode()))return;
  db.update("UPDATE order_mail_jobs SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,next_attempt=CURRENT_TIMESTAMP WHERE status='RUNNING' AND claimed_at<CURRENT_TIMESTAMP-INTERVAL '90 seconds'");
  for(int i=0;i<10;i++){
   var job=transactions.execute(tx->{var rows=db.queryForList("SELECT j.order_id,j.recipient,j.attempts,o.order_number,o.total_price,o.currency FROM order_mail_jobs j JOIN orders o ON o.id=j.order_id WHERE j.status='PENDING' AND j.next_attempt<=CURRENT_TIMESTAMP ORDER BY j.created_at FOR UPDATE OF j SKIP LOCKED LIMIT 1");if(rows.isEmpty())return null;var row=rows.get(0);db.update("UPDATE order_mail_jobs SET status='RUNNING',attempts=attempts+1,claimed_at=CURRENT_TIMESTAMP WHERE order_id=?",row.get("order_id"));return row;});
   if(job==null)return;
   try{
    var lines=db.query("SELECT name,company_name,quantity,unit_price FROM order_items WHERE order_id=? ORDER BY id",(r,n)->r.getString(1)+" / "+r.getString(2)+" × "+r.getInt(3)+" — "+r.getBigDecimal(4).multiply(BigDecimal.valueOf(r.getInt(3))).toPlainString()+" "+job.get("currency"),job.get("order_id"));
    String summary="Mavelle\nOrder / הזמנה / طلب: "+job.get("order_number")+"\n\n"+String.join("\n",lines)+"\n\nTotal / סך הכול / المجموع: "+job.get("total_price")+" "+job.get("currency");
    mail.sendOrder((String)job.get("recipient"),(String)job.get("order_number"),summary);
    db.update("UPDATE order_mail_jobs SET status='COMPLETED',delivery_mode=?,completed_at=CURRENT_TIMESTAMP WHERE order_id=?",mail.mode(),job.get("order_id"));
   }catch(Exception failure){db.update("UPDATE order_mail_jobs SET status=?,next_attempt=CURRENT_TIMESTAMP+INTERVAL '1 minute' WHERE order_id=?",((Number)job.get("attempts")).intValue()>=2?"FAILED":"PENDING",job.get("order_id"));}
  }
 }
}
