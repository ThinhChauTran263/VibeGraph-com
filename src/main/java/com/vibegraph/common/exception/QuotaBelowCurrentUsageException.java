package com.vibegraph.common.exception;

public class QuotaBelowCurrentUsageException extends RuntimeException {

    private final long currentUsageBytes;
    private final long requestedQuotaBytes;

    public QuotaBelowCurrentUsageException(long currentUsageBytes, long requestedQuotaBytes) {
        super("Requested quota is lower than current storage usage");
        this.currentUsageBytes = currentUsageBytes;
        this.requestedQuotaBytes = requestedQuotaBytes;
    }

    public String getCode() {
        return "QUOTA_BELOW_CURRENT_USAGE";
    }

    public long getCurrentUsageBytes() {
        return currentUsageBytes;
    }

    public long getRequestedQuotaBytes() {
        return requestedQuotaBytes;
    }
}
