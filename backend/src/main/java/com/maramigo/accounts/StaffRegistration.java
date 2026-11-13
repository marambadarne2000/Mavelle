package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** An invitation is email-bound, single use, expiring and never grants owner permissions. @author Maram Abbas */
@RestController
public class StaffRegistration {
 private final JdbcTemplate jdbc;private final PasswordEncoder passwords;
 public StaffRegistration(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
 public record Accept(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password){}
 @PostMapping("/api/auth/staff-register") @ResponseStatus(HttpStatus.CREATED) @Transactional
 public AccountService.UserView accept(@Valid @RequestBody Accept form){
  if(!AccountService.validPassword(form.password()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 12 characters, up to 72 UTF-8 bytes.");
  var rows=jdbc.queryForList("SELECT id,email,full_name,created_by FROM staff_invitations WHERE token_hash=? AND used_at IS NULL AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",OwnerController.hash(form.token()));
  if(rows.isEmpty() || !rows.get(0).get("email").equals(AccountService.normalize(form.email())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invitation is invalid, expired, used or belongs to a different email.");
  var invite=rows.get(0);String email=(String)invite.get("email");
  Long id=jdbc.queryForObject("INSERT INTO users(email,password_hash,role) VALUES (?,?,'EMPLOYEE') RETURNING id",Long.class,email,passwords.encode(form.password()));
  jdbc.update("INSERT INTO staff_profiles(user_id,full_name,created_by,invitation_id) VALUES (?,?,?,?)",id,invite.get("full_name"),invite.get("created_by"),invite.get("id"));
  jdbc.update("UPDATE staff_invitations SET used_at=CURRENT_TIMESTAMP WHERE id=?",invite.get("id"));
  return new AccountService.UserView(id,email,"EMPLOYEE");
 }
}
