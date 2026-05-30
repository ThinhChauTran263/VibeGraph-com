package com.vibegraph.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * File utility helpers.
 */
public final class FileUtils {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            "target", "build", ".git", ".idea", ".gradle", "node_modules", "out", "bin"
    );

    private FileUtils() {}

    /**
     * Recursively scan a directory and return all .java files,
     * ignoring build/target/.git directories.
     */
    public static List<Path> scanJavaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(FileUtils::isJavaFile)
                    .filter(FileUtils::isNotInIgnoredDirectory)
                    .toList();
        }
    }

    /**
     * Returns true if the path ends with .java extension.
     */
    public static boolean isJavaFile(Path path) {
        if (path == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java");
    }

    private static boolean isNotInIgnoredDirectory(Path path) {
        for (Path part : path) {
            if (IGNORED_DIRECTORIES.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }
}
