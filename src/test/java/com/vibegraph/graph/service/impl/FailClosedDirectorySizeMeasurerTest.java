package com.vibegraph.graph.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalImport fail-closed directory size measurer")
class FailClosedDirectorySizeMeasurerTest {

    @Test
    @DisplayName("sums regular file sizes with overflow-safe arithmetic")
    void measureBytes_regularFiles_sumsSizes() {
        FailClosedDirectorySizeMeasurer measurer = new FailClosedDirectorySizeMeasurer((root, visitor) -> {
            visitor.visitFile(root.resolve("a"), regularFile(40));
            visitor.visitFile(root.resolve("b"), regularFile(2));
        });

        assertThat(measurer.measureBytes(Path.of("root"))).isEqualTo(42);
    }

    @Test
    @DisplayName("I/O failure is rejected instead of undercounting")
    void measureBytes_ioFailure_failsClosed() {
        FailClosedDirectorySizeMeasurer measurer = new FailClosedDirectorySizeMeasurer((root, visitor) ->
                visitor.visitFileFailed(root.resolve("denied"), new AccessDeniedException("denied")));

        assertThatThrownBy(() -> measurer.measureBytes(Path.of("root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Directory size could not be measured safely");
    }

    @Test
    @DisplayName("arithmetic overflow is rejected instead of wrapping negative")
    void measureBytes_overflow_failsClosed() {
        FailClosedDirectorySizeMeasurer measurer = new FailClosedDirectorySizeMeasurer((root, visitor) -> {
            visitor.visitFile(root.resolve("huge"), regularFile(Long.MAX_VALUE));
            visitor.visitFile(root.resolve("one-more"), regularFile(1));
        });

        assertThatThrownBy(() -> measurer.measureBytes(Path.of("root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Directory size could not be measured safely");
    }

    @Test
    @DisplayName("symbolic-link entry is rejected without following or undercounting")
    void measureBytes_symlink_failsClosed() {
        BasicFileAttributes symlink = mock(BasicFileAttributes.class);
        when(symlink.isSymbolicLink()).thenReturn(true);
        FailClosedDirectorySizeMeasurer measurer = new FailClosedDirectorySizeMeasurer((root, visitor) ->
                visitor.visitFile(root.resolve("link"), symlink));

        assertThatThrownBy(() -> measurer.measureBytes(Path.of("root")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Symbolic links");
    }

    @Test
    @DisplayName("post-visit directory failure is rejected")
    void measureBytes_postVisitFailure_failsClosed() {
        FailClosedDirectorySizeMeasurer measurer = new FailClosedDirectorySizeMeasurer((root, visitor) ->
                visitor.postVisitDirectory(root, new IOException("walk failed")));

        assertThatThrownBy(() -> measurer.measureBytes(Path.of("root")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BasicFileAttributes regularFile(long size) {
        BasicFileAttributes attributes = mock(BasicFileAttributes.class);
        when(attributes.isRegularFile()).thenReturn(true);
        when(attributes.size()).thenReturn(size);
        return attributes;
    }
}
