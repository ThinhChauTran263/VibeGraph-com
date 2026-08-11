package com.vibegraph.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.FeedbackMessage;
import com.vibegraph.auth.domain.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.domain.FeedbackSenderRole;
import com.vibegraph.auth.dto.FeedbackMessageRequest;
import com.vibegraph.auth.dto.FeedbackMessageResponse;
import com.vibegraph.auth.dto.FeedbackReportCreateRequest;
import com.vibegraph.auth.dto.FeedbackReportDetailResponse;
import com.vibegraph.auth.dto.FeedbackReportResponse;
import com.vibegraph.auth.repository.FeedbackMessageRepository;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.common.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Business logic for user-facing report/feedback operations.
 *
 * <p>Ownership: every mutation verifies that the caller owns the report via
 * {@link FeedbackReportRepository#findByIdAndUserId} — a single-query pattern that
 * avoids timing leaks (unlike findById + manual check).
 *
 * <p>Lifecycle: OPEN → CLOSED (by user). A closed report gets a {@code deleteAfter}
 * timestamp of {@code closedAt + 7 days}. The scheduled cleanup job deletes expired rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackReportService {

    private final FeedbackReportRepository reportRepository;
    private final FeedbackMessageRepository messageRepository;
    private final CurrentUser currentUser;
    private final FeedbackReportRealtimePublisher realtimePublisher;
    private final AuditService auditService;

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Create a new report and persist the first message in the same transaction.
     *
     * @return summary of the newly created report (without messages)
     */
    @Transactional(transactionManager = "supabaseTransactionManager")
    public FeedbackReportResponse createReport(FeedbackReportCreateRequest request) {
        UUID userId = currentUser.id();

        FeedbackReport report = reportRepository.save(FeedbackReport.builder()
                .userId(userId)
                .category(request.category())
                .title(request.title())
                .status(FeedbackReportStatus.OPEN)
                .build());

        // Persist the first message in the same transaction.
        messageRepository.save(FeedbackMessage.builder()
                .reportId(report.getId())
                .senderUserId(userId)
                .senderRole(FeedbackSenderRole.USER)
                .body(request.body())
                .build());

        log.info("User {} created report {} ({})", userId, report.getId(), request.category());
        return toResponse(report);
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    /**
     * List all reports owned by the currently authenticated user, newest first
     * (ordering delegated to Spring Data convention; adjust query if needed).
     */
    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<FeedbackReportResponse> listReports() {
        return reportRepository.findByUserId(currentUser.id())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── GET DETAIL ────────────────────────────────────────────────────────────

    /**
     * Return a report with all its messages, in chronological order.
     * Throws {@link ForbiddenException} if the report does not belong to the caller.
     */
    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public FeedbackReportDetailResponse getReportDetail(UUID reportId) {
        FeedbackReport report = findOwnedReport(reportId);
        List<FeedbackMessageResponse> messages = messageRepository
                .findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
        return new FeedbackReportDetailResponse(toResponse(report), messages);
    }

    // ── ADD MESSAGE ───────────────────────────────────────────────────────────

    /**
     * Append a message to an open report thread.
     *
     * @throws ForbiddenException    if the report is not owned by the caller
     * @throws IllegalStateException if the report is already CLOSED
     */
    @Transactional(transactionManager = "supabaseTransactionManager")
    public FeedbackMessageResponse addMessage(UUID reportId, FeedbackMessageRequest request) {
        FeedbackReport report = findOwnedReport(reportId);

        if (report.getStatus() == FeedbackReportStatus.CLOSED) {
            throw new IllegalStateException("Cannot add a message to a CLOSED report");
        }

        FeedbackMessage message = messageRepository.save(FeedbackMessage.builder()
                .reportId(reportId)
                .senderUserId(currentUser.id())
                .senderRole(FeedbackSenderRole.USER)
                .body(request.body())
                .build());

        FeedbackMessageResponse response = toMessageResponse(message);
        realtimePublisher.publishMessageAdded(reportId, response);
        return response;
    }

    // ── CLOSE ─────────────────────────────────────────────────────────────────

    /**
     * Close an open report. Idempotent: closing an already-closed report is a no-op.
     * Sets {@code closedAt = now} and {@code deleteAfter = closedAt + 7 days}.
     */
    @Transactional(transactionManager = "supabaseTransactionManager")
    public FeedbackReportResponse closeReport(UUID reportId) {
        FeedbackReport report = findOwnedReport(reportId);

        if (report.getStatus() == FeedbackReportStatus.CLOSED) {
            return toResponse(report); // idempotent
        }

        Instant now = Instant.now();
        report.setStatus(FeedbackReportStatus.CLOSED);
        report.setClosedAt(now);
        report.setDeleteAfter(now.plusSeconds(7L * 24 * 3600)); // +7 days

        log.info("User {} closed report {}", currentUser.id(), reportId);
        FeedbackReportResponse response = toResponse(reportRepository.save(report));
        realtimePublisher.publishReportClosed(response);
        auditService.recordCurrentUser("REPORT_CLOSE", report.getUserId(), "REPORT", reportId.toString(),
                java.util.Map.of("deleteAfter", report.getDeleteAfter().toString()));
        return response;
    }

    // ── CLEANUP JOB ───────────────────────────────────────────────────────────

    /**
     * Delete expired (closed + past deleteAfter) reports in bulk.
     * Runs at 02:00 every day. Requires {@code @EnableScheduling} on the application class.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(transactionManager = "supabaseTransactionManager")
    public void cleanupExpiredReports() {
        reportRepository.deleteByDeleteAfterLessThanEqual(Instant.now());
        log.info("Cleaned up expired feedback reports");
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Find a report that both exists and belongs to the current user.
     * Uses a single DB query — empty result means "not found OR access denied",
     * deliberately indistinguishable to prevent information leakage.
     */
    private FeedbackReport findOwnedReport(UUID reportId) {
        return reportRepository.findByIdAndUserId(reportId, currentUser.id())
                .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));
    }

    private FeedbackReportResponse toResponse(FeedbackReport r) {
        return new FeedbackReportResponse(
                r.getId(),
                r.getStatus(),
                r.getCategory(),
                r.getTitle(),
                r.getCreatedAt(),
                r.getClosedAt(),
                r.getDeleteAfter()
        );
    }

    private FeedbackMessageResponse toMessageResponse(FeedbackMessage m) {
        return new FeedbackMessageResponse(
                m.getId(),
                m.getSenderRole(),
                m.getBody(),
                m.getCreatedAt()
        );
    }
}
