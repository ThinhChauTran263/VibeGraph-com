package com.vibegraph.common.exception;

/**
 * Thrown when the bounded analysis executor rejects a new import because it is saturated
 * (all threads busy and the queue is full).
 *
 * <p>Maps to HTTP 503 so the client gets a clear "server busy, retry later" instead of having the
 * request thread blocked running the analysis itself. The project is marked FAILED before this is
 * thrown so its state is consistent.
 */
public class ServiceBusyException extends RuntimeException {
    public ServiceBusyException(String message) {
        super(message);
    }
}
