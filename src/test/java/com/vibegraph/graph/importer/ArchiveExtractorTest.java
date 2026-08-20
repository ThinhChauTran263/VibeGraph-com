package com.vibegraph.graph.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

/**
 * TAR fixtures are written with a minimal in-test ustar writer rather than
 * commons-compress {@code TarArchiveOutputStream}: that writer references the optional
 * {@code commons-codec} dependency which is not on the classpath. The extractor's TAR
 * *read* path ({@code TarArchiveInputStream}) does not need it, so this keeps the tests
 * hermetic without a build change.
 */
@DisplayName("ArchiveExtractor")
class ArchiveExtractorTest {

    private static final byte TYPE_FILE = '0';
    private static final byte TYPE_SYMLINK = '2';

    @TempDir
    Path tempDir;

    private ArchiveImportProperties properties;
    private ArchiveExtractor extractor;

    @BeforeEach
    void setUp() {
        properties = new ArchiveImportProperties();
        extractor = new ArchiveExtractor(properties);
    }

    private Path dest() {
        return tempDir.resolve("workspace");
    }

    // ----------------------------- ZIP -----------------------------

    @Test
    @DisplayName("extracts .java files from a valid ZIP and preserves relative paths")
    void zipWithJavaFiles_extracted() throws IOException {
        Path zip = writeZip("project.zip", entries(
                "src/main/java/com/demo/App.java", "package com.demo; class App {}",
                "README.md", "not java"));

        ArchiveExtractionResult result = extractor.extract(zip, ArchiveType.ZIP, dest());

        assertThat(result.extractedRoot()).isEqualTo(dest().toAbsolutePath().normalize());
        assertThat(result.relativeJavaPaths()).containsExactly("src/main/java/com/demo/App.java");
        assertThat(result.javaFiles()).hasSize(1);
        assertThat(Files.readString(result.javaFiles().get(0))).contains("class App");
    }

