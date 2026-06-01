package com.vibegraph.graph.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.common.exception.ArchiveImportException;

@DisplayName("ArchiveTypeDetector")
class ArchiveTypeDetectorTest {

    @Test
    @DisplayName("detects .zip / .tar / .tar.gz / .tgz, case-insensitively")
    void detectsSupportedTypes() {
        assertThat(ArchiveTypeDetector.detect("project.zip")).isEqualTo(ArchiveType.ZIP);
        assertThat(ArchiveTypeDetector.detect("project.tar")).isEqualTo(ArchiveType.TAR);
        assertThat(ArchiveTypeDetector.detect("project.tar.gz")).isEqualTo(ArchiveType.TAR_GZ);
        assertThat(ArchiveTypeDetector.detect("project.tgz")).isEqualTo(ArchiveType.TAR_GZ);
        assertThat(ArchiveTypeDetector.detect("PROJECT.TAR.GZ")).isEqualTo(ArchiveType.TAR_GZ);
        assertThat(ArchiveTypeDetector.detect("Project.Zip")).isEqualTo(ArchiveType.ZIP);
    }

    @Test
    @DisplayName("rejects null and blank filenames as UNSUPPORTED_TYPE")
    void rejectsNullOrBlank() {
        assertThatThrownBy(() -> ArchiveTypeDetector.detect(null))
                .isInstanceOf(ArchiveImportException.class)
                .satisfies(ex -> assertThat(((ArchiveImportException) ex).getReason())
                        .isEqualTo(ArchiveImportException.Reason.UNSUPPORTED_TYPE));

        assertThatThrownBy(() -> ArchiveTypeDetector.detect("   "))
                .isInstanceOf(ArchiveImportException.class);
    }

    @Test
    @DisplayName("rejects unsupported extensions - decision is by extension, not Content-Type")
    void rejectsUnsupportedExtensions() {
        // A client could lie via Content-Type (e.g. application/zip); detection is filename-based,
        // so any non-archive extension is still rejected. ".gz" alone (not ".tar.gz") is not allowed.
        for (String bad : new String[] {"project.rar", "evil.exe", "notes.txt", "archive.gz",
                "project.zip.exe", "noextension"}) {
            assertThatThrownBy(() -> ArchiveTypeDetector.detect(bad))
                    .as("should reject %s", bad)
                    .isInstanceOf(ArchiveImportException.class)
                    .satisfies(ex -> assertThat(((ArchiveImportException) ex).getCode())
                            .isEqualTo("ARCHIVE_UNSUPPORTED_TYPE"));
        }
    }
}
