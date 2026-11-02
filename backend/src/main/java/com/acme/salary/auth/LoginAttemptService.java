package com.acme.salary.auth;

import com.acme.salary.security.SecurityProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: after N consecutive failures a username is locked for a cooldown period.
 * Keyed by the submitted username (existing or not), so lockout behaviour reveals nothing about
 * which accounts exist. In-memory is sufficient for a single instance; a multi-instance deployment
 * would move this state to Redis.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.maxFailures = properties.login().maxFailedAttempts();
        this.lockout = properties.login().lockoutDuration();
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempts current = attempts.get(key(username));
        return current != null && current.lockedUntil() != null && clock.instant().isBefore(current.lockedUntil());
    }

    public void recordFailure(String username) {
        evictExpiredIfFull();
        Instant now = clock.instant();
        attempts.compute(key(username), (k, current) -> {
            boolean expiredLock = current != null && current.lockedUntil() != null && !now.isBefore(current.lockedUntil());
            int failures = (current == null || expiredLock ? 0 : current.failures()) + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockout) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** Bounds memory if an attacker sprays random usernames. */
    private void evictExpiredIfFull() {
        if (attempts.size() < MAX_TRACKED_USERNAMES) {
            return;
        }
        Instant now = clock.instant();
        attempts.values().removeIf(a -> a.lockedUntil() == null || !now.isBefore(a.lockedUntil()));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
