package com.vibegraph.graph.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

/**
 * Counts regular {@code .java} files under a project root. Re-analysis bills
 * by this count, mirroring the file-count pricing imports pay at upload time.
 */
@Slf4j
public final class JavaFileCounter {

    private JavaFileCounter() {
    }

    /**
     * Counts {@code .java} files recursively under {@code root}. Returns 0 for
     * a missing root or when the walk fails, so a stale path degrades to the
     * rule's base charge instead of breaking the analyze request.
     */
    public static int count(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return (int) paths
                    .filter(path -> path.getFileName() != null
                            && path.getFileName().toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException | SecurityException ex) {
            log.warn("Could not count .java files under {}: {}", root, ex.getMessage());
            return 0;
        }
    }
}
