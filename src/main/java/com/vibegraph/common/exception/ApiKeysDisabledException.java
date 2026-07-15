package com.vibegraph.common.exception;

public class ApiKeysDisabledException extends RuntimeException {

    public ApiKeysDisabledException(String message) {
        super(message);
    }

    public String getCode() {
        return "API_KEYS_DISABLED";
    }
}
