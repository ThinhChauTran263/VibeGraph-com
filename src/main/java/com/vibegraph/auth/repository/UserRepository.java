package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.projection.AdminSeriesRow;

import jakarta.persistence.LockModeType;

/**
 * Control-plane user access. Email matching is case-insensitive to honour the
 * {@code uq_users_email_lower} functional index (lower(email) uniqueness).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

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

    @Query(value = """
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS label,
                   count(*) AS value,
                   'month' AS period
            FROM users
            WHERE created_at IS NOT NULL
            GROUP BY date_trunc('month', created_at)
            ORDER BY date_trunc('month', created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> countGrowthByMonth();

    @Query(value = """
            SELECT concat(extract(year from created_at)::int, '-Q', extract(quarter from created_at)::int) AS label,
                   count(*) AS value,
                   'quarter' AS period
            FROM users
            WHERE created_at IS NOT NULL
            GROUP BY extract(year from created_at), extract(quarter from created_at)
            ORDER BY extract(year from created_at), extract(quarter from created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> countGrowthByQuarter();

    @Query(value = """
            SELECT extract(year from created_at)::int::text AS label,
                   count(*) AS value,
                   'year' AS period
            FROM users
            WHERE created_at IS NOT NULL
            GROUP BY extract(year from created_at)
            ORDER BY extract(year from created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> countGrowthByYear();
}
