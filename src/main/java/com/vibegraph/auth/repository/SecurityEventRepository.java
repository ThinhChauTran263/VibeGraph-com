package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.auth.repository.projection.AdminSecurityAlertRow;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    List<SecurityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT event_type AS "type",
                   severity AS "severity",
                   count(*) AS "value",
                   max(created_at) AS "createdAt"
            FROM security_events
            WHERE created_at >= :since
            GROUP BY event_type, severity
            ORDER BY max(created_at) DESC
            """, nativeQuery = true)
    List<AdminSecurityAlertRow> summarizeSince(
            @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
