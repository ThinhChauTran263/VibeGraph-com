package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Server-side guardrails for the HTTP full-graph payload
 * ({@code GET /api/projects/{id}/graph}).
 *
 * <p>The default HTTP graph payload is uncapped so the frontend filter panel and the rendered
 * graph are based on the same node/edge set. Deployments that need a browser safety rail can set
 * {@link #nodeLimit} / {@link #edgeLimit} to a positive value. Callers may also request explicit
 * positive limits via query params, clamped to {@link #maxNodeLimit} / {@link #maxEdgeLimit}.
 *
 * <p>NOTE: only the HTTP boundary is capped. Internal Java consumers (diagram inference, MCP
 * analyzers, websocket broadcast) keep calling {@code GraphService.getFullGraph} directly and
 * still receive the complete graph.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.graph")
public class GraphPayloadProperties {

    /** Default maximum nodes returned over HTTP; {@code 0} means uncapped. */
    private int nodeLimit = 0;

    /** Default maximum edges returned over HTTP; {@code 0} means uncapped. */
    private int edgeLimit = 0;

    /** Hard ceiling on an explicitly requested node limit (protects server + browser). */
    private int maxNodeLimit = 10000;

    /** Hard ceiling on an explicitly requested edge limit. */
    private int maxEdgeLimit = 30000;
}
