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

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.dto.AdminFeedbackDetailResponse;
import com.vibegraph.auth.dto.AdminFeedbackReplyRequest;
import com.vibegraph.auth.dto.AdminFeedbackResponse;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.common.dto.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminService adminService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminFeedbackResponse>>> getReports() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getFeedbackReports()));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<AdminFeedbackDetailResponse>> getReportDetail(
            @PathVariable UUID reportId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getFeedbackReportDetail(reportId)));
    }

    @PostMapping("/{reportId}/reply")
    public ResponseEntity<ApiResponse<Void>> reply(
            @PathVariable UUID reportId,
            @Valid @RequestBody AdminFeedbackReplyRequest request
    ) {
        UUID adminUserId = currentUser.id();
        adminService.replyToFeedbackReport(reportId, adminUserId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{reportId}/close")
    public ResponseEntity<ApiResponse<Void>> close(
            @PathVariable UUID reportId
    ) {
        adminService.closeFeedbackReport(reportId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
