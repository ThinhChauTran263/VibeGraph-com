package com.vibegraph.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hash utilities for incremental parse cache.
 */
public final class HashUtils {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private HashUtils() {}

    /**
     * Compute SHA-256 of string content as lowercase hex.
     */
    public static String sha256(String content) {
        Objects.requireNonNull(content, "content must not be null");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return sha256Bytes(bytes);
    }

    /**
     * Compute SHA-256 of file content as lowercase hex.
     */
    public static String sha256(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return sha256Bytes(bytes);
    }

    private static String sha256Bytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HEX_FORMAT.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
