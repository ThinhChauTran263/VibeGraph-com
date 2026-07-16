package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:outcome IS NULL OR a.outcome = :outcome)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
              AND (:targetUserId IS NULL OR a.targetUserId = :targetUserId)
              AND (:fromTime IS NULL OR a.createdAt >= :fromTime)
              AND (:toTime IS NULL OR a.createdAt <= :toTime)
            """)
    Page<AuditLog> findAllWithFilters(
            @Param("action") String action,
            @Param("outcome") String outcome,
            @Param("actorUserId") UUID actorUserId,
            @Param("targetUserId") UUID targetUserId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
