package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminFeedbackReplyRequest(
        @NotBlank String body
) {}
