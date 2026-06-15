package com.vibegraph.graph.importer;

import java.util.Locale;

import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;

/**
 * Detects the {@link ArchiveType} of an uploaded archive from its filename extension.
 *
 * <p>Detection is by filename extension only. The HTTP {@code Content-Type} header is
 * client-controlled and unreliable, so it is intentionally NOT consulted when deciding
 * whether an upload is accepted. Pass the (display-only) original filename here, never a
 * filesystem path.
 */
public final class ArchiveTypeDetector {

    private ArchiveTypeDetector() {
    }

    /**
     * @param originalFilename the uploaded file's display name (e.g. {@code project.tar.gz})
     * @return the detected archive type
     * @throws ArchiveImportException with {@link Reason#UNSUPPORTED_TYPE} when the name is
     *         null, blank, or not one of {@code .zip}, {@code .tar}, {@code .tar.gz}, {@code .tgz}
     */
    public static ArchiveType detect(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ArchiveImportException(Reason.UNSUPPORTED_TYPE, "Archive filename is missing");
        }
        String name = originalFilename.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return ArchiveType.TAR_GZ;
        }
        if (name.endsWith(".tar")) {
            return ArchiveType.TAR;
        }
        if (name.endsWith(".zip")) {
            return ArchiveType.ZIP;
        }
        throw new ArchiveImportException(Reason.UNSUPPORTED_TYPE,
                "Unsupported archive type '" + originalFilename + "' (allowed: .zip, .tar, .tar.gz, .tgz)");
    }
}
