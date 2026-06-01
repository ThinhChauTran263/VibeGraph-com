package com.vibegraph.common.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.common.dto.response.ErrorResponse;

/**
 * Global exception handler — maps exceptions to standardized ApiResponse errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotFound(ProjectNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("PROJECT_NOT_FOUND")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(details)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("BAD_REQUEST")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(GithubImportException.class)
    public ResponseEntity<ApiResponse<Void>> handleGithubImport(GithubImportException ex) {
        // Domain failure of the import (private/oversize/non-existent repo, bad URL,
        // timeout) — surface the real reason as a 422 instead of a generic 500.
        ErrorResponse error = ErrorResponse.builder()
                .code("GITHUB_IMPORT_ERROR")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(error));
    }

    @ExceptionHandler(FeatureNotImplementedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotImplemented(FeatureNotImplementedException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("NOT_IMPLEMENTED")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(ArchiveImportException.class)
    public ResponseEntity<ApiResponse<Void>> handleArchiveImport(ArchiveImportException ex) {
        // User-correctable archive-upload failure (unsupported type, oversize, unsafe entry,
        // empty archive, ...). Surface the stable reason code instead of a generic 500.
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // Spring rejects oversized multipart uploads before the controller runs — map to 413
        // so the archive-upload client gets a clear "too large" instead of a generic 500.
        ErrorResponse error = ErrorResponse.builder()
                .code("ARCHIVE_OVERSIZE")
                .message("Uploaded archive exceeds the maximum allowed size")
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }
}
