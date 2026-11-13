package com.maramigo.accounts;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

/** @author Maram Abbas */
@Service
public class AccountService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    @org.springframework.beans.factory.annotation.Value("${maramigo.verification-required:true}")
    private boolean verificationRequired;
    public AccountService(UserRepository users, PasswordEncoder passwords) {
        this.users = users; this.passwords = passwords;
    }
    public record UserView(Long id, String email, String role) {
        public static UserView of(UserAccount u) { return new UserView(u.getId(), u.getEmail(), u.getRole()); }
    }
    public record Principal(Long id, String email, String password, String role, boolean enabled, long authVersion) implements UserDetails {
        @Override public boolean isEnabled() { return enabled; }
        @Override public String getUsername() { return email; }
        @Override public String getPassword() { return password; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        }
        public UserView view() { return new UserView(id, email, role); }
        @Override public String toString() { return "Principal[id=" + id + ", role=" + role + "]"; }
    }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public static boolean validPassword(String password) {
        return password != null && password.codePointCount(0, password.length()) >= 12
            && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public UserView register(String email, String password) {
        return UserView.of(users.saveAndFlush(new UserAccount(normalize(email), passwords.encode(password), "CUSTOMER")));
    }
    @Override public UserDetails loadUserByUsername(String email) {
        var account = users.findByEmail(normalize(email))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return new Principal(account.getId(), account.getEmail(), account.getPasswordHash(), account.getRole(), account.isEnabled() && (!verificationRequired || account.isEmailVerified()), account.getAuthVersion());
    }
}
