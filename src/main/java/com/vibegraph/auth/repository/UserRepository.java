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

    @Query("SELECT u FROM User u LEFT JOIN UserAccountSettings s ON u.id = s.userId WHERE " +
           "(:search IS NULL OR :search = '' OR lower(u.email) LIKE lower(concat('%', :search, '%')) OR lower(u.displayName) LIKE lower(concat('%', :search, '%'))) AND " +
           "(:plan IS NULL OR :plan = '' OR (s IS NOT NULL AND lower(s.plan.code) = lower(:plan))) AND " +
           "(:status IS NULL OR :status = '' OR " +
           " (lower(:status) = 'blocked' AND s IS NOT NULL AND s.blockedAt IS NOT NULL) OR " +
           " (lower(:status) = 'deactivated' AND u.deactivated = true) OR " +
           " (lower(:status) = 'active' AND u.deactivated = false AND (s IS NULL OR s.blockedAt IS NULL)))")
    org.springframework.data.domain.Page<User> findAllWithFilters(
            @Param("search") String search,
            @Param("status") String status,
            @Param("plan") String plan,
            org.springframework.data.domain.Pageable pageable);

    long countByDeactivated(boolean deactivated);
}
