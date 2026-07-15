package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.FeedbackMessage;

public interface FeedbackMessageRepository extends JpaRepository<FeedbackMessage, UUID> {

    List<FeedbackMessage> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
