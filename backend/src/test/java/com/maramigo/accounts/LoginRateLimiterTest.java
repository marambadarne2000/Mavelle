package com.maramigo.accounts;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

/** @author Maram Abbas */
class LoginRateLimiterTest {
    @Test void throttledHttpRequestsReturnRetryAfterInsteadOfReachingAuthentication() throws Exception {
        for(String path:java.util.List.of("/api/auth/register","/api/auth/business-register","/api/auth/staff-register","/api/auth/login","/api/auth/forgot-password","/api/auth/verification-request","/api/auth/reset-password","/api/auth/verify-email")){
        var limiter = new LoginRateLimiter(Clock.systemUTC(), 1);
        for (int i=0; i<2; i++) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
            request.setServletPath(path); request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            final boolean[] called = {false};
            limiter.doFilter(request, response, (req, res) -> called[0] = true);
            if (i == 0) assertThat(called[0]).isTrue();
            else { assertThat(called[0]).isFalse(); assertThat(response.getStatus()).isEqualTo(429); assertThat(response.getHeader("Retry-After")).isNotBlank(); }
        }
    }
    }
    @Test void limitsArePerAddressAndExpireAtTheWindowBoundary() {
        AtomicLong time = new AtomicLong(1_000_000);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(time.get()); }
        };
        var limiter = new LoginRateLimiter(clock, 2);
        assertThat(limiter.retryAfter("one")).isZero();
        assertThat(limiter.retryAfter("one")).isZero();
        assertThat(limiter.retryAfter("one")).isEqualTo(60);
        assertThat(limiter.retryAfter("two")).isZero();
        time.addAndGet(60_000);
        assertThat(limiter.retryAfter("one")).isZero();
    }
    @Test void utf8ByteLimitPreventsBcryptTruncation() {
        assertThat(AccountService.validPassword("a".repeat(72))).isTrue();
        assertThat(AccountService.validPassword("a".repeat(73))).isFalse();
        assertThat(AccountService.validPassword("א".repeat(37))).isFalse();
        assertThat(AccountService.validPassword("short")).isFalse();
    }
}
