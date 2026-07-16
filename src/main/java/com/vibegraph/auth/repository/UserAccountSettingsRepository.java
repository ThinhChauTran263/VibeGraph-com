package com.vibegraph.auth.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.repository.projection.AdminDistributionRow;

public interface UserAccountSettingsRepository extends JpaRepository<UserAccountSettings, UUID> {
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
