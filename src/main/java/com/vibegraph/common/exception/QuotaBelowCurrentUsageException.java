package com.vibegraph.common.exception;

public class QuotaBelowCurrentUsageException extends RuntimeException {

    public QuotaBelowCurrentUsageException(String message) {
        super(message);
    }

    public String getCode() {
        return "QUOTA_BELOW_CURRENT_USAGE";
    }
}
