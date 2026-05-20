package com.vibegraph.graph.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket message handler for graph realtime updates.
 *
 * Topics published:
 * - /topic/projects/{id}/updates  (FULL_UPDATE / INCREMENTAL events)
 * - /topic/projects/{id}/status   (analysis progress)
 *
 * TODO: Implement broadcast methods
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

    public void broadcastStatus(String projectId, String status, int progress) {
        // TODO: Send to /topic/projects/{projectId}/status
    }
}
