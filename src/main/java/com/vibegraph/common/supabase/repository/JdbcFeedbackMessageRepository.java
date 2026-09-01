package com.vibegraph.common.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.entity.FeedbackMessage;
import com.vibegraph.auth.domain.FeedbackSenderRole;
import com.vibegraph.auth.repository.FeedbackMessageRepository;

@Repository
public class JdbcFeedbackMessageRepository implements FeedbackMessageRepository {

    private static final RowMapper<FeedbackMessage> ROW_MAPPER = JdbcFeedbackMessageRepository::map;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFeedbackMessageRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FeedbackMessage save(FeedbackMessage message) {
        UUID id = message.getId() == null ? UUID.randomUUID() : message.getId();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("reportId", message.getReportId())
                .addValue("senderUserId", message.getSenderUserId())
                .addValue("senderRole", message.getSenderRole().name())
                .addValue("body", message.getBody());
        return jdbc.queryForObject("""
                INSERT INTO feedback_messages (id, report_id, sender_user_id, sender_role, body)
                VALUES (:id, :reportId, :senderUserId, :senderRole, :body)
                RETURNING *
                """, parameters, ROW_MAPPER);
    }

    @Override
    public List<FeedbackMessage> findByReportIdOrderByCreatedAtAsc(UUID reportId) {
        return jdbc.query(
                "SELECT * FROM feedback_messages WHERE report_id = :reportId ORDER BY created_at, id",
                Map.of("reportId", reportId), ROW_MAPPER);
    }

    private static FeedbackMessage map(ResultSet rs, int rowNum) throws SQLException {
        return FeedbackMessage.builder()
                .id(rs.getObject("id", UUID.class))
                .reportId(rs.getObject("report_id", UUID.class))
                .senderUserId(rs.getObject("sender_user_id", UUID.class))
                .senderRole(FeedbackSenderRole.valueOf(rs.getString("sender_role")))
                .body(rs.getString("body"))
                .createdAt(JdbcFeedbackReportRepository.instant(rs, "created_at"))
                .build();
    }
}

