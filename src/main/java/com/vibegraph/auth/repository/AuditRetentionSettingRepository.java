package com.vibegraph.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.AuditRetentionSetting;

public interface AuditRetentionSettingRepository extends JpaRepository<AuditRetentionSetting, Short> {
}
