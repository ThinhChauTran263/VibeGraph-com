package com.vibegraph.auth.dto;

import java.util.List;

public record AdminStorageOverviewResponse(
        long trackedProjectUsageBytes,
        List<StorageMountResponse> mounts,
        StorageUnknownResponse database,
        StorageUnknownResponse neo4j) {
}
