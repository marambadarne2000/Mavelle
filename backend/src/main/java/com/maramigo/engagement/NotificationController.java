package com.maramigo.engagement;

import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** A bounded, account-scoped inbox; read receipts persist across devices. @author Maram Abbas */
@RestController @RequestMapping("/api/notifications") @Transactional(readOnly=true)
public class NotificationController {
 private final JdbcTemplate db;
 private final NamedParameterJdbcTemplate named;
 private final String sql;
 public NotificationController(JdbcTemplate db)throws java.io.IOException{
  this.db=db;this.named=new NamedParameterJdbcTemplate(db);
  try(var input=new ClassPathResource("notifications.sql").getInputStream()){sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
 }
 public record Update(String key,String title,String reference,String state,String target,Instant occurredAt,boolean read){}
 public record Inbox(List<Update> items,long unread,int retentionDays,int limit){}
 public record Read(@NotNull @Size(min=1,max=100) List<@NotBlank @Size(max=100) String> keys){}
 @GetMapping public Inbox inbox(@AuthenticationPrincipal AccountService.Principal u){
  return load(u,false,List.of("none"));
 }
 private Inbox load(AccountService.Principal u,boolean receipts,List<String> keys){
  var params=Map.of("user",u.id(),"manager",u.role().equals("ADMIN"),"employee",u.role().equals("EMPLOYEE"),"receipts",receipts,"keys",keys);
  var items=named.query(sql,params,(r,n)->new Update(r.getString("event_key"),r.getString("title"),r.getString("reference"),r.getString("state"),r.getString("target"),r.getTimestamp("occurred_at").toInstant(),r.getBoolean("is_read")));
  return new Inbox(items,items.stream().filter(i->!i.read()).count(),90,100);
 }
 @PostMapping("/read") @Transactional public Inbox read(@AuthenticationPrincipal AccountService.Principal u,@Valid @RequestBody Read form){
  if(db.queryForList("SELECT id FROM users WHERE id=? AND enabled=true FOR UPDATE",u.id()).isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This account is no longer active.");
  var owned=load(u,true,form.keys()).items().stream().map(Update::key).collect(java.util.stream.Collectors.toSet());
  if(!owned.containsAll(form.keys()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Update not found. Refresh your inbox.");
  for(String key:new HashSet<>(form.keys()))db.update("INSERT INTO notification_reads(user_id,event_key) VALUES (?,?) ON CONFLICT DO NOTHING",u.id(),key);
  db.update("DELETE FROM notification_reads WHERE user_id=? AND read_at<CURRENT_TIMESTAMP-INTERVAL '90 days'",u.id());
  return inbox(u);
 }
}
