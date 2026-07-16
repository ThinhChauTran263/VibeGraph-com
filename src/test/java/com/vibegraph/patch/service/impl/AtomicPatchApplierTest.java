package com.vibegraph.patch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AtomicPatchApplier")
class AtomicPatchApplierTest {

    @TempDir Path root;

    private final AtomicPatchApplier applier = new AtomicPatchApplier();

    @Test
    @DisplayName("rollback restores created, overwritten, and deleted files")
    void rollback_restoresCompleteBatch() throws Exception {
        Path overwritten = root.resolve("src/Existing.java");
        Path deleted = root.resolve("src/DeleteMe.java");
        Path created = root.resolve("src/New.java");
        Files.createDirectories(overwritten.getParent());
        Files.writeString(overwritten, "old");
        Files.writeString(deleted, "delete");

        AtomicPatchApplier.Session session = applier.apply(
                root,
                List.of(
                        new AtomicPatchApplier.Write(overwritten, "new".getBytes()),
                        new AtomicPatchApplier.Write(created, "created".getBytes())),
                List.of(deleted));
        session.rollback();

        assertThat(Files.readString(overwritten)).isEqualTo("old");
        assertThat(Files.exists(created)).isFalse();
        assertThat(Files.readString(deleted)).isEqualTo("delete");
    }

    @Test
    @DisplayName("later IO failure rolls back earlier writes")
    void apply_laterIoFailure_rollsBackEarlierWrites() throws Exception {
        Path blocker = root.resolve("blocked-parent");
        Files.writeString(blocker, "not a directory");

        assertThatThrownBy(() -> applier.apply(
                        root,
                        List.of(
                                new AtomicPatchApplier.Write(
                                        root.resolve("src/First.java"), "first".getBytes()),
                                new AtomicPatchApplier.Write(
                                        blocker.resolve("Second.java"), "second".getBytes())),
                        List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to apply local patch atomically");

        assertThat(Files.exists(root.resolve("src/First.java"))).isFalse();
        assertThat(Files.readString(blocker)).isEqualTo("not a directory");
    }

    @Test
    @DisplayName("commit keeps changes and removes journal artifacts")
    void commit_keepsChangesAndCleansJournal() throws Exception {
        Path created = root.resolve("src/New.java");

        AtomicPatchApplier.Session session = applier.apply(
                root,
                List.of(new AtomicPatchApplier.Write(created, "created".getBytes())),
                List.of());
        session.commit();

        assertThat(Files.readString(created)).isEqualTo("created");
        try (var children = Files.list(root)) {
            assertThat(children.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".vibegraph-patch-"));
        }
    }
}
