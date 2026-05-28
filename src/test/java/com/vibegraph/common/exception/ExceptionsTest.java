package com.vibegraph.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
