package com.vibegraph.patch.exception;

/**
 * Thrown when a local-patch request is rejected during validation (unsafe path, blocked file,
 * binary content, size/count limit, bad encoding, ...).
 *
 * <p>Validation is fail-fast: this is raised <em>before</em> any file is written or deleted, so a
 * rejected request never leaves partially-applied changes on disk. {@code PatchExceptionHandler}
 * maps it to HTTP 400 with the stable code {@code PATCH_REJECTED}.
 *
 * <p>The message and {@link #getReason()} must never contain file content, secrets, base64, or the
 * caller's JWT — only a category and the offending (already user-supplied) relative path.
 */
public class PatchRejectedException extends RuntimeException {

    /** Stable rejection categories surfaced as {@code error.details}. */
    public enum Reason {
        BLANK_PATH,
        PATH_TOO_LONG,
        INVALID_PATH,
        BACKSLASH_PATH,
        ABSOLUTE_PATH,
        DRIVE_PATH,
        PATH_TRAVERSAL,
        PATH_ESCAPE,
        SYMLINK_ESCAPE,
        BLOCKED_DIRECTORY,
        BLOCKED_FILE,
        ARCHIVE_NOT_ALLOWED,
        BINARY_CONTENT,
        NOT_JAVA_SOURCE,
        MISSING_CONTENT,
        UNSUPPORTED_ENCODING,
        INVALID_BASE64,
        FILE_TOO_LARGE,
        TOTAL_TOO_LARGE,
        TOO_MANY_FILES,
        DUPLICATE_PATH,
        OVERLAPPING_PATH
    }

    private final transient Reason reason;

    public PatchRejectedException(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
