package com.vibegraph.auth.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vibegraph.auth.dto.FeedbackMessageRequest;
import com.vibegraph.auth.dto.FeedbackMessageResponse;
import com.vibegraph.auth.dto.FeedbackReportCreateRequest;
import com.vibegraph.auth.dto.FeedbackReportDetailResponse;
import com.vibegraph.auth.dto.FeedbackReportResponse;
import com.vibegraph.auth.service.FeedbackReportService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for user report/feedback management.
 *
 * <ul>
 *   <li>POST   /api/account/reports                        — create report + first message</li>
 *   <li>GET    /api/account/reports                        — list own reports</li>
 *   <li>GET    /api/account/reports/{reportId}             — report detail with messages</li>
 *   <li>POST   /api/account/reports/{reportId}/messages    — reply to open report</li>
 *   <li>PATCH  /api/account/reports/{reportId}/close       — close own report</li>
 * </ul>
 *
 * <p>All endpoints require authentication. Ownership is enforced inside the service layer.
 */
@RestController
@RequestMapping("/api/account/reports")
@RequiredArgsConstructor
public class AccountReportController {

    private final FeedbackReportService feedbackReportService;

    /** Create a new report. The {@code body} field becomes the first message in the thread. */
    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackReportResponse>> createReport(
            @Valid @RequestBody FeedbackReportCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(feedbackReportService.createReport(request)));
    }

    /** List all reports belonging to the authenticated user. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedbackReportResponse>>> listReports() {
        return ResponseEntity.ok(ApiResponse.success(feedbackReportService.listReports()));
    }

    /** Get full detail of a report (metadata + messages). Returns 403 if not owned by caller. */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<FeedbackReportDetailResponse>> getReport(
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.success(feedbackReportService.getReportDetail(reportId)));
    }

    /** Append a reply message to an open report thread. Returns 403 if not owned; 500 if CLOSED. */
    @PostMapping("/{reportId}/messages")
    public ResponseEntity<ApiResponse<FeedbackMessageResponse>> addMessage(
            @PathVariable UUID reportId,
            @Valid @RequestBody FeedbackMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(feedbackReportService.addMessage(reportId, request)));
    }

    /** Close an open report. Idempotent — closing an already-closed report is a no-op. */
    @PatchMapping("/{reportId}/close")
    public ResponseEntity<ApiResponse<FeedbackReportResponse>> closeReport(
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.success(feedbackReportService.closeReport(reportId)));
    }
}
