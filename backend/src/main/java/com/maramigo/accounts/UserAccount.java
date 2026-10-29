package com.maramigo.accounts;

import jakarta.persistence.*;

/** @author Maram Abbas */
@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String passwordHash;
    private String role;
    private boolean enabled = true;
    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, String role) {
        this.email = email; this.passwordHash = passwordHash; this.role = role;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
