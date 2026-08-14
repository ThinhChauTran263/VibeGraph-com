package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Server-side guardrails for the HTTP full-graph payload
 * ({@code GET /api/projects/{id}/graph}).
 *
 * <p>B-M10: the default HTTP graph cap is POSITIVE so oversized graphs are truncated with
 * truthful {@code meta} counts instead of freezing the browser. Callers may request explicit
 * positive limits via query params, clamped to {@link #maxNodeLimit} / {@link #maxEdgeLimit};
 * a non-positive explicit limit falls back to the default cap rather than disabling it.
 *
 * <p>NOTE: only the HTTP boundary is capped. Internal Java consumers (diagram inference, MCP
 * analyzers) keep calling {@code GraphService.getFullGraph} directly and still receive the
 * complete graph.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.graph")
public class GraphPayloadProperties {

    /** Default maximum nodes returned over HTTP; {@code 0} disables the cap (not the default). */
    private int nodeLimit = 5000;

    /** Default maximum edges returned over HTTP; {@code 0} disables the cap (not the default). */
    private int edgeLimit = 15000;

    /** Hard ceiling on an explicitly requested node limit (protects server + browser). */
    private int maxNodeLimit = 10000;

    /** Hard ceiling on an explicitly requested edge limit. */
    private int maxEdgeLimit = 30000;
}
