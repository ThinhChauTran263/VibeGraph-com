package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.IpBlock;

import java.time.Instant;
import java.util.UUID;

public record IpBlockResponse(
        UUID id,
        String ipAddress,
        String safeReason,
        Instant expiresAt,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        boolean active) {

    public static IpBlockResponse from(IpBlock block) {
        return new IpBlockResponse(block.getId(), block.getIpAddress(), block.getSafeReason(),
                block.getExpiresAt(), block.getCreatedBy(), block.getCreatedAt(), block.getUpdatedAt(), block.isActive());
    }
}
