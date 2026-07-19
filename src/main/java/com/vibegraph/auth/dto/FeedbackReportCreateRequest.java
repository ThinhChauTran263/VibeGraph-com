package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.FeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/account/reports.
 * {@code body} is the content of the first message in the thread.
 */
public record FeedbackReportCreateRequest(
        @NotNull FeedbackCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String body
) {}
