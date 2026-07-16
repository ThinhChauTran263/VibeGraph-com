package com.vibegraph.abuse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestEventRepository extends JpaRepository<RequestEvent, UUID> {

    List<RequestEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Query(value = """
            SELECT user_id AS userId, max(ip_address) AS ipAddress,
                   max(api_key_ref) AS apiKeyRef,
                   date_trunc('minute', occurred_at) AS minuteBucket,
                   count(*) AS requestCount
            FROM request_events
            WHERE user_id IS NOT NULL AND occurred_at >= :since
            GROUP BY user_id, date_trunc('minute', occurred_at)
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<RequestAggregateProjection> topUsers(@Param("since") Instant since, Pageable pageable);

    @Query(value = """
            SELECT max(user_id::text)::uuid AS userId, ip_address AS ipAddress,
                   max(api_key_ref) AS apiKeyRef,
                   date_trunc('minute', occurred_at) AS minuteBucket,
                   count(*) AS requestCount
            FROM request_events
            WHERE occurred_at >= :since
            GROUP BY ip_address, date_trunc('minute', occurred_at)
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<RequestAggregateProjection> topIps(@Param("since") Instant since, Pageable pageable);
}
