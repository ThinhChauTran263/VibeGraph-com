package com.vibegraph.graph.service.impl;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.vibegraph.graph.service.DirectorySizeMeasurer;

@Component
public class FailClosedDirectorySizeMeasurer implements DirectorySizeMeasurer {

    private final FileTreeWalker fileTreeWalker;

    public FailClosedDirectorySizeMeasurer() {
        this(Files::walkFileTree);
    }

    FailClosedDirectorySizeMeasurer(FileTreeWalker fileTreeWalker) {
        this.fileTreeWalker = Objects.requireNonNull(fileTreeWalker);
    }

    @Override
    public long measureBytes(Path root) {
        SizeVisitor visitor = new SizeVisitor();
        try {
            fileTreeWalker.walk(root, visitor);
            return visitor.totalBytes;
        } catch (IOException | SecurityException | ArithmeticException ex) {
            throw new IllegalArgumentException("Directory size could not be measured safely", ex);
        }
    }

    @FunctionalInterface
    interface FileTreeWalker {
        void walk(Path root, FileVisitor<Path> visitor) throws IOException;
    }

    private static final class SizeVisitor implements FileVisitor<Path> {
        private long totalBytes;

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                throw new IllegalArgumentException("Symbolic links are not allowed in local imports");
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isSymbolicLink()) {
                throw new IllegalArgumentException("Symbolic links are not allowed in local imports");
            }
            if (!attrs.isRegularFile()) {
                throw new IllegalArgumentException("Unsupported file type in local import");
            }
            totalBytes = Math.addExact(totalBytes, attrs.size());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
