package com.vibegraph.common.exception;

public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }

    public String getCode() {
        return "QUOTA_EXCEEDED";
    }
}
