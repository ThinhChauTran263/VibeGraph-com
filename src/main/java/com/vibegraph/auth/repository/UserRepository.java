package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.User;

/**
 * Control-plane user access. Email matching is case-insensitive to honour the
 * {@code uq_users_email_lower} functional index (lower(email) uniqueness).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT (count(u) > 0) FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    org.springframework.data.domain.Page<User> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String email, String displayName, org.springframework.data.domain.Pageable pageable);

    long countByDeactivated(boolean deactivated);
}
