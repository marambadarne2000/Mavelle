package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** A single provisioned site owner controls business details and employee invitations. @author Maram Abbas */
@RestController @RequestMapping("/api/admin/owner")
public class OwnerController {
 private final JdbcTemplate jdbc;
 public OwnerController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Profile(@NotBlank @Size(max=120) String ownerName,@NotBlank @Size(max=160) String businessName,
  @Size(max=40) String registrationNumber,@NotBlank @Email @Size(max=254) String contactEmail,@NotBlank @Size(max=40) String phone){}
 public record Invitation(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=120) String fullName){}
 public record Issued(long id,String token,Instant expiresAt){}
 public record Employee(long id,String email,String fullName,boolean enabled){}
 public record Pending(long id,String email,String fullName,Instant expiresAt){}
 public record Enabled(@NotNull Boolean enabled){}
 @GetMapping("/profile") public Profile profile(){
  var rows=jdbc.query("SELECT owner_name,business_name,registration_number,contact_email,phone FROM business_profile WHERE id=1",(r,n)->new Profile(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)));
  return rows.isEmpty()?new Profile("","","","",""):rows.get(0);
 }
 @PutMapping("/profile") public Profile profile(@Valid @RequestBody Profile p,@AuthenticationPrincipal AccountService.Principal owner){
  jdbc.update("INSERT INTO business_profile(id,owner_id,owner_name,business_name,registration_number,contact_email,phone) VALUES (1,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET owner_name=excluded.owner_name,business_name=excluded.business_name,registration_number=excluded.registration_number,contact_email=excluded.contact_email,phone=excluded.phone,updated_at=CURRENT_TIMESTAMP",owner.id(),p.ownerName().trim(),p.businessName().trim(),p.registrationNumber()==null?"":p.registrationNumber().trim(),AccountService.normalize(p.contactEmail()),p.phone().trim());
  return profile();
 }
 @GetMapping("/employees") public List<Employee> employees(){return jdbc.query("SELECT u.id,u.email,s.full_name,u.enabled FROM users u JOIN staff_profiles s ON s.user_id=u.id WHERE u.role='EMPLOYEE' ORDER BY u.id",(r,n)->new Employee(r.getLong(1),r.getString(2),r.getString(3),r.getBoolean(4)));}
 @PatchMapping("/employees/{id}") public void enabled(@PathVariable long id,@Valid @RequestBody Enabled form){
  if(jdbc.update("UPDATE users SET enabled=? WHERE id=? AND role='EMPLOYEE'",form.enabled(),id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Employee not found.");
 }
 @GetMapping("/invitations") public List<Pending> invitations(){return jdbc.query("SELECT id,email,full_name,expires_at FROM staff_invitations WHERE used_at IS NULL AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP ORDER BY id DESC",(r,n)->new Pending(r.getLong(1),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()));}
 @PostMapping("/invitations") @Transactional public Issued invite(@Valid @RequestBody Invitation form,@AuthenticationPrincipal AccountService.Principal owner){
  String email=AccountService.normalize(form.email());
  // Serializes invitations for an address, including across application instances.
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","staff:"+email);
  if(jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?",Long.class,email)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This email already has an account. Use a separate work email.");
  if(jdbc.queryForObject("SELECT count(*) FROM staff_invitations WHERE email=? AND used_at IS NULL AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP",Long.class,email)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"An active invitation already exists for this email.");
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  Instant expires=Instant.now().plusSeconds(86400);
  long id=jdbc.queryForObject("INSERT INTO staff_invitations(email,full_name,token_hash,created_by,expires_at) VALUES (?,?,?,?,?) RETURNING id",Long.class,email,form.fullName().trim(),hash(token),owner.id(),java.sql.Timestamp.from(expires));
  return new Issued(id,token,expires);
 }
 @DeleteMapping("/invitations/{id}") public void revoke(@PathVariable long id){jdbc.update("UPDATE staff_invitations SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND used_at IS NULL",id);}
 static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
