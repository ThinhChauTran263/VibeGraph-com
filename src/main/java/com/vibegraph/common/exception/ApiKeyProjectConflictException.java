package com.vibegraph.common.exception;

public class ApiKeyProjectConflictException extends ApiKeyPlanLimitReachedException {

    public ApiKeyProjectConflictException(String message) {
        super(message);
    }

    @Override
    public String getCode() {
        return "API_KEY_PROJECT_CONFLICT";
    }
}
