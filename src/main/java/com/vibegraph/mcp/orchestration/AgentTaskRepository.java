package com.vibegraph.mcp.orchestration;

import com.vibegraph.mcp.orchestration.entity.AgentTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing persistent AgentTask entities in the database.
 */
@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AgentTask task where task.id = :id")
    Optional<AgentTask> findByIdForUpdate(@Param("id") String id);

    /**
     * Claims a bounded set of terminal leaf tasks without waiting for another replica's cleanup
     * transaction. The edge and replacement guards are deliberately repeated in this query so a
     * stale or partially repaired DAG can never lose a referenced node.
     */
    @Query(value = """
            SELECT t.id
            FROM agent_tasks t
            WHERE t.status IN ('COMPLETED', 'SUPERSEDED', 'FAILED_TERMINAL')
              AND COALESCE(t.completed_at, t.updated_at) < :cutoff
              AND NOT EXISTS (
                  SELECT 1 FROM agent_task_dependencies d
                  WHERE d.dependency_task_id = t.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM agent_task_dependents d
                  WHERE d.task_id = t.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM agent_tasks replacement
                  WHERE replacement.replacement_task_id = t.id
              )
            ORDER BY COALESCE(t.completed_at, t.updated_at), t.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> findPrunableTaskIds(@Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize);

    /** Deletes IDs claimed by {@link #findPrunableTaskIds(Instant, int)} in the same transaction. */
    @Modifying
    @Query(value = """
            DELETE FROM agent_tasks t
            WHERE t.id IN (:ids)
              AND t.status IN ('COMPLETED', 'SUPERSEDED', 'FAILED_TERMINAL')
              AND NOT EXISTS (
                  SELECT 1 FROM agent_task_dependencies d
                  WHERE d.dependency_task_id = t.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM agent_task_dependents d
                  WHERE d.task_id = t.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM agent_tasks replacement
                  WHERE replacement.replacement_task_id = t.id
              )
            """, nativeQuery = true)
    int deleteClaimedTaskIds(@Param("ids") List<String> ids);
}
