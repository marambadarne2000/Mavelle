package com.maramigo.accounts;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance limiter for the demo's login endpoint. @author Maram Abbas */
public class LoginRateLimiter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int limit;
    public LoginRateLimiter(Clock clock, int limit) { this.clock = clock; this.limit = limit; }
    synchronized long retryAfter(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window previous = windows.get(address);
        if (previous == null && windows.size() >= 1024) return 60;
        if (previous != null && previous.count() >= limit) return Math.max(1, (60_000 - (now - previous.start()) + 999) / 1000);
        windows.put(address, previous == null ? new Window(now, 1) : new Window(previous.start(), previous.count()+1));
        return 0;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !java.util.Set.of("/api/auth/login", "/api/auth/forgot-password", "/api/auth/verification-request", "/api/auth/reset-password", "/api/auth/verify-email", "/api/auth/confirm-email-change", "/api/account/password", "/api/account/email", "/api/account/privacy", "/api/auth/mfa/verify", "/api/auth/mfa/setup", "/api/auth/mfa/confirm", "/api/auth/mfa/disable", "/api/auth/mfa/recovery-codes").contains(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        long seconds = retryAfter(request.getRemoteAddr());
        if (seconds > 0) {
            response.setStatus(429); response.setHeader("Retry-After", Long.toString(seconds));
            response.setContentType("application/json"); response.getWriter().write("{\"detail\":\"Too many account attempts. Please try again later.\"}");
        } else { chain.doFilter(request, response); }
    }
}
