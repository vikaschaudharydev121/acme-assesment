package com.acme.salary.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Validated at startup: the application refuses to boot with a missing or short JWT secret rather
 * than running with weak security.
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull Login login,
        List<@Valid BootstrapUser> bootstrapUsers) {

    public SecurityProperties {
        bootstrapUsers = bootstrapUsers == null ? List.of() : List.copyOf(bootstrapUsers);
    }

    public record Jwt(
            @NotBlank(message = "APP_JWT_SECRET must be set")
            @Size(min = 32, message = "APP_JWT_SECRET must be at least 32 characters")
            String secret,
            @NotNull Duration ttl) {
    }

    public record Cookie(boolean secure) {
    }

    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockoutDuration) {
    }

    /** A blank password means "do not create this user", so production must opt in explicitly. */
    public record BootstrapUser(@NotBlank String username, String password, @NotNull Role role) {
    }
}
