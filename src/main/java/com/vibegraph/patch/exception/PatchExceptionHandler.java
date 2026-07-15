package com.vibegraph.patch.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

/**
 * Maps {@link PatchRejectedException} to a {@code 400 PATCH_REJECTED} response in the standard
 * {@link ApiResponse} envelope.
 *
 * <p>Kept as a dedicated advice (highest precedence) so it does not touch the shared
 * {@code GlobalExceptionHandler}. The response carries only the rejection category and the
 * offending relative path — never file content, base64, secrets, or the caller's JWT.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PatchExceptionHandler {

    @ExceptionHandler(PatchRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePatchRejected(PatchRejectedException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("PATCH_REJECTED")
                .message("Patch request rejected")
                .details(ex.getReason() + ": " + ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }
}
