package com.vibegraph.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.vibegraph.common.dto.response.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom exception classes.
 *
 * Run: mvn test -Dtest=ExceptionsTest
 */
@DisplayName("Custom Exceptions")
class ExceptionsTest {

    @Test
    @DisplayName("ProjectNotFoundException should carry message")
    void projectNotFoundExceptionShouldCarryMessage() {
        ProjectNotFoundException ex = new ProjectNotFoundException("Project xyz not found");
        assertEquals("Project xyz not found", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    @DisplayName("ParseException should carry message and cause")
    void parseExceptionShouldCarryMessageAndCause() {
        Exception cause = new RuntimeException("root cause");
        ParseException ex = new ParseException("Parse failed", cause);
        assertEquals("Parse failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("NodeNotFoundException should carry message")
    void nodeNotFoundExceptionShouldCarryMessage() {
        NodeNotFoundException ex = new NodeNotFoundException("Node 123 not found");
        assertEquals("Node 123 not found", ex.getMessage());
    }

    @Test
    @DisplayName("GithubImportException should carry message")
    void githubImportExceptionShouldCarryMessage() {
        GithubImportException ex = new GithubImportException("Repo too large");
        assertEquals("Repo too large", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    @DisplayName("GithubImportException should carry message and cause")
    void githubImportExceptionShouldCarryMessageAndCause() {
        Exception cause = new RuntimeException("network timeout");
        GithubImportException ex = new GithubImportException("Tarball download failed", cause);
        assertEquals("Tarball download failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps generic exception to typed INTERNAL_ERROR 500")
    void globalHandlerMapsGenericExceptionToInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneric(new RuntimeException("boom — DB connection lost"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertNotNull(body.getError());
        assertEquals("INTERNAL_ERROR", body.getError().getCode());
        // Internal detail must not leak to the client.
        assertEquals("An unexpected error occurred", body.getError().getMessage());
        assertNull(body.getData());
    }

    @Test
    @DisplayName("ArchiveImportException carries a stable reason and code")
    void archiveImportExceptionCarriesReasonAndCode() {
        ArchiveImportException ex = new ArchiveImportException(
                ArchiveImportException.Reason.UNSUPPORTED_TYPE, "bad type");
        assertEquals("bad type", ex.getMessage());
        assertEquals(ArchiveImportException.Reason.UNSUPPORTED_TYPE, ex.getReason());
        assertEquals("ARCHIVE_UNSUPPORTED_TYPE", ex.getCode());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps ArchiveImportException to 400 with its code")
    void globalHandlerMapsArchiveImportToBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleArchiveImport(
                new ArchiveImportException(ArchiveImportException.Reason.UNSAFE_ENTRY, "unsafe entry"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("ARCHIVE_UNSAFE_ENTRY", body.getError().getCode());
        assertEquals("unsafe entry", body.getError().getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps MaxUploadSizeExceededException to 413")
    void globalHandlerMapsMaxUploadSizeToPayloadTooLarge() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(100L));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("ARCHIVE_OVERSIZE", body.getError().getCode());
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps AccountBlockedException to 403")
    void globalHandlerMapsAccountBlockedToForbidden() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccountBlocked(new AccountBlockedException("Account is blocked", "policy violation"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("ACCOUNT_BLOCKED", body.getError().getCode());
        assertEquals("policy violation", body.getError().getMessage());
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps quota and API key contract errors")
    void globalHandlerMapsPhase4ContractErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertError(handler.handleQuotaExceeded(new QuotaExceededException("storage quota exceeded")),
                HttpStatus.PAYLOAD_TOO_LARGE, "QUOTA_EXCEEDED", "storage quota exceeded");
        assertError(handler.handleApiKeysDisabled(new ApiKeysDisabledException("API key creation is disabled")),
                HttpStatus.FORBIDDEN, "API_KEYS_DISABLED", "API key creation is disabled");
        assertError(handler.handleApiKeyPlanLimitReached(
                        new ApiKeyPlanLimitReachedException("API key plan limit reached")),
                HttpStatus.CONFLICT, "API_KEY_PLAN_LIMIT_REACHED", "API key plan limit reached");
        assertError(handler.handleFeatureDisabled(new FeatureDisabledException("global.cli_push")),
                HttpStatus.FORBIDDEN, "FEATURE_DISABLED", "Feature is currently disabled: global.cli_push");
        ResponseEntity<ApiResponse<Void>> belowUsage = handler.handleQuotaBelowCurrentUsage(
                new QuotaBelowCurrentUsageException(2_000L, 1_000L));
        assertError(belowUsage, HttpStatus.BAD_REQUEST, "QUOTA_BELOW_CURRENT_USAGE",
                "Requested quota is lower than current storage usage");
        assertNotNull(belowUsage.getBody());
        assertEquals("currentUsageBytes=2000; requestedQuotaBytes=1000",
                belowUsage.getBody().getError().getDetails());
    }

    private void assertError(ResponseEntity<ApiResponse<Void>> response, HttpStatus status,
                             String code, String message) {
        assertEquals(status, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals(code, body.getError().getCode());
        assertEquals(message, body.getError().getMessage());
    }
}
