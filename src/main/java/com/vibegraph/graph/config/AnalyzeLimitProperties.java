package com.vibegraph.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-fast ceilings for the in-memory analysis pipeline.
 *
 * <p>{@code AnalyzeServiceImpl} accumulates every parsed node/edge in memory (and runs flow
 * inference over the whole set) before persisting. A pathologically large project could exhaust the
 * JVM heap. These caps bound that: when exceeded, analysis stops and the project is marked FAILED
 * with a clear message rather than risking an OutOfMemoryError that takes the whole server down.
 *
 * <p>Tune {@code VIBEGRAPH_ANALYZE_MAX_NODES} / {@code VIBEGRAPH_ANALYZE_MAX_EDGES} up if the host
 * has the heap for larger projects. This is a safety ceiling, not a streaming solution — truly huge
 * repositories need batched/streaming ingest (future work).
 */
@Configuration
@ConfigurationProperties(prefix = "vibegraph.analyze")
public class AnalyzeLimitProperties {

    /** Maximum nodes accumulated before analysis fails fast. */
    private int maxNodes = 200_000;

    /** Maximum edges accumulated before analysis fails fast. */
    private int maxEdges = 600_000;

    public int getMaxNodes() {
        return maxNodes;
    }

    public void setMaxNodes(int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public int getMaxEdges() {
        return maxEdges;
    }

    public void setMaxEdges(int maxEdges) {
        this.maxEdges = maxEdges;
    }
}
