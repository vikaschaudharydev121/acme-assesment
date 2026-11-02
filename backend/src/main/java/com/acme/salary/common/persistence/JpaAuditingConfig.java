package com.acme.salary.common.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /** Actor recorded for changes made outside a user request (seeding, bootstrapping). */
    public static final String SYSTEM_ACTOR = "system";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of(SYSTEM_ACTOR);
            }
            return Optional.of(auth.getName());
        };
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        // Truncate to the precision stored in SQLite so API responses match later reads.
        return () -> Optional.of(clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }
}
