package com.vibegraph.common.exception;

public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException(String message) {
        super(message);
    }

    public String getCode() {
        return "CREDIT_EXHAUSTED";
    }
}
