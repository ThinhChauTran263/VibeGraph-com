package com.vibegraph.auth.service;

public final class StorageUnitConverter {

    public static final long BYTES_PER_MB = 1_048_576L;

    private StorageUnitConverter() {
    }

    public static long mbToBytes(long megabytes) {
        if (megabytes < 0) {
            throw new IllegalArgumentException("Storage quota must be non-negative");
        }
        try {
            return Math.multiplyExact(megabytes, BYTES_PER_MB);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Storage quota is too large", ex);
        }
    }

    public static long bytesToUsedMb(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Storage usage must be non-negative");
        }
        return bytes == 0 ? 0 : Math.floorDiv(bytes - 1, BYTES_PER_MB) + 1;
    }

    public static long bytesToAvailableMb(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Storage quota must be non-negative");
        }
        return bytes / BYTES_PER_MB;
    }

    /**
     * Compact human-readable size with one decimal fraction, e.g. {@code "512 B"},
     * {@code "850.8 KB"}, {@code "3.9 MB"}, {@code "1.2 GB"}. Used in quota error
     * messages so users see the exact footprint instead of a rounded MB integer.
     */
    public static String humanReadable(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Size must be non-negative");
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0);
    }
}
