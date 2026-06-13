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
 * <h2>Status: partially unblocked</h2>
 * The producer→broadcast bridge and watcher lifecycle now exist:
 * <ul>
 *   <li>{@code FileChangeBroadcaster} registers an {@code onFileChange} handler that, on a
 *       {@code .java} DELETE (after the watcher prunes via {@code GraphRepository.deleteFile}),
 *       re-reads {@code getFullGraph} and calls {@code GraphUpdateController.broadcastFullUpdate}
 *       → {@code /topic/projects/{id}/updates}. Covered deterministically by
 *       {@code RealtimeUpdateBroadcastTest}.</li>
 *   <li>The import/analyze flows call {@code FileChangeBroadcaster.watchProject(projectId, rootPath)}
 *       on success, and {@code ProjectServiceImpl.deleteProject} stops the watcher.</li>
 * </ul>
 *
 * <h2>Why this full E2E remains {@link Disabled}</h2>
 * This class is the heavyweight end-to-end variant (real STOMP client + Testcontainers Neo4j +
 * a real file delete on disk + the OS WatchService + the &lt;3s latency assertion). That belongs
 * to <b>T70 (FileWatcher incremental E2E)</b>, which is out of scope here. The component-level
 * wiring it would exercise is already proven by {@code RealtimeUpdateBroadcastTest} and the
 * watcher unit tests; this remains as the E2E spec to enable under T70.
 *
 * <p>Additionally, CREATE/MODIFY realtime updates stay out of scope until incremental re-parse
 * exists ({@code ParserService.parseFileWithCache}, deferred to Sprint 2) — only the DELETE path
 * mutates the graph today, so only DELETE produces a broadcast.
 *
 * <h2>Intended verification (T70)</h2>
 * Boot the app with Testcontainers Neo4j, import/analyze a small project (which starts the
 * watcher), connect a STOMP client to {@code /ws/graph-updates}, subscribe to
 * {@code /topic/projects/{id}/updates}, delete a {@code .java} file under the watched root, and
 * assert a {@code FULL_UPDATE} reflecting the pruned graph arrives within 3s.
 */
@Disabled("T38 full STOMP+Neo4j <3s E2E is T70 scope. Bridge + lifecycle are implemented and "
        + "covered by RealtimeUpdateBroadcastTest; DELETE path broadcasts, CREATE/MODIFY pending "
        + "incremental re-parse (Sprint 2). Enable under T70.")
@DisplayName("T38 Realtime update E2E (save → /topic/.../updates < 3s)")
class RealtimeUpdateIT {

    @Test
    @DisplayName("DELETE of a .java file broadcasts a graph update within 3s")
    void deleteBroadcastsUpdateWithin3s() {
        // E2E deferred to T70 — see class-level @Disabled reason and Javadoc.
        // Component-level wiring is proven by RealtimeUpdateBroadcastTest:
        //   watcher DELETE → deleteFile → FileChangeBroadcaster → getFullGraph → broadcastFullUpdate.
    }
}
