package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Server-side guardrails for the HTTP full-graph payload
 * ({@code GET /api/projects/{id}/graph}).
 *
 * <p>The browser must never receive an unbounded graph by default: for very large projects the
 * transfer + {@code JSON.parse} + filtering on the main thread can freeze the tab even before
 * the frontend's own render cap kicks in. The controller caps the payload to {@link #nodeLimit}
 * nodes / {@link #edgeLimit} edges and reports truncation metadata. Callers may request higher
 * explicit limits via query params, but never above {@link #maxNodeLimit} / {@link #maxEdgeLimit}.
 *
 * <p>NOTE: only the HTTP boundary is capped. Internal Java consumers (diagram inference, MCP
 * analyzers, websocket broadcast) keep calling {@code GraphService.getFullGraph} directly and
 * still receive the complete graph.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.graph")
public class GraphPayloadProperties {

    /** Default maximum nodes returned over HTTP when no explicit limit is requested. */
    private int nodeLimit = 1500;

    /** Default maximum edges returned over HTTP when no explicit limit is requested. */
    private int edgeLimit = 4000;

    /** Hard ceiling on an explicitly requested node limit (protects server + browser). */
    private int maxNodeLimit = 10000;

    /** Hard ceiling on an explicitly requested edge limit. */
    private int maxEdgeLimit = 30000;
}
