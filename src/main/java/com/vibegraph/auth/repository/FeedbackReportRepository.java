package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.FeedbackReport;

public interface FeedbackReportRepository extends JpaRepository<FeedbackReport, UUID> {

    List<FeedbackReport> findByDeleteAfterLessThanEqual(Instant now);

    long countByStatus(com.vibegraph.auth.domain.FeedbackReportStatus status);

    List<FeedbackReport> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM FeedbackReport r WHERE " +
        "(:status IS NULL OR r.status = :status) AND " +
        "(:query IS NULL OR :query = '' OR lower(r.title) LIKE lower(concat('%', :query, '%')))")
    org.springframework.data.domain.Page<FeedbackReport> findAllWithFilters(
            @org.springframework.data.repository.query.Param("status") com.vibegraph.auth.domain.FeedbackReportStatus status,
            @org.springframework.data.repository.query.Param("query") String query,
            org.springframework.data.domain.Pageable pageable);
}
