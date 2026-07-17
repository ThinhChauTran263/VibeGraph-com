package com.vibegraph.common.exception;

/** Signals that the current account has been deactivated. */
public class AccountDeactivatedException extends AccountBlockedException {

    public AccountDeactivatedException(String message, String safeReason) {
        super(message, safeReason);
    }

    @Override
    public String getCode() {
        return "ACCOUNT_DEACTIVATED";
    }
}
