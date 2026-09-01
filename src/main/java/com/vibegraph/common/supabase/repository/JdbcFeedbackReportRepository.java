package com.vibegraph.common.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.FeedbackCategory;
import com.vibegraph.auth.domain.entity.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.repository.FeedbackReportRepository;

@Repository
public class JdbcFeedbackReportRepository implements FeedbackReportRepository {

    private static final RowMapper<FeedbackReport> ROW_MAPPER = JdbcFeedbackReportRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFeedbackReportRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FeedbackReport save(FeedbackReport report) {
        return report.getId() == null ? insert(report) : update(report);
    }

    @Override
    public Optional<FeedbackReport> findById(UUID id) {
        return queryOne("SELECT * FROM feedback_reports WHERE id = :id", Map.of("id", id));
    }

    @Override
    public boolean existsById(UUID id) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM feedback_reports WHERE id = :id", Map.of("id", id), Long.class);
        return count != null && count > 0;
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM feedback_reports", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<FeedbackReport> findByUserId(UUID userId) {
        return jdbc.query(
                "SELECT * FROM feedback_reports WHERE user_id = :userId ORDER BY created_at DESC",
                Map.of("userId", userId), ROW_MAPPER);
    }

    @Override
    public Optional<FeedbackReport> findByIdAndUserId(UUID id, UUID userId) {
        return queryOne(
                "SELECT * FROM feedback_reports WHERE id = :id AND user_id = :userId",
                Map.of("id", id, "userId", userId));
    }

    @Override
    public void deleteByDeleteAfterLessThanEqual(Instant now) {
        jdbc.update("DELETE FROM feedback_reports WHERE delete_after <= :now",
                Map.of("now", JdbcParameters.instant(now)));
    }

    @Override
    public List<FeedbackReport> findByDeleteAfterLessThanEqual(Instant now) {
        return jdbc.query(
                "SELECT * FROM feedback_reports WHERE delete_after <= :now ORDER BY delete_after",
                Map.of("now", JdbcParameters.instant(now)), ROW_MAPPER);
    }

    @Override
    public long countByStatus(FeedbackReportStatus status) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM feedback_reports WHERE status = :status",
                Map.of("status", status.name()), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<FeedbackReport> findAllByOrderByCreatedAtDesc() {
        return jdbc.query("SELECT * FROM feedback_reports ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public Page<FeedbackReport> findAllWithFilters(
            FeedbackReportStatus status, String query, Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        if (status != null) {
            where.append(" AND status = :status");
            parameters.addValue("status", status.name());
        }
        if (!normalized.isEmpty()) {
            where.append(" AND lower(title) LIKE lower(concat('%', :query, '%'))");
            parameters.addValue("query", normalized);
        }
        String whereClause = where.toString();
        List<FeedbackReport> content = jdbc.query(
                "SELECT * FROM feedback_reports" + whereClause
                        + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
                parameters, ROW_MAPPER);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM feedback_reports" + whereClause, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private FeedbackReport insert(FeedbackReport report) {
        MapSqlParameterSource parameters = parameters(report).addValue("id", UUID.randomUUID());
        return jdbc.queryForObject("""
                INSERT INTO feedback_reports (id, user_id, status, category, title, closed_at, delete_after)
                VALUES (:id, :userId, :status, :category, :title, :closedAt, :deleteAfter)
                RETURNING *
                """, parameters, ROW_MAPPER);
    }

    private FeedbackReport update(FeedbackReport report) {
        return jdbc.queryForObject("""
                UPDATE feedback_reports
                SET user_id = :userId, status = :status, category = :category, title = :title,
                    closed_at = :closedAt, delete_after = :deleteAfter
                WHERE id = :id
                RETURNING *
                """, parameters(report).addValue("id", report.getId()), ROW_MAPPER);
    }

    private MapSqlParameterSource parameters(FeedbackReport report) {
        return new MapSqlParameterSource()
                .addValue("userId", report.getUserId())
                .addValue("status", report.getStatus().name())
                .addValue("category", report.getCategory().name())
                .addValue("title", report.getTitle())
                .addValue("closedAt", JdbcParameters.instant(report.getClosedAt()))
                .addValue("deleteAfter", JdbcParameters.instant(report.getDeleteAfter()));
    }

    private Optional<FeedbackReport> queryOne(String sql, Map<String, ?> parameters) {
        return jdbc.query(sql, parameters, ROW_MAPPER).stream().findFirst();
    }

    private static FeedbackReport map(ResultSet rs, int rowNum) throws SQLException {
        return FeedbackReport.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .status(FeedbackReportStatus.valueOf(rs.getString("status")))
                .category(FeedbackCategory.valueOf(rs.getString("category")))
                .title(rs.getString("title"))
                .createdAt(instant(rs, "created_at"))
                .closedAt(instant(rs, "closed_at"))
                .deleteAfter(instant(rs, "delete_after"))
                .build();
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