    @Test
    @DisplayName("rejects a ZIP path-traversal entry (../evil.java)")
    void zipPathTraversal_rejected() throws IOException {
        Path zip = writeZip("evil.zip", entries("../evil.java", "class Evil {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.UNSAFE_ENTRY);
    }

    @Test
    @DisplayName("rejects a ZIP absolute-path entry")
    void zipAbsolutePath_rejected() throws IOException {
        Path zip = writeZip("evil.zip", entries("/abs/Evil.java", "class Evil {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.UNSAFE_ENTRY);
    }

    @Test
    @DisplayName("rejects a ZIP Windows drive-path entry (C:\\evil.java)")
    void zipWindowsDrivePath_rejected() throws IOException {
        Path zip = writeZip("evil.zip", entries("C:\\evil.java", "class Evil {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.UNSAFE_ENTRY);
    }

    @Test
    @DisplayName("a ZIP with no .java files yields EMPTY_ARCHIVE")
    void zipWithoutJava_emptyArchive() throws IOException {
        Path zip = writeZip("docs.zip", entries("README.md", "x", "build.gradle", "y"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.EMPTY_ARCHIVE);
    }

    @Test
    @DisplayName("skips .java files under ignored directories (e.g. target/)")
    void zipIgnoredDirectory_skipped() throws IOException {
        Path zip = writeZip("project.zip", entries(
                "target/Generated.java", "class Generated {}",
                "src/Real.java", "class Real {}"));

        ArchiveExtractionResult result = extractor.extract(zip, ArchiveType.ZIP, dest());

        assertThat(result.relativeJavaPaths()).containsExactly("src/Real.java");
    }

    // ----------------------------- TAR / TAR.GZ -----------------------------

    @Test
    @DisplayName("extracts .java files from a valid TAR")
    void tarWithJavaFiles_extracted() throws IOException {
        Path tar = writeTar("project.tar", false, entries(
                "src/A.java", "class A {}",
                "notes.txt", "ignore"));

        ArchiveExtractionResult result = extractor.extract(tar, ArchiveType.TAR, dest());

        assertThat(result.relativeJavaPaths()).containsExactly("src/A.java");
        assertThat(Files.readString(result.javaFiles().get(0))).contains("class A");
    }

    @Test
    @DisplayName("rejects a TAR symlink entry (policy: reject, not follow)")
    void tarSymlinkEntry_rejected() throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        writeUstarEntry(tar, "src/A.java", TYPE_FILE, null, "class A {}".getBytes(StandardCharsets.UTF_8));
        writeUstarEntry(tar, "src/link.java", TYPE_SYMLINK, "/etc/passwd", new byte[0]);
        tar.write(new byte[1024]); // end-of-archive marker
        Path path = tempDir.resolve("evil.tar");
        Files.write(path, tar.toByteArray());

        assertReason(() -> extractor.extract(path, ArchiveType.TAR, dest()), Reason.UNSAFE_ENTRY);
    }

    @Test
    @DisplayName("extracts .java files from a valid TAR.GZ")
    void tarGzWithJavaFiles_extracted() throws IOException {
        Path tgz = writeTar("project.tar.gz", true, entries("src/B.java", "class B {}"));

        ArchiveExtractionResult result = extractor.extract(tgz, ArchiveType.TAR_GZ, dest());

        assertThat(result.relativeJavaPaths()).containsExactly("src/B.java");
        assertThat(Files.readString(result.javaFiles().get(0))).contains("class B");
    }

    // ----------------------------- size / IO -----------------------------

    @Test
    @DisplayName("rejects an archive larger than the configured max size")
    void archiveExceedingMaxSize_oversize() throws IOException {
        properties.setMaxSize(DataSize.ofBytes(5));
        Path zip = writeZip("project.zip", entries("src/A.java", "class A {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.OVERSIZE);
    }

    @Test
    @DisplayName("counts decompressed bytes of skipped non-.java entries (F9 zip-bomb guard)")
    void zipBombHiddenInNonJavaEntry_oversize() throws IOException {
        properties.setMaxSize(DataSize.ofKilobytes(1));
        Path zip = writeZip("bomb.zip", entries(
                "big.bin", "x".repeat(4096),
                "src/A.java", "class A {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.OVERSIZE);
    }

    @Test
    @DisplayName("counts decompressed bytes of entries under ignored directories (F9 zip-bomb guard)")
    void zipBombHiddenInIgnoredDirectory_oversize() throws IOException {
        properties.setMaxSize(DataSize.ofKilobytes(1));
        Path zip = writeZip("bomb.zip", entries(
                "target/blob.java", "x".repeat(4096),
                "src/A.java", "class A {}"));
        assertReason(() -> extractor.extract(zip, ArchiveType.ZIP, dest()), Reason.OVERSIZE);
    }

    @Test
    @DisplayName("a missing/unreadable archive maps to EXTRACTION_FAILED")
    void missingArchive_extractionFailed() {
        Path missing = tempDir.resolve("does-not-exist.zip");
        assertReason(() -> extractor.extract(missing, ArchiveType.ZIP, dest()), Reason.EXTRACTION_FAILED);
    }

    // ----------------------------- helpers -----------------------------

    private void assertReason(ThrowingCallable call, Reason expected) {
        assertThatThrownBy(call)
                .isInstanceOf(ArchiveImportException.class)
                .satisfies(e -> assertThat(((ArchiveImportException) e).getReason()).isEqualTo(expected));
    }

    private Map<String, String> entries(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private Path writeZip(String name, Map<String, String> entries) throws IOException {
        Path zip = tempDir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }

    private Path writeTar(String name, boolean gzip, Map<String, String> entries) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            writeUstarEntry(tar, e.getKey(), TYPE_FILE, null, e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        tar.write(new byte[1024]); // two zero blocks = end-of-archive
        Path path = tempDir.resolve(name);
        try (OutputStream out = gzip
                ? new GZIPOutputStream(Files.newOutputStream(path))
                : Files.newOutputStream(path)) {
            out.write(tar.toByteArray());
        }
        return path;
    }

    /** Writes one 512-byte POSIX ustar header (+ padded data) - enough for TarArchiveInputStream to read. */
    private void writeUstarEntry(ByteArrayOutputStream out, String name, byte typeFlag, String linkName, byte[] data)
            throws IOException {
        byte[] h = new byte[512];
        putString(h, 0, 100, name);
        putOctal(h, 100, 8, 0644);          // mode
        putOctal(h, 108, 8, 0);             // uid
        putOctal(h, 116, 8, 0);             // gid
        putOctal(h, 124, 12, data.length);  // size
        putOctal(h, 136, 12, 0);            // mtime
        for (int i = 148; i < 156; i++) {   // checksum field as spaces while summing
            h[i] = ' ';
        }
        h[156] = typeFlag;
        if (linkName != null) {
            putString(h, 157, 100, linkName);
        }
        putString(h, 257, 6, "ustar");      // magic "ustar\0"
        h[263] = '0';
        h[264] = '0';                       // version "00"
        int checksum = 0;
        for (byte b : h) {
            checksum += (b & 0xFF);
        }
        byte[] chk = String.format("%06o", checksum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(chk, 0, h, 148, 6);
        h[154] = 0;
        h[155] = ' ';
        out.write(h);
        if (data.length > 0) {
            out.write(data);
            int pad = (512 - (data.length % 512)) % 512;
            out.write(new byte[pad]);
        }
    }

    private void putString(byte[] header, int offset, int len, String value) {
        byte[] b = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, header, offset, Math.min(b.length, len));
    }

    private void putOctal(byte[] header, int offset, int len, long value) {
        byte[] b = String.format("%0" + (len - 1) + "o", value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, header, offset, Math.min(b.length, len - 1));
        header[offset + len - 1] = 0;
    }
}
