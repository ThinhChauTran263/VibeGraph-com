package com.vibegraph.common.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Hash utilities for incremental parse cache.
 *
 * TODO:
 * - sha256(String content) → hex string
 * - sha256File(Path path)
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * Compute SHA-256 of string content as lowercase hex.
     *
     * TODO: Implement using MessageDigest.
     */
    public static String sha256(String content) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Compute SHA-256 of file content as lowercase hex.
     *
     * TODO: Implement using MessageDigest + Files.readAllBytes.
     */
    public static String sha256(Path path) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
