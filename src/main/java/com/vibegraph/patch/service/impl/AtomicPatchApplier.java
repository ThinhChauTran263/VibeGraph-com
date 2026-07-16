package com.vibegraph.patch.service.impl;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
final class AtomicPatchApplier {

    record Write(Path target, byte[] content) {
        Write {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    Session apply(Path root, List<Write> writes, List<Path> deletions) {
        Path journalRoot = root.resolve(".vibegraph-patch-" + UUID.randomUUID());
        Session session = new Session(root, journalRoot, writes, deletions);
        try {
            session.apply();
            return session;
        } catch (IOException | RuntimeException ex) {
            try {
                session.rollback();
            } catch (RuntimeException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException("Failed to apply local patch atomically", ex);
        }
    }

    static final class Session {
        private final Path root;
        private final Path journalRoot;
        private final List<Write> writes;
        private final List<Path> deletions;
        private final Map<Path, Path> backups = new HashMap<>();
        private final Set<Path> createdTargets = new LinkedHashSet<>();
        private final Set<Path> createdDirectories = new LinkedHashSet<>();
        private final AtomicBoolean finished = new AtomicBoolean();

        private Session(Path root, Path journalRoot, List<Write> writes, List<Path> deletions) {
            this.root = root;
            this.journalRoot = journalRoot;
            this.writes = List.copyOf(writes);
            this.deletions = List.copyOf(deletions);
        }

        void commit() {
            if (finished.compareAndSet(false, true)) {
                deleteJournal();
            }
        }

        void rollback() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            List<RuntimeException> failures = new ArrayList<>();
            createdTargets.stream()
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(target -> deleteQuietly(target, failures));
            backups.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparingInt(Path::getNameCount).reversed()))
                    .forEach(entry -> restoreQuietly(entry.getValue(), entry.getKey(), failures));
            createdDirectories.stream()
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(directory -> deleteIfEmptyQuietly(directory, failures));
            deleteJournalQuietly(failures);
            if (!failures.isEmpty()) {
                IllegalStateException rollbackFailure =
                        new IllegalStateException("Failed to roll back local patch");
                failures.forEach(rollbackFailure::addSuppressed);
                throw rollbackFailure;
            }
        }

        private void apply() throws IOException {
            Files.createDirectory(journalRoot);
            int backupIndex = 0;
            for (Write write : writes) {
                backupIndex = capture(write.target(), backupIndex);
            }
            for (Path deletion : deletions) {
                backupIndex = capture(deletion, backupIndex);
            }
            for (Write write : writes) {
                writeAtomically(write.target(), write.content());
            }
            for (Path deletion : deletions) {
                Files.deleteIfExists(deletion);
            }
        }

        private int capture(Path target, int backupIndex) throws IOException {
            if (backups.containsKey(target) || createdTargets.contains(target)) {
                return backupIndex;
            }
            if (Files.exists(target)) {
                Path backup = journalRoot.resolve(Integer.toString(backupIndex));
                Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
                backups.put(target, backup);
                return backupIndex + 1;
            }
            createdTargets.add(target);
            return backupIndex;
        }

        private void writeAtomically(Path target, byte[] content) throws IOException {
            createParentDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".vibegraph-patch-", ".tmp");
            try {
                Files.write(temp, content);
                try {
                    Files.move(
                            temp,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }

        private void createParentDirectories(Path parent) throws IOException {
            if (parent == null || Files.exists(parent)) {
                return;
            }
            List<Path> missing = new ArrayList<>();
            Path current = parent;
            while (current != null && !Files.exists(current) && current.startsWith(root)) {
                missing.add(current);
                current = current.getParent();
            }
            Files.createDirectories(parent);
            createdDirectories.addAll(missing);
        }

        private void restoreQuietly(Path backup, Path target, List<RuntimeException> failures) {
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(
                        backup,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ex) {
                failures.add(new IllegalStateException("Failed to restore patched file", ex));
            }
        }

        private void deleteQuietly(Path target, List<RuntimeException> failures) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ex) {
                failures.add(new IllegalStateException("Failed to remove patched file", ex));
            }
        }

        private void deleteIfEmptyQuietly(Path directory, List<RuntimeException> failures) {
            try {
                Files.deleteIfExists(directory);
            } catch (IOException ex) {
                if (Files.exists(directory)) {
                    failures.add(new IllegalStateException("Failed to remove patch directory", ex));
                }
            }
        }

        private void deleteJournal() {
            List<RuntimeException> failures = new ArrayList<>();
            deleteJournalQuietly(failures);
            if (!failures.isEmpty()) {
                throw failures.get(0);
            }
        }

        private void deleteJournalQuietly(List<RuntimeException> failures) {
            if (!Files.exists(journalRoot)) {
                return;
            }
            try (var paths = Files.walk(journalRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        failures.add(new IllegalStateException("Failed to clean patch journal", ex));
                    }
                });
            } catch (IOException ex) {
                failures.add(new IllegalStateException("Failed to inspect patch journal", ex));
            }
        }
    }
}
