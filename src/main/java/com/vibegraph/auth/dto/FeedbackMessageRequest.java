package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/account/reports/{reportId}/messages */
public record FeedbackMessageRequest(
        @NotBlank @Size(max = 5000) String body
) {}
