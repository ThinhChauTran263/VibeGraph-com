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
            SELECT re.user_id AS userId,
                   max(u.display_name) AS userDisplayName,
                   max(u.email) AS userEmail,
                   NULL::varchar AS ipAddress,
                   re.api_key_ref AS apiKeyRef,
                   max(date_trunc('minute', re.occurred_at)) AS minuteBucket,
                   count(*) AS requestCount
            FROM request_events re
            LEFT JOIN users u ON u.id = re.user_id
            WHERE re.occurred_at >= :since AND (re.user_id IS NOT NULL OR re.api_key_ref IS NOT NULL)
            GROUP BY re.user_id, re.api_key_ref
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<RequestAggregateProjection> topUsers(@Param("since") Instant since, Pageable pageable);

    @Query(value = """
            SELECT NULL::uuid AS userId,
                   NULL::varchar AS userDisplayName,
                   NULL::varchar AS userEmail,
                   re.ip_address AS ipAddress,
                   NULL::varchar AS apiKeyRef,
                   max(date_trunc('minute', re.occurred_at)) AS minuteBucket,
                   count(*) AS requestCount
            FROM request_events re
            WHERE re.occurred_at >= :since AND re.ip_address IS NOT NULL
            GROUP BY re.ip_address
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<RequestAggregateProjection> topIps(@Param("since") Instant since, Pageable pageable);

    @Query(value = """
            SELECT re.ip_address AS ipAddress,
                   max(date_trunc('minute', re.occurred_at)) AS minuteBucket,
                   count(*) AS totalRequests,
                   count(DISTINCT re.user_id) AS uniqueUsers,
                   count(DISTINCT re.api_key_ref) AS uniqueApiKeys
            FROM request_events re
            WHERE re.occurred_at >= :since AND re.ip_address IS NOT NULL
            GROUP BY re.ip_address
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<NetworkAggregateProjection> suspiciousNetworks(@Param("since") Instant since, Pageable pageable);

    @Query(value = """
            SELECT re.ip_address AS ipAddress,
                   re.user_id AS userId,
                   max(u.display_name) AS userDisplayName,
                   max(u.email) AS userEmail,
                   re.api_key_ref AS apiKeyRef,
                   count(*) AS requests
            FROM request_events re
            LEFT JOIN users u ON u.id = re.user_id
            WHERE re.occurred_at >= :since AND re.ip_address IN (:ipAddresses)
            GROUP BY re.ip_address, re.user_id, re.api_key_ref
            ORDER BY re.ip_address, count(*) DESC
            """, nativeQuery = true)
    List<NetworkBreakdownProjection> networkBreakdowns(
            @Param("since") Instant since,
            @Param("ipAddresses") List<String> ipAddresses);
}
