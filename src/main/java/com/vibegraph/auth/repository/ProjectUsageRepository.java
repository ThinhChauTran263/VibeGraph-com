package com.vibegraph.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.ProjectUsage;
import com.vibegraph.auth.repository.projection.AdminStorageSubjectRow;

public interface ProjectUsageRepository extends JpaRepository<ProjectUsage, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProjectUsage p WHERE p.projectId = :projectId")
    Optional<ProjectUsage> findByIdForUpdate(@Param("projectId") String projectId);

    @Query("SELECT COALESCE(SUM(p.storageBytes), 0) FROM ProjectUsage p WHERE p.ownerId = :ownerId")
    long sumStorageBytesByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(p.storageBytes), 0) FROM ProjectUsage p")
    long sumStorageBytes();

    @Query(value = """
            SELECT pu.owner_id::text AS id,
                   COALESCE(NULLIF(u.display_name, ''), u.email, pu.owner_id::text) AS name,
                   u.email AS "ownerEmail",
                   SUM(pu.storage_bytes) AS "usedBytes"
            FROM project_usage pu
            LEFT JOIN users u ON u.id = pu.owner_id
            GROUP BY pu.owner_id, u.display_name, u.email
            ORDER BY SUM(pu.storage_bytes) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AdminStorageSubjectRow> findTopStorageUsers(@Param("limit") int limit);

    @Query(value = """
            SELECT pu.project_id AS id,
                   COALESCE(p.name, pu.project_id) AS name,
                   u.email AS "ownerEmail",
                   pu.storage_bytes AS "usedBytes"
            FROM project_usage pu
            LEFT JOIN projects p ON p.project_id = pu.project_id
            LEFT JOIN users u ON u.id = pu.owner_id
            ORDER BY pu.storage_bytes DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AdminStorageSubjectRow> findTopStorageProjects(@Param("limit") int limit);
}
