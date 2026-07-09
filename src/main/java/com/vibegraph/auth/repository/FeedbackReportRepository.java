package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.FeedbackReport;

public interface FeedbackReportRepository extends JpaRepository<FeedbackReport, UUID> {

    List<FeedbackReport> findByDeleteAfterLessThanEqual(Instant now);
}
