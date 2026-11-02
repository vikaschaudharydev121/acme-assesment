package com.acme.salary.auth;

import com.acme.salary.security.SecurityProperties;
import com.acme.salary.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T09:00:00Z"));
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new SecurityProperties.Cookie(true),
                new SecurityProperties.Login(3, LOCKOUT),
                List.of());
        service = new LoginAttemptService(properties, clock);
    }

    @Test
    void locksAfterMaxConsecutiveFailures() {
        service.recordFailure("alice");
        service.recordFailure("alice");
        assertThat(service.isLocked("alice")).isFalse();

        service.recordFailure("alice");

        assertThat(service.isLocked("alice")).isTrue();
    }

    @Test
    void lockExpiresAfterLockoutDuration() {
        failTimes("alice", 3);

        clock.advance(LOCKOUT.minusSeconds(1));
        assertThat(service.isLocked("alice")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isLocked("alice")).isFalse();
    }

    @Test
    void failuresAfterAnExpiredLockStartCountingAgain() {
        failTimes("alice", 3);
        clock.advance(LOCKOUT);

        service.recordFailure("alice");

        assertThat(service.isLocked("alice")).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        failTimes("alice", 2);

        service.recordSuccess("alice");
        failTimes("alice", 2);

        assertThat(service.isLocked("alice")).isFalse();
    }

    @Test
    void usernamesAreCaseAndWhitespaceInsensitive() {
        service.recordFailure("Alice");
        service.recordFailure(" alice ");
        service.recordFailure("ALICE");

        assertThat(service.isLocked("alice")).isTrue();
    }

    @Test
    void lockingOneUserDoesNotAffectAnother() {
        failTimes("alice", 3);

        assertThat(service.isLocked("bob")).isFalse();
    }

    private void failTimes(String username, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username);
        }
    }
}
