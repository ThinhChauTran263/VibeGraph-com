package com.vibegraph.auth.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vibegraph.auth.dto.FeedbackMessageResponse;
import com.vibegraph.auth.dto.FeedbackReportRealtimeEvent;
import com.vibegraph.auth.dto.FeedbackReportResponse;

import lombok.RequiredArgsConstructor;

/** Publishes saved feedback/report changes to authorized STOMP subscribers. */
@Service
@RequiredArgsConstructor
public class FeedbackReportRealtimePublisher {

    private static final String REPORT_TOPIC_TEMPLATE = "/topic/reports/%s";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishMessageAdded(java.util.UUID reportId, FeedbackMessageResponse message) {
        publishAfterCommit(FeedbackReportRealtimeEvent.messageAdded(reportId, message));
    }

    public void publishReportClosed(FeedbackReportResponse report) {
        publishAfterCommit(FeedbackReportRealtimeEvent.reportClosed(report));
    }

    private void publishAfterCommit(FeedbackReportRealtimeEvent event) {
        Runnable publish = () -> messagingTemplate.convertAndSend(reportTopic(event.reportId()), event);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private static String reportTopic(java.util.UUID reportId) {
        return String.format(REPORT_TOPIC_TEMPLATE, reportId);
    }
}
