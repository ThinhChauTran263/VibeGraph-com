package com.vibegraph.common.exception;

public class ConcurrentImportLimitException extends RuntimeException {

    public ConcurrentImportLimitException(String message) {
        super(message);
    }

    public String getCode() {
        return "CONCURRENT_IMPORT_LIMIT";
    }
}
