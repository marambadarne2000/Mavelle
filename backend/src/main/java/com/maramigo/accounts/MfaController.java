package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Owner challenge and factor management share no public bypass. @author Maram Abbas */
@RestController @RequestMapping("/api/auth/mfa")
public class MfaController {
 private final MfaService mfa;public MfaController(MfaService mfa){this.mfa=mfa;}
 public record Password(@NotBlank @Size(max=72) String password){}
 public record Code(@NotBlank @Size(max=24) String code){}
 public record Manage(@NotBlank @Size(max=72) String password,@NotBlank @Size(max=24) String code){}
 private void owner(AccountService.Principal user){if(!user.role().equals("ADMIN"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This action is for the platform owner.");}
 @GetMapping public Map<String,Boolean> status(@AuthenticationPrincipal AccountService.Principal user){owner(user);return Map.of("enabled",mfa.enabled(user.id()),"available",mfa.available());}
 @PostMapping("/setup") public MfaService.Setup setup(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Password form){owner(user);return mfa.setup(user,form.password());}
 @PostMapping("/confirm") public Map<String,List<String>> confirm(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Code form){owner(user);return Map.of("recoveryCodes",mfa.confirm(user.id(),form.code()));}
 @PostMapping("/verify") public AccountService.UserView verify(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Code form,HttpServletRequest request){
  owner(user);var session=request.getSession(false);if(session==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification expired. Sign in again.");synchronized(session){Long expires=session==null?null:(Long)session.getAttribute("MFA_EXPIRES");Integer attempts=session==null?null:(Integer)session.getAttribute("MFA_ATTEMPTS");
  if(expires==null||expires<System.currentTimeMillis()||attempts==null||attempts>=5){if(session!=null)session.invalidate();throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification expired. Sign in again.");}
  session.setAttribute("MFA_ATTEMPTS",attempts+1);mfa.verify(user.id(),form.code());request.changeSessionId();session.setAttribute("MFA_VERIFIED",true);session.removeAttribute("MFA_EXPIRES");return user.view();}
 }
 @PostMapping("/disable") public void disable(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Manage form){owner(user);mfa.disable(user.id(),form.password(),form.code());}
 @PostMapping("/recovery-codes") public Map<String,List<String>> regenerate(@AuthenticationPrincipal AccountService.Principal user,@Valid @RequestBody Manage form){owner(user);return Map.of("recoveryCodes",mfa.regenerate(user.id(),form.password(),form.code()));}
}
