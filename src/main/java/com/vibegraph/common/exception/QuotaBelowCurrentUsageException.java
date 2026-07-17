package com.vibegraph.common.exception;

public class QuotaBelowCurrentUsageException extends RuntimeException {

    private static final long BYTES_PER_MB = 1_048_576L;

    private final long currentUsageMb;
    private final long requestedQuotaMb;

    public QuotaBelowCurrentUsageException(long currentUsageBytes, long requestedQuotaBytes) {
        super("Requested quota is lower than current storage usage");
        this.currentUsageMb = currentUsageBytes == 0
                ? 0
                : Math.floorDiv(currentUsageBytes - 1, BYTES_PER_MB) + 1;
        this.requestedQuotaMb = requestedQuotaBytes / BYTES_PER_MB;
    }

    public String getCode() {
        return "QUOTA_BELOW_CURRENT_USAGE";
    }

    public long getCurrentUsageMb() {
        return currentUsageMb;
    }

    public long getRequestedQuotaMb() {
        return requestedQuotaMb;
    }
}
