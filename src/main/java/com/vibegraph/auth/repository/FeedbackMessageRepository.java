package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import com.vibegraph.auth.domain.entity.FeedbackMessage;

public interface FeedbackMessageRepository {

    FeedbackMessage save(FeedbackMessage message);

    List<FeedbackMessage> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
