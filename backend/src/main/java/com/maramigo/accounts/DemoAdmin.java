package com.maramigo.accounts;

import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit opt-in only; never promotes or resets an existing account. @author Maram Abbas */
@Configuration
@Profile("demo")
public class DemoAdmin {
    @Bean ApplicationRunner initializeDemoAdministrator(UserRepository users, PasswordEncoder passwords,
            @Value("${DEMO_ADMIN_EMAIL:}") String email, @Value("${DEMO_ADMIN_PASSWORD:}") String password) {
        return arguments -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || !AccountService.validPassword(password)) throw new IllegalArgumentException("Demo admin configuration is incomplete or password is invalid.");
            String normalized = AccountService.normalize(email);
            if (users.findByEmail(normalized).isEmpty() && !users.existsByRole("ADMIN")) users.saveAndFlush(new UserAccount(normalized, passwords.encode(password), "ADMIN"));
        };
    }
}
