package com.acme.salary.auth;

import com.acme.salary.security.SecurityProperties;
import com.acme.salary.security.SecurityProperties.BootstrapUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the configured HR accounts on first start. Passwords come from environment variables and
 * only their BCrypt hash is stored. Existing accounts are never modified.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class UserBootstrapper implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(UserBootstrapper.class);

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    UserBootstrapper(AppUserRepository repository, PasswordEncoder passwordEncoder,
                     SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (BootstrapUser user : properties.bootstrapUsers()) {
            if (repository.existsByUsernameIgnoreCase(user.username())) {
                continue;
            }
            if (user.password() == null || user.password().isBlank()) {
                log.warn("No password configured for bootstrap user '{}'; account not created", user.username());
                continue;
            }
            if (user.password().length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("Bootstrap password for '" + user.username()
                        + "' must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            repository.save(new AppUser(user.username(), passwordEncoder.encode(user.password()),
                    user.role(), clock.instant()));
            log.info("Created bootstrap user '{}' with role {}", user.username(), user.role());
        }
    }
}
