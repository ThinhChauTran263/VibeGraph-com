package com.vibegraph.graph.websocket;

import java.time.Instant;

/**
 * Payload broadcast to {@code /topic/projects/{projectId}/status} for analysis progress.
 *
 * @param projectId the project this status applies to
 * @param status    lifecycle status name (see {@code ProjectStatus}, e.g. {@code ANALYZING})
 * @param progress  0..100
 * @param message   optional human-readable detail (e.g. failure reason); may be null
 * @param timestamp when the event was produced
 */
public record ProjectStatusEvent(
        String projectId,
        String status,
        int progress,
        String message,
        Instant timestamp
) {
}
