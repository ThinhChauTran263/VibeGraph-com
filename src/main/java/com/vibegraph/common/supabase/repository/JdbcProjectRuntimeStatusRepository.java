package com.vibegraph.common.supabase.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.vibegraph.common.supabase.SupabaseProperties;
import com.vibegraph.graph.repository.ProjectRuntimeStatusRepository;
import com.vibegraph.graph.websocket.ProjectStatusEvent;

@Repository
public class JdbcProjectRuntimeStatusRepository implements ProjectRuntimeStatusRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SupabaseProperties properties;

    public JdbcProjectRuntimeStatusRepository(
            @Qualifier("supabaseJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            SupabaseProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void upsert(ProjectStatusEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        jdbc.update("""
                INSERT INTO project_runtime_status
                    (project_id, status, progress, message, updated_at)
                VALUES (:projectId, :status, :progress, :message, :updatedAt)
                ON CONFLICT (project_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    progress = EXCLUDED.progress,
                    message = EXCLUDED.message,
                    updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource()
                .addValue("projectId", event.projectId())
                .addValue("status", event.status())
                .addValue("progress", event.progress())
                .addValue("message", event.message())
                .addValue("updatedAt", JdbcParameters.instant(event.timestamp())));
    }
}
