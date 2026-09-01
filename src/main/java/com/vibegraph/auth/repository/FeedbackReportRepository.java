package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vibegraph.auth.domain.entity.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;

public interface FeedbackReportRepository {

    FeedbackReport save(FeedbackReport report);

    Optional<FeedbackReport> findById(UUID id);

    boolean existsById(UUID id);

    long count();

    List<FeedbackReport> findByUserId(UUID userId);

    Optional<FeedbackReport> findByIdAndUserId(UUID id, UUID userId);

    void deleteByDeleteAfterLessThanEqual(Instant now);

    List<FeedbackReport> findByDeleteAfterLessThanEqual(Instant now);

    long countByStatus(FeedbackReportStatus status);

    List<FeedbackReport> findAllByOrderByCreatedAtDesc();

    Page<FeedbackReport> findAllWithFilters(FeedbackReportStatus status, String query, Pageable pageable);
}
