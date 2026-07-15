package com.vibegraph.common.exception;

public class ApiKeyPlanLimitReachedException extends RuntimeException {

    public ApiKeyPlanLimitReachedException(String message) {
        super(message);
    }

    public String getCode() {
        return "API_KEY_PLAN_LIMIT_REACHED";
    }
}
