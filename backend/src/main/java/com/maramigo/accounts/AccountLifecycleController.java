package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** No public response reveals whether the requested email is registered. @author Maram Abbas */
@RestController @RequestMapping("/api/auth")
public class AccountLifecycleController {
    private final AccountLifecycle lifecycle; private final AccountRequestQueue requests; private final AccountMail mail; private final JdbcTemplate db; private final boolean required;
    public AccountLifecycleController(AccountLifecycle lifecycle,AccountRequestQueue requests,AccountMail mail,JdbcTemplate db,@Value("${maramigo.verification-required:true}") boolean required){this.lifecycle=lifecycle;this.requests=requests;this.mail=mail;this.db=db;this.required=required;}
    public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email){}
    public record Token(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){}
    public record Reset(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(max=72) String password){}
    public record Options(String deliveryMode,boolean verificationRequired){}
    public record Status(boolean emailVerified,boolean verificationRequired,String deliveryMode){}
    @GetMapping("/options") public Options options(){return new Options(mail.mode(),required);}
    @GetMapping("/security") public Status status(@AuthenticationPrincipal AccountService.Principal user){return new Status(Boolean.TRUE.equals(db.queryForObject("SELECT email_verified FROM users WHERE id=?",Boolean.class,user.id())),required,mail.mode());}
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED) public void forgot(@Valid @RequestBody Email form){requests.submit(form.email(),"RESET");}
    @PostMapping("/verification-request") @ResponseStatus(HttpStatus.ACCEPTED) public void verification(@Valid @RequestBody Email form){requests.submit(form.email(),"VERIFY");}
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@Valid @RequestBody Reset form){lifecycle.consume(form.token(),"RESET",form.password());}
    @PostMapping("/confirm-email-change") @ResponseStatus(HttpStatus.NO_CONTENT) public void email(@Valid @RequestBody Token form){lifecycle.consume(form.token(),"EMAIL",null);}
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT) public void verify(@Valid @RequestBody Token form){lifecycle.consume(form.token(),"VERIFY",null);}
}
