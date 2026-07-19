package com.vibegraph.auth.dto;

import java.util.List;

public record AdminFeedbackDetailResponse(
        AdminFeedbackResponse report,
        List<FeedbackMessageResponse> messages
) {}
