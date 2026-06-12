package com.vibegraph.watcher.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T38 — Integration test: realtime graph update on file save.
 *
 * <h2>Acceptance (PB09 / RB20)</h2>
 * A {@code .java} change under a watched project root must push a graph update to
 * {@code /topic/projects/{projectId}/updates} within 3 seconds, so a subscribed client
 * patches its graph without a full reload.
 *
 * <h2>Why this test is {@link Disabled} — architectural blocker (not a test gap)</h2>
 * The end-to-end chain required by the acceptance does not exist in the current codebase:
 *
 * <pre>
 *   file change → graph mutation → GraphUpdateController.broadcast* → /topic/.../updates
 * </pre>
 *
 * It is broken at two points as of commit {@code 32ec192}:
 *
 * <ol>
 *   <li><b>No producer→broadcast bridge.</b> {@code FileWatcherServiceImpl.dispatch} calls
 *       {@code GraphRepository.deleteFile(projectId, relativePath)} on DELETE (T25/T37) and
 *       only notifies in-process {@code onFileChange} handlers for CREATE/MODIFY. It never
 *       invokes {@code GraphUpdateController.broadcastFullUpdate / broadcastIncremental}.
 *       Impact analysis confirms {@code GraphUpdateController} is consumed only by the
 *       archive/tarball import services, never by the watcher.</li>
 *   <li><b>Watcher is never started in-app.</b> No production code calls
 *       {@code FileWatcherService.startWatching}, so no project is actually being watched
 *       in a running context.</li>
 * </ol>
 *
 * Additionally, CREATE/MODIFY incremental re-parse is explicitly not wired
 * ({@code ParserService.parseFileWithCache} is deferred to Sprint 2), so even with a bridge
 * only the DELETE path could mutate the graph today.
 *
 * <h2>What must land before enabling this test</h2>
 * <ul>
 *   <li>A bridge that registers an {@code onFileChange} handler (or equivalent) which, after
 *       the watcher mutates the graph, calls {@code GraphUpdateController.broadcastIncremental}
 *       (DELETE: {@code removed}; CREATE/MODIFY once re-parse exists: {@code added}/{@code modified}).</li>
 *   <li>Lifecycle wiring that calls {@code startWatching(projectId, rootPath)} for analyzed
 *       projects.</li>
 * </ul>
 *
 * <h2>Intended verification (once unblocked)</h2>
 * Boot the app with Testcontainers Neo4j, import/analyze a small project, connect a STOMP
 * client to {@code /ws/graph-updates}, subscribe to {@code /topic/projects/{id}/updates},
 * delete a {@code .java} file under the watched root, and assert an {@code INCREMENTAL}
 * event carrying the removed node/edge ids arrives within 3s. Mirror the broadcast payload
 * assertions in {@code GraphUpdateControllerTest}.
 *
 * <p>This class is intentionally left as a documented, skipped specification rather than a
 * test that fakes the broadcast (which would assert nothing about the real save→update path).
 */
@Disabled("T38 BLOCKED: no FileWatcher→GraphUpdateController broadcast bridge and watcher is "
        + "never started in-app (commit 32ec192). Realtime save→update path does not exist yet; "
        + "see class Javadoc for the wiring required before enabling. Not faking E2E.")
@DisplayName("T38 Realtime update (save → /topic/.../updates < 3s)")
class RealtimeUpdateIT {

    @Test
    @DisplayName("DELETE of a .java file broadcasts an INCREMENTAL graph update within 3s")
    void deleteBroadcastsIncrementalUpdate() {
        // Blocked — see class-level @Disabled reason and Javadoc.
        // Intended flow once the producer→broadcast bridge + startWatching wiring exist:
        //   1. Testcontainers Neo4j up; import + analyze a small fixture project.
        //   2. STOMP connect to /ws/graph-updates; subscribe /topic/projects/{id}/updates.
        //   3. Delete a watched .java file under the project root.
        //   4. Assert an INCREMENTAL GraphUpdateEvent with removed ids arrives < 3s.
    }
}
