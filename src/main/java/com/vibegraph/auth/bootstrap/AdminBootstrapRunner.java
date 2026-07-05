package com.vibegraph.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.vibegraph.auth.config.BootstrapProperties;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Explicit, opt-in admin bootstrap. The bean exists ONLY when
 * {@code vibegraph.auth.bootstrap.enabled=true} ({@link ConditionalOnProperty}); normal startup
 * (default {@code false}) never instantiates it and therefore never creates an admin.
 *
 * <p>When enabled it:
 * <ul>
 *   <li><b>Fails fast</b> if the required credentials are missing (admin email plus a password or
 *       password hash), so a misconfigured bootstrap aborts startup rather than proceeding.</li>
 *   <li>Is <b>idempotent</b>: if an account with the admin email already exists it is a no-op;
 *       re-running creates no duplicate.</li>
 * </ul>
 *
 * <p>Scope note: this foundation creates only the admin <em>account</em>. Assigning ownership of
 * pre-auth (legacy) Neo4j {@code :Project} nodes is intentionally deferred — it requires reading the
 * graph via graph services, which this slice does not touch. That migration step will be added
 * separately once it can be isolated from graph-service edits.
 */
@Component
@ConditionalOnProperty(prefix = "vibegraph.auth.bootstrap", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BootstrapProperties.class)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String email = trimToNull(properties.getAdminEmail());
        String rawPassword = trimToNull(properties.getAdminPassword());
        String passwordHash = trimToNull(properties.getAdminPasswordHash());

        if (email == null || (rawPassword == null && passwordHash == null)) {
            // Fail fast: bootstrap was explicitly requested but is under-configured.
            throw new IllegalStateException(
                    "Admin bootstrap is enabled but misconfigured: require ADMIN_EMAIL and one of "
                            + "ADMIN_PASSWORD / ADMIN_PASSWORD_HASH.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Admin bootstrap: admin account already exists for {} — no-op (idempotent).", email);
            return;
        }

        String hash = passwordHash != null ? passwordHash : passwordEncoder.encode(rawPassword);
        User admin = User.builder()
                .email(email)
                .passwordHash(hash)
                .displayName("Administrator")
                .role(Role.ADMIN)
                .emailVerified(true)
                .build();
        userRepository.save(admin);
        log.info("Admin bootstrap: created admin account for {}.", email);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
