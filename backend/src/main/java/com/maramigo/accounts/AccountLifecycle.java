package com.maramigo.accounts;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

/** Tokens are random, hashed, purpose-bound, expiring and consumed under the account lock. @author Maram Abbas */
@Service
public class AccountLifecycle {
    private final JdbcTemplate db; private final PasswordEncoder passwords; private final AccountMail mail;
    public AccountLifecycle(JdbcTemplate db,PasswordEncoder passwords,AccountMail mail){this.db=db;this.passwords=passwords;this.mail=mail;}
    @Transactional public void request(String email,String purpose){
        mail.available(); // Same response when delivery is disabled, whether the account exists or not.
        var users=db.queryForList("SELECT id,email_verified,pending_email FROM users WHERE email=? AND enabled=true FOR UPDATE",AccountService.normalize(email));
        if(users.isEmpty())return;
        long id=((Number)users.get(0).get("id")).longValue();
        if(purpose.equals("VERIFY") && Boolean.TRUE.equals(users.get(0).get("email_verified")))return;
        if(!db.queryForList("SELECT token_hash FROM account_tokens WHERE user_id=? AND purpose=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '60 seconds'",id,purpose).isEmpty())return;
        db.update("DELETE FROM account_tokens WHERE user_id=? AND (purpose=? OR expires_at<CURRENT_TIMESTAMP OR used_at IS NOT NULL)",id,purpose);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("INSERT INTO account_tokens(token_hash,user_id,purpose,expires_at) VALUES (?,?,?,?)",OwnerController.hash(token),id,purpose,
            Timestamp.from(Instant.now().plusSeconds(purpose.equals("RESET")?1800:86400)));
        String recipient=purpose.equals("EMAIL")?(String)users.get(0).get("pending_email"):AccountService.normalize(email);
        if(recipient==null)throw invalid();
        mail.send(recipient,purpose,token);
    }
    @Transactional public void consume(String token,String purpose,String password){
        if(purpose.equals("RESET") && !AccountService.validPassword(password))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 12 characters, up to 72 UTF-8 bytes.");
        String hash=OwnerController.hash(token);
        var ids=db.queryForList("SELECT user_id FROM account_tokens WHERE token_hash=? AND purpose=?",Long.class,hash,purpose);
        if(ids.isEmpty())throw invalid();
        long id=ids.get(0);
        // Requests and consumption take the same lock order, including two different tokens for one user.
        if(db.queryForList("SELECT id FROM users WHERE id=? AND enabled=true FOR UPDATE",id).isEmpty())throw invalid();
        if(db.update("UPDATE account_tokens SET used_at=CURRENT_TIMESTAMP WHERE token_hash=? AND purpose=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",hash,purpose)!=1)throw invalid();
        if(purpose.equals("VERIFY"))db.update("UPDATE users SET email_verified=true WHERE id=?",id);
        else if(purpose.equals("EMAIL")){
            if(db.update("UPDATE users SET email=pending_email,pending_email=NULL,email_verified=true,auth_version=auth_version+1 WHERE id=? AND pending_email IS NOT NULL",id)!=1)throw invalid();
            db.update("UPDATE account_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",id);
        }else {
            db.update("UPDATE users SET password_hash=?,auth_version=auth_version+1 WHERE id=?",passwords.encode(password),id);
            db.update("UPDATE account_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",id);
        }
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"This link is invalid, expired or already used. Request a new link.");}
}
