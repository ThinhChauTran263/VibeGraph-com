package com.vibegraph.common.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.Announcement;
import com.vibegraph.auth.repository.AnnouncementRepository;

@Repository
public class JdbcAnnouncementRepository implements AnnouncementRepository {

    private static final RowMapper<Announcement> ROW_MAPPER = JdbcAnnouncementRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAnnouncementRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Announcement> findAll() {
        return jdbc.query("SELECT * FROM announcements ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public Optional<Announcement> findById(UUID id) {
        return jdbc.query(
                "SELECT * FROM announcements WHERE id = :id", Map.of("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Announcement save(Announcement announcement) {
        return announcement.getId() == null ? insert(announcement) : update(announcement);
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM announcements WHERE id = :id", Map.of("id", id));
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM announcements WHERE ends_at < :cutoff",
                Map.of("cutoff", JdbcParameters.instant(cutoff)));
    }

    private Announcement insert(Announcement announcement) {
        return jdbc.queryForObject("""
                INSERT INTO announcements
                    (id, type, severity, target, title, body, starts_at, ends_at, dismissible,
                     active, created_by_user_id, created_at)
                VALUES
                    (:id, :type, :severity, :target, :title, :body, :startsAt, :endsAt, :dismissible,
                     :active, :createdByUserId, COALESCE(:createdAt, now()))
                RETURNING *
                """, parameters(announcement).addValue("id", UUID.randomUUID()), ROW_MAPPER);
    }

    private Announcement update(Announcement announcement) {
        return jdbc.queryForObject("""
                UPDATE announcements
                SET type = :type, severity = :severity, target = :target, title = :title, body = :body,
                    starts_at = :startsAt, ends_at = :endsAt, dismissible = :dismissible,
                    active = :active, created_by_user_id = :createdByUserId
                WHERE id = :id
                RETURNING *
                """, parameters(announcement).addValue("id", announcement.getId()), ROW_MAPPER);
    }

    private MapSqlParameterSource parameters(Announcement announcement) {
        return new MapSqlParameterSource()
                .addValue("type", announcement.getType())
                .addValue("severity", announcement.getSeverity())
                .addValue("target", announcement.getTarget())
                .addValue("title", announcement.getTitle())
                .addValue("body", announcement.getBody())
                .addValue("startsAt", JdbcParameters.instant(announcement.getStartsAt()))
                .addValue("endsAt", JdbcParameters.instant(announcement.getEndsAt()))
                .addValue("dismissible", announcement.isDismissible())
                .addValue("active", announcement.isActive())
                .addValue("createdByUserId", announcement.getCreatedByUserId())
                .addValue("createdAt", JdbcParameters.instant(announcement.getCreatedAt()));
    }

    private static Announcement map(ResultSet rs, int rowNum) throws SQLException {
        return Announcement.builder()
                .id(rs.getObject("id", UUID.class))
                .type(rs.getString("type"))
                .severity(rs.getString("severity"))
                .target(rs.getString("target"))
                .title(rs.getString("title"))
                .body(rs.getString("body"))
                .createdByUserId(rs.getObject("created_by_user_id", UUID.class))
                .startsAt(JdbcFeedbackReportRepository.instant(rs, "starts_at"))
                .endsAt(JdbcFeedbackReportRepository.instant(rs, "ends_at"))
                .dismissible(rs.getBoolean("dismissible"))
                .active(rs.getBoolean("active"))
                .createdAt(JdbcFeedbackReportRepository.instant(rs, "created_at"))
                .updatedAt(JdbcFeedbackReportRepository.instant(rs, "updated_at"))
                .build();
    }
}
