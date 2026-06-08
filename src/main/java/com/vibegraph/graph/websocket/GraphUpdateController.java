package com.vibegraph.graph.websocket;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.vibegraph.graph.dto.response.ProjectStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes graph realtime updates to STOMP subscribers.
 *
 * Topics:
 * - /topic/projects/{id}/updates  (FULL_UPDATE / INCREMENTAL events)
 * - /topic/projects/{id}/status   (analysis progress)
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GraphUpdateController {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastFullUpdate(String projectId) {
        // TODO: Send to /topic/projects/{projectId}/updates
    }

    public void broadcastIncremental(String projectId, Object diff) {
        // TODO: Send incremental update
    }

    /**
     * Publish an analysis status/progress event to {@code /topic/projects/{projectId}/status}.
     * {@code progress} is clamped to 0..100.
     */
    public void broadcastStatus(String projectId, String status, int progress) {
        broadcastStatus(projectId, status, progress, null);
    }

    /**
     * Publish an analysis status/progress event with an optional human-readable {@code message}
     * (e.g. a failure reason) to {@code /topic/projects/{projectId}/status}. {@code progress}
     * is clamped to 0..100.
     */
    public void broadcastStatus(String projectId, String status, int progress, String message) {
        int clamped = Math.max(0, Math.min(100, progress));
        ProjectStatusEvent event = new ProjectStatusEvent(projectId, status, clamped, message, Instant.now());
        messagingTemplate.convertAndSend("/topic/projects/" + projectId + "/status", event);
        log.debug("Status broadcast: project={} status={} progress={}", projectId, status, clamped);
    }

    /** Type-safe overload so callers use {@link ProjectStatus} instead of raw strings. */
    public void broadcastStatus(String projectId, ProjectStatus status, int progress) {
        broadcastStatus(projectId, status.name(), progress);
    }

    /** Type-safe overload with an optional status detail message. */
    public void broadcastStatus(String projectId, ProjectStatus status, int progress, String message) {
        broadcastStatus(projectId, status.name(), progress, message);
    }
}
