package com.vibegraph.graph.service;

/**
 * Receives coarse-grained analysis progress so callers (e.g. import services) can
 * surface intermediate progress to the UI instead of a single 0 -> 100 jump.
 *
 * <p>Progress is an overall percentage in {@code 0..100}; {@code phase} is a short,
 * human-readable label (e.g. {@code "Parsing files (12/40)"}).
 */
@FunctionalInterface
public interface AnalysisProgressListener {

    /** A listener that discards every update. Used by the backward-compatible no-arg path. */
    AnalysisProgressListener NOOP = (percent, phase) -> { };

    /**
     * @param percent overall analysis progress, 0..100
     * @param phase   short human-readable phase label; may be {@code null}
     */
    void onProgress(int percent, String phase);
}
