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

import com.vibegraph.auth.domain.SecurityEvent;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.repository.projection.AdminSecurityAlertRow;

@Repository
public class JdbcSecurityEventRepository implements SecurityEventRepository {

    private static final RowMapper<SecurityEvent> ROW_MAPPER = JdbcSecurityEventRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSecurityEventRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts idempotently on the primary key, so replaying a partially written telemetry batch
     * neither duplicates rows nor fails on a duplicate key.
     */
    private static final String INSERT_SQL = """
            INSERT INTO security_events
                (id, event_type, severity, subject_user_id, api_key_ref, source, description)
            VALUES (:id, :eventType, :severity, :subjectUserId, :apiKeyRef, :source, :description)
            ON CONFLICT (id) DO NOTHING
            """;

    @Override
    public SecurityEvent save(SecurityEvent event) {
        UUID id = event.getId() == null ? UUID.randomUUID() : event.getId();
        List<SecurityEvent> inserted = jdbc.query(
                INSERT_SQL + " RETURNING *", parameters(event).addValue("id", id), ROW_MAPPER);
        if (!inserted.isEmpty()) {
            return inserted.get(0);
        }
        // The id already existed: return the stored row rather than null.
        return jdbc.query("SELECT * FROM security_events WHERE id = :id",
                        Map.of("id", id), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseGet(() -> event.getId() != null ? event : withId(event, id));
    }

    @Override
    public void saveAll(List<SecurityEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = events.stream()
                .map(event -> parameters(event).addValue(
                        "id", event.getId() == null ? UUID.randomUUID() : event.getId()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_SQL, batch);
    }

    private static SecurityEvent withId(SecurityEvent event, UUID id) {
        return SecurityEvent.builder()
                .id(id)
                .eventType(event.getEventType())
                .severity(event.getSeverity())
                .subjectUserId(event.getSubjectUserId())
                .apiKeyRef(event.getApiKeyRef())
                .source(event.getSource())
                .description(event.getDescription())
                .createdAt(event.getCreatedAt())
                .build();
    }

    @Override
    public List<SecurityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable) {
        return jdbc.query(
                "SELECT * FROM security_events ORDER BY created_at DESC LIMIT :limit",
                Map.of("limit", pageable.getPageSize()), ROW_MAPPER);
    }

    @Override
    public List<AdminSecurityAlertRow> summarizeSince(Instant since) {
        return jdbc.query("""
                SELECT event_type, severity, count(*) AS value, max(created_at) AS created_at
                FROM security_events
                WHERE created_at >= :since
                GROUP BY event_type, severity
                ORDER BY max(created_at) DESC
                """, Map.of("since", JdbcParameters.instant(since)), (rs, rowNum) -> new SecurityAlertRow(
                rs.getString("event_type"), rs.getString("severity"), rs.getLong("value"),
                JdbcFeedbackReportRepository.instant(rs, "created_at")));
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM security_events WHERE created_at < :cutoff",
                Map.of("cutoff", JdbcParameters.instant(cutoff)));
    }

    private MapSqlParameterSource parameters(SecurityEvent event) {
        return new MapSqlParameterSource()
                .addValue("eventType", event.getEventType())
                .addValue("severity", event.getSeverity())
                .addValue("subjectUserId", event.getSubjectUserId())
                .addValue("apiKeyRef", event.getApiKeyRef())
                .addValue("source", event.getSource())
                .addValue("description", event.getDescription());
    }

    private static SecurityEvent map(ResultSet rs, int rowNum) throws SQLException {
        return SecurityEvent.builder()
                .id(rs.getObject("id", UUID.class))
                .eventType(rs.getString("event_type"))
                .severity(rs.getString("severity"))
                .subjectUserId(rs.getObject("subject_user_id", UUID.class))
                .apiKeyRef(rs.getString("api_key_ref"))
                .source(rs.getString("source"))
                .description(rs.getString("description"))
                .createdAt(JdbcFeedbackReportRepository.instant(rs, "created_at"))
                .build();
    }

    private record SecurityAlertRow(
            String type, String severity, Long value, Instant createdAt) implements AdminSecurityAlertRow {
        @Override public String getType() { return type; }
        @Override public String getSeverity() { return severity; }
        @Override public Long getValue() { return value; }
        @Override public Instant getCreatedAt() { return createdAt; }
    }
}
