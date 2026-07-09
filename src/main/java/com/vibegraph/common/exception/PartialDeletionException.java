package com.vibegraph.common.exception;

/**
 * A project delete succeeded on one plane but failed on another, leaving inconsistent state that
 * needs manual/retry cleanup. Mapped to HTTP 500 {@code DELETE_PARTIAL_FAILED} by
 * {@link GlobalExceptionHandler}.
 *
 * <p>The public {@code DELETE} must NEVER report 204 in this case — the surviving plane is
 * preserved for retry and the failure is logged with {@code (projectId, userId, plane)}.
 */
public class PartialDeletionException extends RuntimeException {

    /** The plane that failed to delete (e.g. {@code CONTROL_PLANE}). */
    private final String failedPlane;

    public PartialDeletionException(String projectId, String failedPlane, Throwable cause) {
        super("Partial deletion for project '" + projectId + "': " + failedPlane + " delete failed", cause);
        this.failedPlane = failedPlane;
    }

    public String getFailedPlane() {
        return failedPlane;
    }
}
