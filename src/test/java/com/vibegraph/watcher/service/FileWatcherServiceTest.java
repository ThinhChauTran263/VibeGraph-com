package com.vibegraph.watcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vibegraph.watcher.config.WatcherProperties;
import com.vibegraph.watcher.service.impl.FileWatcherServiceImpl;

/**
 * Public-API behavior tests for {@link FileWatcherService}: watch lifecycle, idempotency,
 * multi-project isolation, argument validation and the disabled switch. These tests do not
 * depend on OS-level WatchService event delivery timing (which is exercised via the
 * deterministic pipeline tests in {@code FileWatcherServiceImplTest}).
 */
@DisplayName("FileWatcherService")
class FileWatcherServiceTest {

    @TempDir
    Path tempDir;

    private DebouncedEventHandler debouncer;
    private FileWatcherService watcher;

    @BeforeEach
    void setUp() {
        WatcherProperties properties = new WatcherProperties();
        properties.setDebounceMs(60);
        debouncer = new DebouncedEventHandler(properties);
        watcher = new FileWatcherServiceImpl(properties, debouncer);
    }

    @AfterEach
    void tearDown() {
        debouncer.shutdown();
    }

    @Nested
    @DisplayName("startWatching / isWatching")
    class StartWatching {

        @Test
        @DisplayName("marks the project as watching")
        void marksWatching() {
            watcher.startWatching("p1", tempDir.toString());

            assertThat(watcher.isWatching("p1")).isTrue();

            watcher.stopWatching("p1");
        }

        @Test
        @DisplayName("is idempotent when called repeatedly for the same project")
        void repeatedStartIsIdempotent() {
            watcher.startWatching("p1", tempDir.toString());
            watcher.startWatching("p1", tempDir.toString());

            assertThat(watcher.isWatching("p1")).isTrue();

            // A single stop fully releases the (single) active watcher.
            watcher.stopWatching("p1");
            assertThat(watcher.isWatching("p1")).isFalse();
        }

        @Test
        @DisplayName("rejects blank arguments")
        void rejectsBlankArguments() {
            assertThatThrownBy(() -> watcher.startWatching("  ", tempDir.toString()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> watcher.startWatching("p1", " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a root path that is not a directory")
        void rejectsNonDirectory() throws IOException {
            Path file = tempDir.resolve("not-a-dir.txt");
            Files.writeString(file, "x");

            assertThatThrownBy(() -> watcher.startWatching("p1", file.toString()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(watcher.isWatching("p1")).isFalse();
        }

        @Test
        @DisplayName("does nothing when the watcher is disabled")
        void noOpWhenDisabled() {
            WatcherProperties disabled = new WatcherProperties();
            disabled.setEnabled(false);
            FileWatcherService disabledWatcher =
                    new FileWatcherServiceImpl(disabled, debouncer);

            disabledWatcher.startWatching("p1", tempDir.toString());

            assertThat(disabledWatcher.isWatching("p1")).isFalse();
        }
    }

    @Nested
    @DisplayName("stopWatching")
    class StopWatching {

        @Test
        @DisplayName("marks the project as no longer watching")
        void stopMarksNotWatching() {
            watcher.startWatching("p1", tempDir.toString());
            watcher.stopWatching("p1");

            assertThat(watcher.isWatching("p1")).isFalse();
        }

        @Test
        @DisplayName("is a no-op for an unknown project")
        void stopUnknownIsNoOp() {
            watcher.stopWatching("does-not-exist");

            assertThat(watcher.isWatching("does-not-exist")).isFalse();
        }
    }

    @Nested
    @DisplayName("isWatching")
    class IsWatching {

        @Test
        @DisplayName("returns false for an unknown project")
        void falseWhenUnknown() {
            assertThat(watcher.isWatching("unknown")).isFalse();
        }

        @Test
        @DisplayName("returns false for a null project id")
        void falseWhenNull() {
            assertThat(watcher.isWatching(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Multi-project support")
    class MultiProject {

        @Test
        @DisplayName("watches multiple projects simultaneously and isolates stop")
        void watchesMultipleProjects() throws IOException {
            Path project1 = Files.createDirectories(tempDir.resolve("project1"));
            Path project2 = Files.createDirectories(tempDir.resolve("project2"));

            watcher.startWatching("p1", project1.toString());
            watcher.startWatching("p2", project2.toString());

            assertThat(watcher.isWatching("p1")).isTrue();
            assertThat(watcher.isWatching("p2")).isTrue();

            watcher.stopWatching("p1");

            assertThat(watcher.isWatching("p1")).isFalse();
            assertThat(watcher.isWatching("p2")).isTrue();

            watcher.stopWatching("p2");
        }
    }

    @Nested
    @DisplayName("onFileChange")
    class OnFileChange {

        @Test
        @DisplayName("accepts handler registration without error and ignores null")
        void registersHandler() {
            watcher.onFileChange(event -> { });
            watcher.onFileChange(null);

            // Registration alone changes no watching state.
            assertThat(watcher.isWatching("p1")).isFalse();
        }
    }
}
