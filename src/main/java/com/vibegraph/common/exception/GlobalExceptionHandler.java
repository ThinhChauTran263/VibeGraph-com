package com.vibegraph.common.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNodeNotFound(NodeNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("NODE_NOT_FOUND")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        // Registration with an email that already exists (case-insensitive) — 409.
        ErrorResponse error = ErrorResponse.builder()
                .code("EMAIL_TAKEN")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        // Unknown email or wrong password — generic 401, no user enumeration.
        ErrorResponse error = ErrorResponse.builder()
                .code("INVALID_CREDENTIALS")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        // No resolvable authenticated user at the service layer — 401.
        ErrorResponse error = ErrorResponse.builder()
                .code("UNAUTHORIZED")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(ProjectNotAnalyzedException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotAnalyzed(ProjectNotAnalyzedException ex) {
        // Project exists but its graph has not been built yet — surface a clear 409 so
        // diagram clients don't mistake an empty result for a real (empty) diagram.
        ErrorResponse error = ErrorResponse.builder()
                .code("PROJECT_NOT_ANALYZED")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
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

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterError(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("BAD_REQUEST")
                .message("Request parameters are invalid")
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        // A precondition/feature-state failure the caller can act on (e.g. directory browsing
        // disabled because no allowed-root is configured). Surface the real reason as a 409
        // instead of a generic 500 so the client can show actionable guidance.
        ErrorResponse error = ErrorResponse.builder()
                .code("PRECONDITION_FAILED")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
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

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceBusy(ServiceBusyException ex) {
        // The analysis executor is saturated; the project was marked FAILED before this was thrown.
        // 503 tells the client to retry later instead of blocking the request thread on analysis.
        ErrorResponse error = ErrorResponse.builder()
                .code("SERVICE_BUSY")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(error));
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
