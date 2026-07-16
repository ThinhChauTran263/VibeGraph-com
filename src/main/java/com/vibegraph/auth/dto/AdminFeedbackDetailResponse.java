package com.vibegraph.auth.dto;

import java.util.List;

import com.vibegraph.auth.domain.FeedbackMessage;

public record AdminFeedbackDetailResponse(
        AdminFeedbackResponse report,
        List<FeedbackMessage> messages
) {}
