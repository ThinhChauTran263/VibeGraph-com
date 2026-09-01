package com.vibegraph.auth.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.repository.projection.AdminDistributionRow;

import jakarta.persistence.LockModeType;

public interface UserAccountSettingsRepository extends JpaRepository<UserAccountSettings, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserAccountSettings s JOIN FETCH s.plan WHERE s.userId = :userId")
    Optional<UserAccountSettings> findByIdForUpdate(@Param("userId") UUID userId);

    long countByBlockedAtIsNotNull();

    long countByPlan_Code(String planCode);

    @Query("""
            SELECT s.plan.code AS label, COUNT(s) AS value
            FROM UserAccountSettings s
            WHERE s.plan IS NOT NULL
            GROUP BY s.plan.code
            ORDER BY s.plan.code
            """)
    List<AdminDistributionRow> countUsersByPlan();
}
