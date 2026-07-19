package com.vibegraph.common.exception;

public class ApiKeyAdminLockedException extends ApiKeysDisabledException {

    public ApiKeyAdminLockedException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "API_KEY_ADMIN_LOCKED";
    }
}
