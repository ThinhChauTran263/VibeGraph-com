package com.vibegraph.common.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.abuse.NetworkAggregateProjection;
import com.vibegraph.abuse.NetworkBreakdownProjection;
import com.vibegraph.abuse.RequestAggregateProjection;
import com.vibegraph.abuse.RequestEvent;
import com.vibegraph.abuse.RequestEventRepository;

@Repository
public class JdbcRequestEventRepository implements RequestEventRepository {

    private static final RowMapper<RequestEvent> ROW_MAPPER = JdbcRequestEventRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRequestEventRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts idempotently on the primary key, so replaying a partially written telemetry batch
     * neither duplicates rows nor fails on a duplicate key.
     */
    private static final String INSERT_SQL = """
            INSERT INTO request_events
                (id, user_id, api_key_ref, ip_address, route, http_method, status, event_type, occurred_at)
            VALUES
                (:id, :userId, :apiKeyRef, :ipAddress, :route, :method, :status, :eventType, :occurredAt)
            ON CONFLICT (id) DO NOTHING
            """;

    @Override
    public RequestEvent save(RequestEvent event) {
        RequestEvent identified = identified(event);
        List<RequestEvent> inserted = jdbc.query(
                INSERT_SQL + " RETURNING *", parameters(identified), ROW_MAPPER);
        if (!inserted.isEmpty()) {
            return inserted.get(0);
        }
        // The id already existed: return the stored row rather than null.
        return jdbc.query("SELECT * FROM request_events WHERE id = :id",
                        Map.of("id", identified.getId()), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElse(identified);
    }

    @Override
    public void saveAll(List<RequestEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = events.stream()
                .map(this::identified)
                .map(this::parameters)
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_SQL, batch);
    }

    @Override
    public List<RequestEvent> findAllByOrderByOccurredAtDesc(Pageable pageable) {
        return jdbc.query(
                "SELECT * FROM request_events ORDER BY occurred_at DESC LIMIT :limit",
                Map.of("limit", pageable.getPageSize()), ROW_MAPPER);
    }

    @Override
    public List<RequestAggregateProjection> topUsers(Instant since, Pageable pageable) {
        return jdbc.query("""
                SELECT user_id, api_key_ref, max(date_trunc('minute', occurred_at)) AS minute_bucket,
                       count(*) AS request_count
                FROM request_events
                WHERE occurred_at >= :since AND (user_id IS NOT NULL OR api_key_ref IS NOT NULL)
                GROUP BY user_id, api_key_ref
                ORDER BY count(*) DESC
                LIMIT :limit
                """, Map.of("since", JdbcParameters.instant(since), "limit", pageable.getPageSize()),
                (rs, rowNum) -> new RequestAggregateRow(
                        rs.getObject("user_id", UUID.class), null, null, null,
                        rs.getString("api_key_ref"),
                        JdbcFeedbackReportRepository.instant(rs, "minute_bucket"),
                        rs.getLong("request_count")));
    }

    @Override
    public List<RequestAggregateProjection> topIps(Instant since, Pageable pageable) {
        return jdbc.query("""
                SELECT ip_address, max(date_trunc('minute', occurred_at)) AS minute_bucket,
                       count(*) AS request_count
                FROM request_events
                WHERE occurred_at >= :since
                GROUP BY ip_address
                ORDER BY count(*) DESC
                LIMIT :limit
                """, Map.of("since", JdbcParameters.instant(since), "limit", pageable.getPageSize()),
                (rs, rowNum) -> new RequestAggregateRow(
                        null, null, null, rs.getString("ip_address"), null,
                        JdbcFeedbackReportRepository.instant(rs, "minute_bucket"),
                        rs.getLong("request_count")));
    }

    @Override
    public List<NetworkAggregateProjection> suspiciousNetworks(Instant since, Pageable pageable) {
        return jdbc.query("""
                SELECT ip_address, max(date_trunc('minute', occurred_at)) AS minute_bucket,
                       count(*) AS total_requests, count(DISTINCT user_id) AS unique_users,
                       count(DISTINCT api_key_ref) AS unique_api_keys
                FROM request_events
                WHERE occurred_at >= :since
                  AND ip_address <> 'unknown'
                  AND route <> '/actuator/health'
                  AND ip_address !~* '^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|169\\.254\\.|100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.|::1$|0:0:0:0:0:0:0:1$|fc[0-9a-f]{2}:|fd[0-9a-f]{2}:|fe80:)'
                GROUP BY ip_address
                ORDER BY count(*) DESC
                LIMIT :limit
                """, Map.of("since", JdbcParameters.instant(since), "limit", pageable.getPageSize()),
                (rs, rowNum) -> new NetworkAggregateRow(
                        rs.getString("ip_address"),
                        JdbcFeedbackReportRepository.instant(rs, "minute_bucket"),
                        rs.getLong("total_requests"), rs.getLong("unique_users"),
                        rs.getLong("unique_api_keys")));
    }

