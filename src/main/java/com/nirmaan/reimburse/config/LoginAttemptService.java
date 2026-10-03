package com.nirmaan.reimburse.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login rate limiter: after N failures for an email, further attempts are blocked for M minutes.
 * Keyed by email so an attacker rotating IPs is still limited. Single-instance deployment assumed.
 * Fed by the form-login success/failure handlers in {@link SecurityConfig}.
 */
@Component
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptService(AppProperties props, Clock clock) {
        AppProperties.Security s = props.security();
        this.maxAttempts = s == null ? 5 : s.loginMaxAttempts();
        this.lockDuration = Duration.ofMinutes(s == null ? 15 : s.loginLockMinutes());
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        if (username == null) {
            return false;
        }
        Attempts a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now(clock));
    }

    public void recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attempts.compute(key(username), (k, a) -> {
            int failures = (a == null || (a.lockedUntil() != null && !a.lockedUntil().isAfter(Instant.now(clock))))
                    ? 1 : a.failures() + 1;
            Instant lock = failures >= maxAttempts ? Instant.now(clock).plus(lockDuration) : null;
            return new Attempts(failures, lock);
        });
    }

    public void recordSuccess(String username) {
        if (username != null) {
            attempts.remove(key(username));
        }
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
