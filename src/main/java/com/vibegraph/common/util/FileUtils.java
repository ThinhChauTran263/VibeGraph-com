package com.vibegraph.common.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * File utility helpers.
 *
 * TODO:
 * - listJavaFiles(directory) → recursive scan
 * - readFile(path) → String
 * - normalizeProjectPath(path)
 */
public final class FileUtils {

    private FileUtils() {}

    /**
     * Recursively scan a directory and return all .java files,
     * ignoring build/target/.git directories.
     *
     * TODO: Implement using Files.walk + filter.
     */
    public static List<Path> scanJavaFiles(Path root) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Returns true if the path ends with .java extension.
     *
     * TODO: Implement extension check.
     */
    public static boolean isJavaFile(Path path) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
