package com.vibegraph.graph.websocket;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ProjectStatus;
import com.vibegraph.graph.service.impl.GraphPayloadGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes graph realtime updates to STOMP subscribers.
 *
 * Topics:
 * - /topic/projects/{id}/updates  (FULL_UPDATE / INCREMENTAL events)
 * - /topic/projects/{id}/status   (analysis progress)
 *
 * The broadcast methods receive their payload from the caller (e.g. the import
 * pipeline or a future file watcher); this controller does not query the graph
 * store itself.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GraphUpdateController {

    private static final String UPDATES_TOPIC_TEMPLATE = "/topic/projects/%s/updates";

    private final SimpMessagingTemplate messagingTemplate;
    private final GraphPayloadGuard payloadGuard;
    private final GraphPayloadProperties payloadProperties;

    /**
     * Broadcast a full-graph replacement to {@code /topic/projects/{projectId}/updates}.
     * No-op (with a warning) when {@code projectId} is blank.
     *
     * <p>The payload is capped here at the browser-facing WebSocket boundary using the same
     * {@link GraphPayloadGuard} + limits as the HTTP API, so a {@code FULL_UPDATE} can never push
     * an unbounded graph to subscribed browsers (which would re-introduce the transfer/parse
     * freeze the HTTP cap prevents). Internal Java consumers are unaffected — they read the full
     * graph directly via {@code GraphService}/{@code GraphRepository}, never through this method.
     *
     * @param projectId the project whose graph changed
     * @param graph     the complete current graph snapshot (capped before broadcast)
     */
    public void broadcastFullUpdate(String projectId, GraphDataResponse graph) {
        if (!StringUtils.hasText(projectId)) {
            log.warn("Skipping full graph update broadcast: blank projectId");
            return;
        }
        GraphDataResponse capped = payloadGuard.cap(graph,
                payloadProperties.getNodeLimit(), payloadProperties.getEdgeLimit());
        GraphUpdateEvent event = GraphUpdateEvent.fullUpdate(projectId, capped);
        messagingTemplate.convertAndSend(updatesTopic(projectId), event);
        log.debug("Full graph update broadcast: project={} truncated={}",
                projectId, capped.getMeta() != null && capped.getMeta().isTruncated());
    }

    /**
     * Broadcast an incremental diff to {@code /topic/projects/{projectId}/updates}.
     * Any of {@code added}/{@code modified}/{@code removed} may be null; null
     * sections are simply omitted from the payload. No-op (with a warning) when
     * {@code projectId} is blank.
     *
     * @param projectId the project whose graph changed
     * @param added     nodes/edges added by the change; may be null
     * @param modified  nodes/edges modified by the change; may be null
     * @param removed   node/edge ids removed by the change; may be null
     */
    public void broadcastIncremental(
            String projectId,
            GraphChangeSet added,
            GraphChangeSet modified,
            GraphRemoval removed
    ) {
        if (!StringUtils.hasText(projectId)) {
            log.warn("Skipping incremental graph update broadcast: blank projectId");
            return;
        }
        GraphUpdateEvent event = GraphUpdateEvent.incremental(projectId, added, modified, removed);
        messagingTemplate.convertAndSend(updatesTopic(projectId), event);
        log.debug("Incremental graph update broadcast: project={}", projectId);
    }

    private static String updatesTopic(String projectId) {
        return String.format(UPDATES_TOPIC_TEMPLATE, projectId);
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
