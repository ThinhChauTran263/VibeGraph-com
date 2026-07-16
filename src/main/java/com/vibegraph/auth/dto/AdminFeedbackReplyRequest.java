package com.vibegraph.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminFeedbackReplyRequest(
        @NotBlank @Size(max = 5000) String body
) {}
