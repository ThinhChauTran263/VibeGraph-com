package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.FeedbackCategory;
import com.vibegraph.auth.domain.entity.FeedbackMessage;
import com.vibegraph.auth.domain.entity.FeedbackReport;
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

/**
 * Unit tests for {@link FeedbackReportService}.
 *
 * Key rules under test:
 *  - Creating a report also saves the first message in the same call
 *  - List returns only reports owned by the current user
 *  - Getting another user's report → ForbiddenException (same as non-existent)
 *  - Replying to an open report → succeeds
 *  - Replying to a CLOSED report → IllegalStateException
 *  - Closing sets closedAt and deleteAfter (+7 days)
 *  - Closing again is idempotent (returns same response without mutating)
 *
 * Run: mvnw test -Dtest=FeedbackReportServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackReportService")
class FeedbackReportServiceTest {

    @Mock FeedbackReportRepository reportRepository;
    @Mock FeedbackMessageRepository messageRepository;
    @Mock CurrentUser currentUser;
    @Mock FeedbackReportRealtimePublisher realtimePublisher;
    @Mock AuditService auditService;

    @InjectMocks FeedbackReportService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private FeedbackReport openReport;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(currentUser.id()).thenReturn(userId);

        openReport = FeedbackReport.builder()
                .id(reportId)
                .userId(userId)
                .category(FeedbackCategory.BUG)
                .title("App crashes on startup")
                .status(FeedbackReportStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }

    // ── createReport ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createReport saves report and first message in same call")
    void createReport_savesReportAndFirstMessage() {
        when(reportRepository.save(any(FeedbackReport.class))).thenReturn(openReport);

        FeedbackReportCreateRequest req = new FeedbackReportCreateRequest(
                FeedbackCategory.BUG, "App crashes on startup", "Steps to reproduce…");

        FeedbackReportResponse res = service.createReport(req);

        assertThat(res.id()).isEqualTo(reportId);
        assertThat(res.title()).isEqualTo("App crashes on startup");
        assertThat(res.status()).isEqualTo(FeedbackReportStatus.OPEN);

        // First message must be persisted with USER role and the body from the request
        verify(messageRepository).save(argThat(m ->
                m.getBody().equals("Steps to reproduce…")
                && m.getSenderRole() == FeedbackSenderRole.USER
                && m.getReportId().equals(reportId)));
    }

    // ── listReports ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listReports returns only reports belonging to the authenticated user")
    void listReports_returnsOwnReports() {
        when(reportRepository.findByUserId(userId)).thenReturn(List.of(openReport));

        List<FeedbackReportResponse> result = service.listReports();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("listReports returns empty list when user has no reports")
    void listReports_empty_returnsEmptyList() {
        when(reportRepository.findByUserId(userId)).thenReturn(List.of());
        assertThat(service.listReports()).isEmpty();
    }

    // ── getReportDetail ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getReportDetail returns report with messages in order")
    void getReportDetail_returnsReportWithMessages() {
        FeedbackMessage msg1 = FeedbackMessage.builder()
                .id(UUID.randomUUID()).reportId(reportId)
                .senderRole(FeedbackSenderRole.USER).body("First message")
                .createdAt(Instant.now()).build();
        FeedbackMessage msg2 = FeedbackMessage.builder()
                .id(UUID.randomUUID()).reportId(reportId)
                .senderRole(FeedbackSenderRole.ADMIN).body("Admin reply")
                .createdAt(Instant.now()).build();

        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(openReport));
        when(messageRepository.findByReportIdOrderByCreatedAtAsc(reportId))
                .thenReturn(List.of(msg1, msg2));

        FeedbackReportDetailResponse detail = service.getReportDetail(reportId);

        assertThat(detail.report().id()).isEqualTo(reportId);
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).body()).isEqualTo("First message");
        assertThat(detail.messages().get(1).senderRole()).isEqualTo(FeedbackSenderRole.ADMIN);
    }

    @Test
    @DisplayName("getReportDetail for another user's report → ForbiddenException")
    void getReportDetail_otherUsersReport_throwsForbidden() {
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReportDetail(reportId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── addMessage ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addMessage to open report → succeeds and returns message")
    void addMessage_toOpenReport_succeeds() {
        FeedbackMessage saved = FeedbackMessage.builder()
                .id(UUID.randomUUID()).reportId(reportId)
                .senderUserId(userId)
                .senderRole(FeedbackSenderRole.USER).body("More context")
                .createdAt(Instant.now()).build();

        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(openReport));
        when(messageRepository.save(any(FeedbackMessage.class))).thenReturn(saved);

        FeedbackMessageResponse res = service.addMessage(reportId, new FeedbackMessageRequest("More context"));

        assertThat(res.body()).isEqualTo("More context");
        assertThat(res.senderRole()).isEqualTo(FeedbackSenderRole.USER);
        verify(realtimePublisher).publishMessageAdded(eq(reportId), argThat(message ->
                message.body().equals("More context")
                        && message.senderRole() == FeedbackSenderRole.USER));
    }

    @Test
    @DisplayName("addMessage to CLOSED report → IllegalStateException")
    void addMessage_toClosedReport_throws() {
        openReport.setStatus(FeedbackReportStatus.CLOSED);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(openReport));

        assertThatThrownBy(() -> service.addMessage(reportId, new FeedbackMessageRequest("Late reply")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    @DisplayName("addMessage to another user's report → ForbiddenException")
    void addMessage_otherUsersReport_throwsForbidden() {
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMessage(reportId, new FeedbackMessageRequest("x")))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any());
    }

    // ── closeReport ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("closeReport sets CLOSED status, closedAt, and deleteAfter (+7 days)")
    void closeReport_setsClosedAtAndDeleteAfter() {
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(openReport));
        when(reportRepository.save(any(FeedbackReport.class))).thenAnswer(inv -> inv.getArgument(0));

        FeedbackReportResponse res = service.closeReport(reportId);

        assertThat(res.status()).isEqualTo(FeedbackReportStatus.CLOSED);
        assertThat(res.closedAt()).isNotNull();
        assertThat(res.deletesAfter()).isNotNull();
        assertThat(res.deletesAfter()).isEqualTo(res.closedAt().plusSeconds(7L * 24 * 60 * 60));
        verify(realtimePublisher).publishReportClosed(argThat(report ->
                report.id().equals(reportId)
                        && report.status() == FeedbackReportStatus.CLOSED));
    }

    @Test
    @DisplayName("closeReport on already-CLOSED report → idempotent, no re-save")
    void closeReport_alreadyClosed_isIdempotent() {
        openReport.setStatus(FeedbackReportStatus.CLOSED);
        openReport.setClosedAt(Instant.now());
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(openReport));

        service.closeReport(reportId);

        // No save should be called for an already-closed report
        verify(reportRepository, never()).save(any());
        verify(realtimePublisher, never()).publishReportClosed(any());
    }

    @Test
    @DisplayName("cleanup makes reports eligible only when deleteAfter is at or before the run time")
    void cleanupExpiredReports_passesCurrentEligibilityCutoffToRepository() {
        Instant before = Instant.now();

        service.cleanupExpiredReports();

        Instant after = Instant.now();
        verify(reportRepository).deleteByDeleteAfterLessThanEqual(argThat(cutoff ->
                !cutoff.isBefore(before) && !cutoff.isAfter(after)));
    }
}
