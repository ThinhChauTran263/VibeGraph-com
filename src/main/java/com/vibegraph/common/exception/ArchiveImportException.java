package com.vibegraph.common.exception;

/**
 * Thrown for user-correctable failures of the archive-upload onboarding flow
 * (POST /api/projects/import-archive). Carries a {@link Reason} so the API returns a
 * stable, clear error code; {@link GlobalExceptionHandler} maps it to HTTP 400.
 */
public class ArchiveImportException extends RuntimeException {

    /** Stable categories of archive-import failure - the basis of the API error code. */
    public enum Reason {
        MISSING_FILE,
        BLANK_NAME,
        UNSUPPORTED_TYPE,
        OVERSIZE,
        UNSAFE_ENTRY,
        EMPTY_ARCHIVE,
        EXTRACTION_FAILED
    }

    private final Reason reason;

    public ArchiveImportException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** Stable API error code, e.g. {@code ARCHIVE_UNSUPPORTED_TYPE}. */
    public String getCode() {
        return "ARCHIVE_" + reason.name();
    }
}
