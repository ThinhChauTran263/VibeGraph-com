package com.vibegraph.common.exception;

public class AccountBlockedException extends RuntimeException {

    private final String safeReason;

    public AccountBlockedException(String message, String safeReason) {
        super(message);
        this.safeReason = safeReason;
    }

    public String getCode() {
        return "ACCOUNT_BLOCKED";
    }

    public String getSafeReason() {
        return safeReason;
    }
}
