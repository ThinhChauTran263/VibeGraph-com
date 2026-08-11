package com.vibegraph.graph.importer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;

import com.vibegraph.common.exception.ArchiveImportException;
import com.vibegraph.common.exception.ArchiveImportException.Reason;
import com.vibegraph.graph.importer.config.ArchiveImportProperties;

/**
 * Safely materializes the {@code .java} files of an uploaded project archive into a
 * server-owned workspace directory.
 *
 * <p>Only {@code .java} files are written; everything else (directories, non-Java files,
 * ignored build directories) is skipped without being materialized. An invalid or unsafe
 * entry aborts extraction; files already written before the failing entry are left for the
 * import service to clean up (it removes the workspace on any failure). Rejection reasons:
 * <ul>
 *   <li>path traversal ({@code ../}), absolute paths, Windows drive paths, or any entry
 *       whose normalized target escapes the destination root -> {@link Reason#UNSAFE_ENTRY};</li>
 *   <li>TAR symlink, hardlink, or other non-regular-file entries -> {@link Reason#UNSAFE_ENTRY}
 *       (rejected outright; resolving link safety across archive variants is error-prone);</li>
 *   <li>an archive larger than {@code maxSize}, or whose extracted bytes exceed {@code maxSize}
 *       (archive-bomb guard) -> {@link Reason#OVERSIZE};</li>
 *   <li>no {@code .java} files at all -> {@link Reason#EMPTY_ARCHIVE};</li>
 *   <li>any I/O or format failure -> {@link Reason#EXTRACTION_FAILED}.</li>
 * </ul>
 */
@Component
public class ArchiveExtractor {

    private final ArchiveImportProperties properties;

    public ArchiveExtractor(ArchiveImportProperties properties) {
        this.properties = properties;
    }

    /**
     * Extract the {@code .java} files of {@code archivePath} into {@code destinationRoot}.
     *
     * @param type detected via {@link ArchiveTypeDetector} from the upload's original filename
     */
    public ArchiveExtractionResult extract(Path archivePath, ArchiveType type, Path destinationRoot) {
        return extract(archivePath, type, destinationRoot, properties.getMaxSize().toBytes());
    }

    /**
     * Extract with a caller-provided byte ceiling, typically the account's remaining quota.
     */
    public ArchiveExtractionResult extract(Path archivePath, ArchiveType type, Path destinationRoot,
            long maxBytes) {
        if (maxBytes <= 0) {
            throw new ArchiveImportException(Reason.OVERSIZE, "No storage quota remains for this import");
        }
        Path destRoot = destinationRoot.toAbsolutePath().normalize();
        Set<String> ignored = Set.copyOf(properties.getIgnoredPaths());
        List<Path> javaFiles = new ArrayList<>();
        List<String> relativeJavaPaths = new ArrayList<>();

        try {
            if (Files.size(archivePath) > maxBytes) {
                throw new ArchiveImportException(Reason.OVERSIZE, "Archive exceeds the configured maximum size");
            }
            Files.createDirectories(destRoot);

            try (InputStream in = new BufferedInputStream(Files.newInputStream(archivePath))) {
                switch (type) {
                    case ZIP -> extractZip(in, destRoot, ignored, maxBytes, javaFiles, relativeJavaPaths);
                    case TAR -> extractTar(in, destRoot, ignored, maxBytes, javaFiles, relativeJavaPaths);
                    case TAR_GZ -> {
                        try (InputStream gz = new GZIPInputStream(in)) {
                            extractTar(gz, destRoot, ignored, maxBytes, javaFiles, relativeJavaPaths);
                        }
                    }
                }
            }
        } catch (ArchiveImportException e) {
            throw e;
        } catch (IOException e) {
            throw new ArchiveImportException(Reason.EXTRACTION_FAILED, "Failed to extract archive: " + e.getMessage());
        }

        if (javaFiles.isEmpty()) {
            throw new ArchiveImportException(Reason.EMPTY_ARCHIVE, "Archive contains no .java files");
        }
        return new ArchiveExtractionResult(destRoot, List.copyOf(javaFiles), List.copyOf(relativeJavaPaths));
    }

    private void extractZip(InputStream in, Path destRoot, Set<String> ignored, long maxBytes,
                            List<Path> javaFiles, List<String> relativeJavaPaths) throws IOException {
        long[] total = {0L};
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                materializeIfJava(entry.getName(), zip, destRoot, ignored, maxBytes, total, javaFiles, relativeJavaPaths);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void extractTar(InputStream in, Path destRoot, Set<String> ignored, long maxBytes,
                            List<Path> javaFiles, List<String> relativeJavaPaths) throws IOException {
        long[] total = {0L};
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new ArchiveImportException(Reason.UNSAFE_ENTRY,
                            "Refusing symlink/hardlink entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (!entry.isFile()) {
                    throw new ArchiveImportException(Reason.UNSAFE_ENTRY,
                            "Refusing non-regular-file entry: " + entry.getName());
                }
                materializeIfJava(entry.getName(), tar, destRoot, ignored, maxBytes, total, javaFiles, relativeJavaPaths);
            }
        }
    }

    private void materializeIfJava(String entryName, InputStream content, Path destRoot, Set<String> ignored,
                                   long maxBytes, long[] total, List<Path> javaFiles, List<String> relativeJavaPaths)
            throws IOException {
        if (entryName == null || !entryName.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return;
        }
        Path target = resolveSafely(destRoot, entryName);
        if (isIgnored(destRoot, target, ignored)) {
            return;
        }
        Files.createDirectories(target.getParent());
        copyCapped(content, target, maxBytes, total);
        javaFiles.add(target);
        relativeJavaPaths.add(destRoot.relativize(target).toString().replace('\\', '/'));
    }

    /**
     * Resolve an archive entry name to a path strictly inside {@code destRoot}, rejecting
     * blank, absolute, Windows-drive, traversal, or escaping names.
     */
    private Path resolveSafely(Path destRoot, String entryName) {
        String normalized = entryName.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            throw new ArchiveImportException(Reason.UNSAFE_ENTRY, "Blank archive entry name");
        }
        if (normalized.startsWith("/")) {
            throw new ArchiveImportException(Reason.UNSAFE_ENTRY, "Absolute path entry: " + entryName);
        }
        if (normalized.matches("(?i)^[a-z]:.*")) {
            throw new ArchiveImportException(Reason.UNSAFE_ENTRY, "Windows drive path entry: " + entryName);
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..")) {
                throw new ArchiveImportException(Reason.UNSAFE_ENTRY, "Path traversal entry: " + entryName);
            }
        }
        Path target = destRoot.resolve(normalized).normalize();
        if (!target.startsWith(destRoot)) {
            throw new ArchiveImportException(Reason.UNSAFE_ENTRY, "Entry escapes workspace: " + entryName);
        }
        return target;
    }

    private boolean isIgnored(Path destRoot, Path target, Set<String> ignored) {
        for (Path segment : destRoot.relativize(target)) {
            if (ignored.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private void copyCapped(InputStream in, Path target, long maxBytes, long[] total) throws IOException {
        byte[] buffer = new byte[8192];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total[0] += read;
                if (total[0] > maxBytes) {
                    throw new ArchiveImportException(Reason.OVERSIZE,
                            "Extracted content exceeds the configured maximum size");
                }
                out.write(buffer, 0, read);
            }
        }
    }
}
