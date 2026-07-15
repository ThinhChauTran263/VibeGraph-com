package com.vibegraph.auth.dto;

public record StorageMountResponse(
        String label,
        String source,
        String path,
        boolean available,
        Long totalBytes,
        Long usableBytes,
        Long usedBytes,
        String status) {
}
