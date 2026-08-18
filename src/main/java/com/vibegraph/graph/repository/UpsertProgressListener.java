package com.vibegraph.graph.repository;

/**
 * Receives per-group write progress from {@link GraphRepository#upsertAnalysis}.
 *
 * <p>Lets callers translate group counts into an overall analysis percentage so the
 * UI shows smooth progress during the graph persistence phase instead of one blocking
 * jump. Callbacks fire INSIDE the single write transaction (B-M11) — they are purely
 * observational and must never throw or do work.
 */
@FunctionalInterface
public interface UpsertProgressListener {

    /** A listener that discards every update. Used by the backward-compatible overload. */
    UpsertProgressListener NOOP = (groupsWritten, totalGroups) -> { };

    /**
     * @param groupsWritten number of node/edge groups persisted so far (0..totalGroups)
     * @param totalGroups   total number of node label groups plus edge type groups
     */
    void onGroupWritten(int groupsWritten, int totalGroups);
}
