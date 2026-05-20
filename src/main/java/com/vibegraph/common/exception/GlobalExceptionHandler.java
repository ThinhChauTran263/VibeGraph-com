package com.vibegraph.common.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Global exception handler.
 * Catches all unhandled exceptions and returns standardized ErrorResponse.
 *
 * TODO:
 * - Handle ProjectNotFoundException → 404
 * - Handle ParseException → 422
 * - Handle NodeNotFoundException → 404
 * - Handle generic Exception → 500
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    // TODO: Implement exception handlers
}
