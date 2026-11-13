package com.maramigo.accounts;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Enrolment is explicit; tests never enrol the normal owner. @author Maram Abbas */
@Service
public class MfaService {
 private final JdbcTemplate db;private final PasswordEncoder passwords;private final SecretVault vault;
 public MfaService(JdbcTemplate db,PasswordEncoder passwords,SecretVault vault){this.db=db;this.passwords=passwords;this.vault=vault;}
 public boolean enabled(long id){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM account_mfa WHERE user_id=? AND enabled)",Boolean.class,id));}
 public boolean available(){return vault.available();}
 private void lock(long id){db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,id);}
 private void password(long id,String password){String hash=db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id);if(!AccountService.validPassword(password)||!passwords.matches(password,hash))throw fail("The password or verification code is incorrect.");}
 public record Setup(String secret,String uri){}
 @Transactional public Setup setup(AccountService.Principal user,String password){lock(user.id());password(user.id(),password);if(enabled(user.id()))throw fail("Two-step verification is already enabled.");String secret=Totp.secret();db.update("INSERT INTO account_mfa(user_id,pending_cipher,pending_expires) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET pending_cipher=excluded.pending_cipher,pending_expires=excluded.pending_expires",user.id(),vault.encrypt(secret),java.sql.Timestamp.from(Instant.now().plusSeconds(600)));
  return new Setup(secret,"otpauth://totp/"+URLEncoder.encode("Mavelle:"+user.email(),StandardCharsets.UTF_8)+"?secret="+secret+"&issuer=Mavelle&algorithm=SHA1&digits=6&period=30");}
 @Transactional public List<String> confirm(long id,String code){lock(id);var rows=db.queryForList("SELECT pending_cipher FROM account_mfa WHERE user_id=? AND NOT enabled AND pending_expires>CURRENT_TIMESTAMP",id);if(rows.isEmpty())throw fail("Authenticator setup expired. Start again.");String secret=vault.decrypt((String)rows.get(0).get("pending_cipher"));long step=Totp.match(secret,code,Instant.now().getEpochSecond(),-1);if(step<0)throw fail("The password or verification code is incorrect.");db.update("UPDATE account_mfa SET secret_cipher=pending_cipher,pending_cipher=NULL,pending_expires=NULL,enabled=true,last_step=? WHERE user_id=?",step,id);db.update("UPDATE users SET auth_version=auth_version+1 WHERE id=?",id);return codes(id);}
 private List<String> codes(long id){db.update("DELETE FROM mfa_recovery_codes WHERE user_id=?",id);var codes=new ArrayList<String>();for(int i=0;i<10;i++){byte[] bytes=new byte[12];new java.security.SecureRandom().nextBytes(bytes);String code=HexFormat.of().formatHex(bytes);codes.add(code);db.update("INSERT INTO mfa_recovery_codes(user_id,code_hash) VALUES (?,?)",id,OwnerController.hash(code));}return codes;}
 @Transactional public void verify(long id,String code){lock(id);accept(id,code);}
 private void accept(long id,String code){var rows=db.queryForList("SELECT secret_cipher,last_step FROM account_mfa WHERE user_id=? AND enabled",id);if(rows.isEmpty())throw fail("Two-step verification is not enabled.");
  if(code!=null&&code.matches("[a-f0-9]{24}")&&db.update("UPDATE mfa_recovery_codes SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND code_hash=? AND used_at IS NULL",id,OwnerController.hash(code))==1)return;
  var row=rows.get(0);long step=Totp.match(vault.decrypt((String)row.get("secret_cipher")),code,Instant.now().getEpochSecond(),((Number)row.get("last_step")).longValue());if(step<0)throw fail("The password or verification code is incorrect.");db.update("UPDATE account_mfa SET last_step=? WHERE user_id=?",step,id);
 }
 @Transactional public void disable(long id,String password,String code){lock(id);password(id,password);accept(id,code);db.update("DELETE FROM account_mfa WHERE user_id=?",id);db.update("DELETE FROM mfa_recovery_codes WHERE user_id=?",id);db.update("UPDATE users SET auth_version=auth_version+1 WHERE id=?",id);}
 @Transactional public List<String> regenerate(long id,String password,String code){lock(id);password(id,password);accept(id,code);return codes(id);}
 private ResponseStatusException fail(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
}