    @Override
    public List<NetworkBreakdownProjection> networkBreakdowns(Instant since, List<String> ipAddresses) {
        if (ipAddresses.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT ip_address, user_id, api_key_ref, count(*) AS requests
                FROM request_events
                WHERE occurred_at >= :since
                  AND ip_address IN (:ipAddresses)
                  AND route <> '/actuator/health'
                GROUP BY ip_address, user_id, api_key_ref
                ORDER BY ip_address, count(*) DESC
                """, Map.of("since", JdbcParameters.instant(since), "ipAddresses", ipAddresses),
                (rs, rowNum) -> new NetworkBreakdownRow(
                        rs.getString("ip_address"), rs.getObject("user_id", UUID.class),
                        null, null, rs.getString("api_key_ref"), rs.getLong("requests")));
    }

    @Override
    public int deleteOccurredBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM request_events WHERE occurred_at < :cutoff",
                Map.of("cutoff", JdbcParameters.instant(cutoff)));
    }

    private RequestEvent identified(RequestEvent event) {
        if (event.getId() != null) {
            return event;
        }
        return RequestEvent.builder()
                .id(UUID.randomUUID())
                .userId(event.getUserId())
                .apiKeyRef(event.getApiKeyRef())
                .ipAddress(event.getIpAddress())
                .route(event.getRoute())
                .method(event.getMethod())
                .status(event.getStatus())
                .eventType(event.getEventType())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    private MapSqlParameterSource parameters(RequestEvent event) {
        return new MapSqlParameterSource()
                .addValue("id", event.getId())
                .addValue("userId", event.getUserId())
                .addValue("apiKeyRef", event.getApiKeyRef())
                .addValue("ipAddress", event.getIpAddress())
                .addValue("route", event.getRoute())
                .addValue("method", event.getMethod())
                .addValue("status", event.getStatus())
                .addValue("eventType", event.getEventType())
                .addValue("occurredAt", JdbcParameters.instant(event.getOccurredAt()));
    }

    private static RequestEvent map(ResultSet rs, int rowNum) throws SQLException {
        return RequestEvent.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .apiKeyRef(rs.getString("api_key_ref"))
                .ipAddress(rs.getString("ip_address"))
                .route(rs.getString("route"))
                .method(rs.getString("http_method"))
                .status(rs.getInt("status"))
                .eventType(rs.getString("event_type"))
                .occurredAt(JdbcFeedbackReportRepository.instant(rs, "occurred_at"))
                .build();
    }

    private record RequestAggregateRow(
            UUID userId, String userDisplayName, String userEmail, String ipAddress,
            String apiKeyRef, Instant minuteBucket, long requestCount) implements RequestAggregateProjection {
        @Override public UUID getUserId() { return userId; }
        @Override public String getUserDisplayName() { return userDisplayName; }
        @Override public String getUserEmail() { return userEmail; }
        @Override public String getIpAddress() { return ipAddress; }
        @Override public String getApiKeyRef() { return apiKeyRef; }
        @Override public Instant getMinuteBucket() { return minuteBucket; }
        @Override public long getRequestCount() { return requestCount; }
    }

    private record NetworkAggregateRow(
            String ipAddress, Instant minuteBucket, long totalRequests,
            long uniqueUsers, long uniqueApiKeys) implements NetworkAggregateProjection {
        @Override public String getIpAddress() { return ipAddress; }
        @Override public Instant getMinuteBucket() { return minuteBucket; }
        @Override public long getTotalRequests() { return totalRequests; }
        @Override public long getUniqueUsers() { return uniqueUsers; }
        @Override public long getUniqueApiKeys() { return uniqueApiKeys; }
    }

    private record NetworkBreakdownRow(
            String ipAddress, UUID userId, String userDisplayName, String userEmail,
            String apiKeyRef, long requests) implements NetworkBreakdownProjection {
        @Override public String getIpAddress() { return ipAddress; }
        @Override public UUID getUserId() { return userId; }
        @Override public String getUserDisplayName() { return userDisplayName; }
        @Override public String getUserEmail() { return userEmail; }
        @Override public String getApiKeyRef() { return apiKeyRef; }
        @Override public long getRequests() { return requests; }
    }
}
