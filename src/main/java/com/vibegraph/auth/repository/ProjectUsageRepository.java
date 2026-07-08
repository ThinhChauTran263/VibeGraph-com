package com.vibegraph.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.ProjectUsage;

public interface ProjectUsageRepository extends JpaRepository<ProjectUsage, String> {

    @Query("SELECT COALESCE(SUM(p.storageBytes), 0) FROM ProjectUsage p WHERE p.ownerId = :ownerId")
    long sumStorageBytesByOwnerId(@Param("ownerId") UUID ownerId);
}
