package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.vibegraph.auth.domain.entity.SecurityEvent;
import com.vibegraph.auth.repository.projection.AdminSecurityAlertRow;

public interface SecurityEventRepository {

    SecurityEvent save(SecurityEvent event);

    void saveAll(List<SecurityEvent> events);

    List<SecurityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AdminSecurityAlertRow> summarizeSince(Instant since);

    int deleteCreatedBefore(Instant cutoff);
}
