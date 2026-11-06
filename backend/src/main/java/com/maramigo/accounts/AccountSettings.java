package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Reauthentication, scoped exports and reviewed closure retain an honest audit trail. @author Maram Abbas */
@RestController
public class AccountSettings {
 private final JdbcTemplate db;private final PasswordEncoder passwords;private final AccountRequestQueue queue;private final AccountMail mail;private final org.springframework.transaction.support.TransactionTemplate transactions;
 public AccountSettings(JdbcTemplate db,PasswordEncoder passwords,AccountRequestQueue queue,AccountMail mail,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.passwords=passwords;this.queue=queue;this.mail=mail;this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);}
 private void lock(long id){db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,id);}
 private void reauth(long id,String password){String hash=db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id);if(!AccountService.validPassword(password)||!passwords.matches(password,hash))throw fail(HttpStatus.BAD_REQUEST,"The current password is incorrect.");}
 public record Password(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(max=72) String newPassword){}
 public record Email(@NotBlank @Size(max=72) String currentPassword,@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email){}
 public record Closure(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(max=500) String reason){}
 public record Decision(@NotBlank @Pattern(regexp="COMPLETED|REJECTED") String status,@NotBlank @Size(max=500) String reason){}
 @PostMapping("/api/account/password") @Transactional public void password(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Password form){lock(user.id());reauth(user.id(),form.currentPassword());if(!AccountService.validPassword(form.newPassword()))throw fail(HttpStatus.BAD_REQUEST,"Use at least 12 characters, up to 72 UTF-8 bytes.");db.update("UPDATE users SET password_hash=?,auth_version=auth_version+1 WHERE id=?",passwords.encode(form.newPassword()),user.id());db.update("DELETE FROM account_tokens WHERE user_id=?",user.id());}
 @PostMapping("/api/account/email") public void email(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Email form){mail.available();changeEmail(user,form);queue.submit(user.email(),"EMAIL");}
 // A separate TransactionTemplate makes the pending address visible before the queued lookup starts.
 private void changeEmail(AccountService.Principal user,Email form){transactions.executeWithoutResult(status->{lock(user.id());reauth(user.id(),form.currentPassword());String email=AccountService.normalize(form.email());if(db.queryForObject("SELECT count(*) FROM users WHERE email=?",Long.class,email)>0)throw fail(HttpStatus.CONFLICT,"An account with this email already exists.");db.update("UPDATE users SET pending_email=? WHERE id=?",email,user.id());db.update("DELETE FROM account_tokens WHERE user_id=? AND purpose='EMAIL'",user.id());});}
 @GetMapping("/api/account/export") public Map<String,Object> export(@AuthenticationPrincipal AccountService.Principal user){var result=new LinkedHashMap<String,Object>();result.put("account",Map.of("id",user.id(),"email",user.email(),"role",user.role()));result.put("profile",db.queryForList("SELECT full_name,phone,address,city FROM customer_profiles WHERE user_id=?",user.id()));result.put("storeProfile",db.queryForList("SELECT owner_name,business_type,registration_number,phone,contact_email FROM merchant_stores WHERE user_id=?",user.id()));result.put("workProfile",db.queryForList("SELECT full_name FROM staff_profiles WHERE user_id=?",user.id()));result.put("orders",db.queryForList("SELECT order_number,status,total_price,currency,shipping,created_at FROM orders WHERE user_id=? ORDER BY id",user.id()));result.put("messages",db.queryForList("SELECT company_id,body,created_at FROM store_messages WHERE sender_id=? ORDER BY id",user.id()));result.put("reviews",db.queryForList("SELECT company_id,rating,body,updated_at FROM store_reviews WHERE customer_id=?",user.id()));result.put("favorites",db.queryForList("SELECT product_id,created_at FROM favorites WHERE user_id=?",user.id()));result.put("privacyRequests",privacy(user));result.put("supportRequests",db.queryForList("SELECT id,subject,category,status,created_at,updated_at FROM support_tickets WHERE requester_id=? ORDER BY id",user.id()));result.put("supportMessages",db.queryForList("SELECT m.ticket_id,m.body,m.created_at FROM support_messages m JOIN support_tickets t ON t.id=m.ticket_id WHERE t.requester_id=? ORDER BY m.id",user.id()));result.put("notificationReads",db.queryForList("SELECT event_key,read_at FROM notification_reads WHERE user_id=?",user.id()));return result;}
 @GetMapping("/api/account/privacy") public List<Map<String,Object>> privacy(@AuthenticationPrincipal AccountService.Principal user){return db.queryForList("SELECT id,status,reason,resolution,created_at,resolved_at FROM privacy_requests WHERE user_id=? ORDER BY id DESC",user.id());}
 @PostMapping("/api/account/privacy") @Transactional public void privacy(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Closure form){lock(user.id());reauth(user.id(),form.currentPassword());if(!Set.of("CUSTOMER","MERCHANT").contains(user.role()))throw fail(HttpStatus.CONFLICT,"Platform and employee accounts must be managed by the owner.");if(db.queryForObject("SELECT count(*) FROM privacy_requests WHERE user_id=? AND status='PENDING'",Long.class,user.id())==0)db.update("INSERT INTO privacy_requests(user_id,reason) VALUES (?,?)",user.id(),form.reason().trim());}
 @GetMapping("/api/admin/privacy") public List<Map<String,Object>> all(){return db.queryForList("SELECT p.id,p.user_id,u.email,u.role,p.status,p.reason,p.resolution,p.created_at FROM privacy_requests p JOIN users u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 200");}
 @PostMapping("/api/admin/privacy/{id}") @Transactional public void decision(@PathVariable long id,@Valid @RequestBody Decision form,@AuthenticationPrincipal AccountService.Principal actor){var rows=db.queryForList("SELECT user_id,status FROM privacy_requests WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Request not found.");if(!rows.get(0).get("status").equals("PENDING"))return;long user=((Number)rows.get(0).get("user_id")).longValue();lock(user);
  if(form.status().equals("COMPLETED")){
   db.queryForList("SELECT c.id FROM companies c JOIN merchant_stores s ON s.company_id=c.id WHERE s.user_id=? ORDER BY c.id FOR UPDATE OF c",user);
   if(db.queryForObject("SELECT count(*) FROM orders WHERE user_id=? AND status IN ('PENDING','SHIPPED')",Long.class,user)>0||db.queryForObject("SELECT count(*) FROM merchant_orders m JOIN merchant_stores s ON s.company_id=m.company_id WHERE s.user_id=? AND m.status IN ('PENDING','SHIPPED')",Long.class,user)>0)throw fail(HttpStatus.CONFLICT,"Complete open orders before closing this account.");
   db.update("DELETE FROM account_mail_jobs WHERE email IN (SELECT email FROM users WHERE id=? UNION SELECT pending_email FROM users WHERE id=?)",user,user);
   db.update("UPDATE users SET email=?,pending_email=NULL,password_hash=?,enabled=false,email_verified=false,auth_version=auth_version+1 WHERE id=? AND role IN ('CUSTOMER','MERCHANT')","closed-"+user+"@example.invalid",passwords.encode(UUID.randomUUID().toString()),user);
   db.update("DELETE FROM notification_reads WHERE user_id=?",user);
   db.update("UPDATE support_messages SET body='[Content removed after account closure]' WHERE ticket_id IN (SELECT id FROM support_tickets WHERE requester_id=?) OR sender_id=?",user,user);
   db.update("UPDATE support_tickets SET subject='[Content removed after account closure]',status='RESOLVED',version=version+1,updated_at=CURRENT_TIMESTAMP,status_changed_at=CURRENT_TIMESTAMP WHERE requester_id=?",user);
   db.update("DELETE FROM customer_profiles WHERE user_id=?",user);db.update("DELETE FROM favorites WHERE user_id=?",user);db.update("DELETE FROM store_reviews WHERE customer_id=?",user);db.update("DELETE FROM account_tokens WHERE user_id=?",user);
   db.update("UPDATE store_messages SET body='[Content removed after account closure]' WHERE sender_id=?",user);
   db.update("UPDATE orders SET shipping='{\"fullName\":\"Removed\",\"phone\":\"Removed\",\"address\":\"Removed\",\"city\":\"Removed\",\"postalCode\":\"Removed\",\"country\":\"IL\"}'::jsonb WHERE user_id=?",user);
   db.update("UPDATE companies SET status='SUSPENDED' WHERE id IN (SELECT company_id FROM merchant_stores WHERE user_id=?)",user);
   db.update("UPDATE merchant_stores SET owner_name='Removed',phone='',contact_email=?,registration_number='' WHERE user_id=?","closed-"+user+"@example.invalid",user);
  }
  db.update("UPDATE privacy_requests SET status=?,resolution=?,actor_id=?,resolved_at=CURRENT_TIMESTAMP WHERE id=?",form.status(),form.reason().trim(),actor.id(),id);
 }
 private ResponseStatusException fail(HttpStatus status,String text){return new ResponseStatusException(status,text);}
}
