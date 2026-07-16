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
}
