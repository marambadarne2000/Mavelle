package com.maramigo.engagement;

import com.maramigo.accounts.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Private platform assistance, separate from store conversations. @author Maram Abbas */
@RestController @RequestMapping("/api/support/tickets") @Transactional(readOnly=true)
public class SupportController {
 private final JdbcTemplate db;
 public SupportController(JdbcTemplate db){this.db=db;}
 public record Ticket(long id,String subject,String category,String status,long version,Instant createdAt,Instant updatedAt,String requesterRole,boolean ownTicket){}
 public record Message(long id,String body,String senderRole,boolean mine,Instant createdAt){}
 public record Detail(Ticket ticket,List<Message> messages,boolean hasOlder){}
 public record TicketPage(List<Ticket> items,boolean hasMore){}
 public record Create(@NotBlank @Size(max=160) String subject,@NotNull @Pattern(regexp="ORDER|ACCOUNT|STORE|TECHNICAL|OTHER") String category,@NotBlank @Size(max=2000) String body){}
 public record Reply(@NotBlank @Size(max=2000) String body){}
 public record Change(@NotNull @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED") String status,@PositiveOrZero long version){}
 private boolean admin(AccountService.Principal u){return "ADMIN".equals(u.role());}
 private void active(long id){if(db.queryForList("SELECT id FROM users WHERE id=? AND enabled=true FOR UPDATE",id).isEmpty())throw fail(HttpStatus.FORBIDDEN,"This account is no longer active.");}
 private Ticket ticket(java.sql.ResultSet r,AccountService.Principal u)throws java.sql.SQLException{return new Ticket(r.getLong("id"),r.getString("subject"),r.getString("category"),r.getString("status"),r.getLong("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getString("role"),r.getLong("requester_id")==u.id());}
 private Ticket find(long id,AccountService.Principal u,boolean lock){
  var rows=db.query("SELECT t.*,u.role FROM support_tickets t JOIN users u ON u.id=t.requester_id WHERE t.id=? AND (? OR t.requester_id=?)"+(lock?" FOR UPDATE OF t":""),(r,n)->ticket(r,u),id,admin(u),u.id());
  if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Support request not found.");return rows.get(0);
 }
 @GetMapping public TicketPage list(@AuthenticationPrincipal AccountService.Principal u,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="") @Pattern(regexp="|OPEN|IN_PROGRESS|RESOLVED") String status){
  var rows=db.query("SELECT t.*,u.role FROM support_tickets t JOIN users u ON u.id=t.requester_id WHERE (? OR t.requester_id=?) AND (?='' OR t.status=?) ORDER BY t.updated_at DESC,t.id DESC LIMIT 21 OFFSET ?",(r,n)->ticket(r,u),admin(u),u.id(),status,status,page*20L);
  return new TicketPage(rows.stream().limit(20).toList(),rows.size()>20);
 }
 @GetMapping("/{id}") public Detail detail(@PathVariable @Positive long id,@AuthenticationPrincipal AccountService.Principal u,@RequestParam(required=false) @Positive Long before){
  Ticket t=find(id,u,false);long cursor=before==null?Long.MAX_VALUE:before;
  var messages=db.query("SELECT m.*,u.role FROM support_messages m JOIN users u ON u.id=m.sender_id WHERE m.ticket_id=? AND m.id<? ORDER BY m.id DESC LIMIT 51",(r,n)->new Message(r.getLong("id"),r.getString("body"),r.getString("role"),r.getLong("sender_id")==u.id(),r.getTimestamp("created_at").toInstant()),id,cursor);
  boolean older=messages.size()>50;var visible=new ArrayList<>(messages.stream().limit(50).toList());Collections.reverse(visible);return new Detail(t,visible,older);
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional public Detail create(@AuthenticationPrincipal AccountService.Principal u,@Valid @RequestBody Create form){
  active(u.id());
  if(db.queryForObject("SELECT count(*) FROM support_tickets WHERE requester_id=? AND (status<>'RESOLVED' OR created_at>CURRENT_TIMESTAMP-INTERVAL '1 hour')",Long.class,u.id())>=10)throw fail(HttpStatus.TOO_MANY_REQUESTS,"Too many support requests. Resolve an existing request or try again later.");
  Long id=db.queryForObject("INSERT INTO support_tickets(requester_id,subject,category) VALUES (?,?,?) RETURNING id",Long.class,u.id(),form.subject().strip(),form.category());
  db.update("INSERT INTO support_messages(ticket_id,sender_id,body) VALUES (?,?,?)",id,u.id(),form.body().strip());return detail(id,u,null);
 }
 @PostMapping("/{id}/messages") @Transactional public Detail reply(@PathVariable @Positive long id,@AuthenticationPrincipal AccountService.Principal u,@Valid @RequestBody Reply form){
  active(u.id());Ticket t=find(id,u,true);
  if("RESOLVED".equals(t.status()))throw fail(HttpStatus.CONFLICT,"Reopen this request before replying.");
  if(db.queryForObject("SELECT count(*) FROM support_messages WHERE sender_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '10 minutes'",Long.class,u.id())>=20)throw fail(HttpStatus.TOO_MANY_REQUESTS,"Too many support messages. Please try again later.");
  db.update("INSERT INTO support_messages(ticket_id,sender_id,body) VALUES (?,?,?)",id,u.id(),form.body().strip());
  db.update("UPDATE support_tickets SET updated_at=CURRENT_TIMESTAMP WHERE id=?",id);return detail(id,u,null);
 }
 @PatchMapping("/{id}") @Transactional public Detail change(@PathVariable @Positive long id,@AuthenticationPrincipal AccountService.Principal u,@Valid @RequestBody Change form){
  active(u.id());Ticket t=find(id,u,true);
  if(t.version()!=form.version())throw fail(HttpStatus.CONFLICT,"This support request changed. Refresh before updating it.");
  if(t.status().equals(form.status()))return detail(id,u,null);
  if(!admin(u)&&!(form.status().equals("RESOLVED")||t.status().equals("RESOLVED")&&form.status().equals("OPEN")))throw fail(HttpStatus.FORBIDDEN,"Only the platform owner can assign a request for review.");
  db.update("UPDATE support_tickets SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP,status_changed_at=CURRENT_TIMESTAMP WHERE id=?",form.status(),id);return detail(id,u,null);
 }
 private ResponseStatusException fail(HttpStatus status,String text){return new ResponseStatusException(status,text);}
}
