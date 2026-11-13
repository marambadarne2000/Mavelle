package com.maramigo.accounts;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/** @author Maram Abbas */
@RestController
public class AdminIdentityController {
    @GetMapping("/api/admin/identity")
    public AccountService.UserView identity(@AuthenticationPrincipal AccountService.Principal user) { return user.view(); }
}
