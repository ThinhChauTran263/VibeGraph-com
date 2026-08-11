package com.vibegraph.common.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.repository.NotificationRepository;
import com.vibegraph.auth.repository.projection.NotificationViewRow;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private static final String VIEW_SELECT = """
            SELECT a.id AS id,
                   a.id AS announcement_id,
                   a.created_by_user_id AS creator_user_id,
                   a.title,
                   a.body,
                   a.type,
                   a.severity,
                   a.created_at,
                   a.dismissible,
                   n.read_at,
                   n.dismissed_at
            FROM announcements a
            LEFT JOIN user_notifications n
              ON n.announcement_id = a.id AND n.user_id = :userId
            """;
    private static final RowMapper<NotificationViewRow> ROW_MAPPER = JdbcNotificationRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<NotificationViewRow> findActiveForUser(
            UUID userId, String roleTarget, Instant now, Pageable pageable) {
        MapSqlParameterSource parameters = visibility(userId, roleTarget, now)
                .addValue("limit", pageable.getPageSize());
        return jdbc.query(VIEW_SELECT + """
                WHERE a.active = true
                  AND (a.target = 'ALL' OR a.target = :roleTarget)
                  AND (a.starts_at IS NULL OR a.starts_at <= :now)
                  AND (a.ends_at IS NULL OR a.ends_at > :now)
                  AND n.dismissed_at IS NULL
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT :limit
                """, parameters, ROW_MAPPER);
    }

    @Override
    public Optional<NotificationViewRow> findViewByAnnouncementIdAndUserId(
            UUID announcementId, UUID userId, String roleTarget, Instant now) {
        MapSqlParameterSource parameters = visibility(userId, roleTarget, now)
                .addValue("announcementId", announcementId);
        return jdbc.query(VIEW_SELECT + """
                WHERE a.id = :announcementId
                  AND a.active = true
                  AND (a.target = 'ALL' OR a.target = :roleTarget)
                  AND (a.starts_at IS NULL OR a.starts_at <= :now)
                  AND (a.ends_at IS NULL OR a.ends_at > :now)
                """, parameters, ROW_MAPPER).stream().findFirst();
    }

    @Override
    public int markRead(UUID announcementId, UUID userId, String roleTarget, Instant now) {
        return upsertState(announcementId, userId, roleTarget, now, false);
    }

    @Override
    public int dismiss(UUID announcementId, UUID userId, String roleTarget, Instant now) {
        return upsertState(announcementId, userId, roleTarget, now, true);
    }

    @Override
    public int deleteDismissedBefore(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM user_notifications WHERE dismissed_at < :cutoff",
                Map.of("cutoff", JdbcParameters.instant(cutoff)));
    }

    private int upsertState(
            UUID announcementId, UUID userId, String roleTarget, Instant now, boolean dismiss) {
        MapSqlParameterSource parameters = visibility(userId, roleTarget, now)
                .addValue("announcementId", announcementId);
        String dismissible = dismiss ? " AND a.dismissible = true" : "";
        String insertedReadAt = dismiss ? "NULL" : ":now";
        String insertedDismissedAt = dismiss ? ":now" : "NULL";
        String update = dismiss
                ? "dismissed_at = COALESCE(user_notifications.dismissed_at, EXCLUDED.dismissed_at)"
                : "read_at = COALESCE(user_notifications.read_at, EXCLUDED.read_at)";
        return jdbc.update("""
                INSERT INTO user_notifications
                    (id, user_id, announcement_id, read_at, dismissed_at, created_at)
                SELECT gen_random_uuid(), :userId, a.id, %s, %s, :now
                FROM announcements a
                WHERE a.id = :announcementId
                  AND a.active = true
                  AND (a.target = 'ALL' OR a.target = :roleTarget)
                  AND (a.starts_at IS NULL OR a.starts_at <= :now)
                  AND (a.ends_at IS NULL OR a.ends_at > :now)%s
                ON CONFLICT (user_id, announcement_id) DO UPDATE SET %s
                """.formatted(insertedReadAt, insertedDismissedAt, dismissible, update), parameters);
    }

    private MapSqlParameterSource visibility(UUID userId, String roleTarget, Instant now) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleTarget", roleTarget)
                .addValue("now", JdbcParameters.instant(now));
    }

    private static NotificationViewRow map(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("announcement_id", UUID.class),
                rs.getObject("creator_user_id", UUID.class),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("type"),
                rs.getString("severity"),
                JdbcFeedbackReportRepository.instant(rs, "created_at"),
                rs.getBoolean("dismissible"),
                JdbcFeedbackReportRepository.instant(rs, "read_at"),
                JdbcFeedbackReportRepository.instant(rs, "dismissed_at"));
    }

    private record NotificationRow(
            UUID id,
            UUID announcementId,
            UUID creatorUserId,
            String title,
            String body,
            String type,
            String severity,
            Instant createdAt,
            Boolean dismissible,
            Instant readAt,
            Instant dismissedAt) implements NotificationViewRow {

        @Override public UUID getId() { return id; }
        @Override public UUID getAnnouncementId() { return announcementId; }
        @Override public UUID getCreatorUserId() { return creatorUserId; }
        @Override public String getTitle() { return title; }
        @Override public String getBody() { return body; }
        @Override public String getType() { return type; }
        @Override public String getSeverity() { return severity; }
        @Override public String getCreatorDisplayName() { return null; }
        @Override public String getCreatorEmail() { return null; }
        @Override public Instant getCreatedAt() { return createdAt; }
        @Override public Boolean getDismissible() { return dismissible; }
        @Override public Instant getReadAt() { return readAt; }
        @Override public Instant getDismissedAt() { return dismissedAt; }
    }
}
