package com.vibegraph.watcher.service.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vibegraph.watcher.config.WatcherProperties;
import com.vibegraph.watcher.service.DebouncedEventHandler;
import com.vibegraph.watcher.service.EventType;
import com.vibegraph.watcher.service.FileChangeEvent;

/**
 * Deterministic tests for the {@link FileWatcherServiceImpl} event pipeline. Rather than
 * rely on OS-level WatchService delivery (slow / flaky on Windows), these drive the
 * package-private {@code enqueue} entry point directly against a registered temp-dir root,
 * which is exactly what the real poll loop calls. The watcher only emits events to
 * registered handlers (no graph mutation), so assertions observe the captured events.
 */
@DisplayName("FileWatcherServiceImpl pipeline")
class FileWatcherServiceImplTest {

    private static final String PROJECT_ID = "proj-1";
    private static final long VERIFY_TIMEOUT_MS = 2000;

    @TempDir
    Path tempDir;

    private DebouncedEventHandler debouncer;
    private FileWatcherServiceImpl watcher;
    private final List<FileChangeEvent> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        WatcherProperties properties = new WatcherProperties();
        properties.setDebounceMs(60);
        debouncer = new DebouncedEventHandler(properties);
        watcher = new FileWatcherServiceImpl(properties, debouncer);
        watcher.onFileChange(received::add);
        watcher.startWatching(PROJECT_ID, tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        watcher.stopWatching(PROJECT_ID);
        debouncer.shutdown();
    }

    @Nested
    @DisplayName("filtering")
    class Filtering {

        @Test
        @DisplayName("ignores non-.java files")
        void ignoresNonJava() {
            watcher.enqueue(PROJECT_ID, tempDir.resolve("README.md"), EventType.MODIFY);

            sleepPastDebounce();
            assertThat(received).isEmpty();
        }

        @Test
        @DisplayName("ignores files inside ignored directories (build/target/.git)")
        void ignoresIgnoredDirectories() {
            watcher.enqueue(PROJECT_ID, tempDir.resolve("build").resolve("Gen.java"), EventType.DELETE);
            watcher.enqueue(PROJECT_ID, tempDir.resolve("target").resolve("Out.java"), EventType.DELETE);
            watcher.enqueue(PROJECT_ID, tempDir.resolve(".git").resolve("Hook.java"), EventType.MODIFY);

            sleepPastDebounce();
            assertThat(received).isEmpty();
        }

        @Test
        @DisplayName("ignores paths outside the project root")
        void ignoresOutsideRoot() {
            Path outside = tempDir.getParent().resolve("Outside.java");

            watcher.enqueue(PROJECT_ID, outside, EventType.DELETE);

            sleepPastDebounce();
            assertThat(received).isEmpty();
        }
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("DELETE of a .java file is emitted to handlers with the relative path")
        void deleteEmittedToHandlers() {
            watcher.enqueue(PROJECT_ID,
                    tempDir.resolve("com").resolve("example").resolve("Foo.java"),
                    EventType.DELETE);

            assertEventuallyReceived(1);
            assertThat(received.get(0).type()).isEqualTo(EventType.DELETE);
            assertThat(received.get(0).relativePath()).isEqualTo("com/example/Foo.java");
        }

        @Test
        @DisplayName("CREATE/MODIFY are emitted to handlers")
        void createModifyEmittedToHandlers() {
            watcher.enqueue(PROJECT_ID, tempDir.resolve("New.java"), EventType.CREATE);

            assertEventuallyReceived(1);
            assertThat(received.get(0).type()).isEqualTo(EventType.CREATE);
            assertThat(received.get(0).relativePath()).isEqualTo("New.java");
        }
    }

    @Nested
    @DisplayName("debounce")
    class Debounce {

        @Test
        @DisplayName("batches several files in one window into a single flush")
        void batchesMultipleFiles() {
            watcher.enqueue(PROJECT_ID, tempDir.resolve("A.java"), EventType.DELETE);
            watcher.enqueue(PROJECT_ID, tempDir.resolve("B.java"), EventType.DELETE);
            watcher.enqueue(PROJECT_ID, tempDir.resolve("C.java"), EventType.DELETE);

            assertEventuallyReceived(3);
            assertThat(received).extracting(FileChangeEvent::relativePath)
                    .containsExactlyInAnyOrder("A.java", "B.java", "C.java");
        }

        @Test
        @DisplayName("collapses repeated events for the same file into one")
        void collapsesSameFile() {
            watcher.enqueue(PROJECT_ID, tempDir.resolve("Same.java"), EventType.MODIFY);
            watcher.enqueue(PROJECT_ID, tempDir.resolve("Same.java"), EventType.MODIFY);
            watcher.enqueue(PROJECT_ID, tempDir.resolve("Same.java"), EventType.DELETE);

            // Only the last buffered event for the file survives the debounce window.
            assertEventuallyReceived(1);
            sleepPastDebounce();
            assertThat(received).hasSize(1);
            assertThat(received.get(0).relativePath()).isEqualTo("Same.java");
            assertThat(received.get(0).type()).isEqualTo(EventType.DELETE);
        }
    }

    private void assertEventuallyReceived(int expected) {
        long deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            sleep(20);
        }
        assertThat(received).hasSize(expected);
    }

    private void sleepPastDebounce() {
        sleep(300);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
