package com.maramigo.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** @author Maram Abbas */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }
    public record Registration(@NotBlank @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record CsrfView(String token, String headerName) {}
    @GetMapping("/csrf") public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getToken(), token.getHeaderName()); }
    @GetMapping("/me") public AccountService.UserView me(@AuthenticationPrincipal AccountService.Principal user) { return user.view(); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AccountService.UserView register(@Valid @RequestBody Registration form) {
        var email = AccountService.normalize(form.email());
        // Conservative portfolio scope: ASCII email addresses with a dotted domain.
        if (!email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
            || email.startsWith(".") || email.substring(0, email.indexOf('@')).endsWith(".") || email.contains("..")
            || !AccountService.validPassword(form.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid email and a password with 12 characters minimum and 72 UTF-8 bytes maximum.");
        }
        return accounts.register(email, form.password());
    }
}
