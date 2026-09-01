package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.entity.RefreshSession;

import jakarta.persistence.LockModeType;

/** Persistence operations for hashed, rotating refresh sessions. */
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RefreshSession s WHERE s.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshSession s
            SET s.revokedAt = :revokedAt, s.revokeReason = :reason
            WHERE s.familyId = :familyId AND s.revokedAt IS NULL
            """)
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshSession s
            SET s.revokedAt = :revokedAt, s.revokeReason = :reason
            WHERE s.userId = :userId AND s.revokedAt IS NULL
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM RefreshSession s
            WHERE s.id = :sessionId AND s.userId = :userId
              AND s.revokedAt IS NULL AND s.expiresAt > :now
            """)
    boolean isActive(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    /**
     * Delete sessions that expired before {@code cutoff}.
     *
     * <p>Rotation only ever inserts, so this is the sole thing keeping the table bounded. Matching
     * on {@code expiresAt} alone is safe: a revoked row still carries its original expiry, and no
     * row is usable once that has passed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
