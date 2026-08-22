package com.vibegraph.mcp.orchestration;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
