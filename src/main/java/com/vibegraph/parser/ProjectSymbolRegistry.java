package com.vibegraph.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * Thread-scoped registry of project symbols discovered during pass 1.
 */
public final class ProjectSymbolRegistry {

    private static final ThreadLocal<ProjectSymbolRegistry> CURRENT = new ThreadLocal<>();

    private final Set<String> typeFullNames;

    private ProjectSymbolRegistry(Set<String> typeFullNames) {
        this.typeFullNames = Set.copyOf(typeFullNames);
    }

    public static ProjectSymbolRegistry empty() {
        return new ProjectSymbolRegistry(Set.of());
    }

    public static ProjectSymbolRegistry fromCompilationUnits(List<CompilationUnit> compilationUnits) {
        Set<String> typeFullNames = new HashSet<>();
        if (compilationUnits != null) {
            for (CompilationUnit cu : compilationUnits) {
                if (cu == null) {
                    continue;
                }
                String packageName = cu.getPackageDeclaration()
                        .map(pkg -> pkg.getNameAsString())
                        .orElse("");
                for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
                    collectTypeNames(typeDeclaration, packageName, null, typeFullNames);
                }
            }
        }
        return new ProjectSymbolRegistry(typeFullNames);
    }

    public static ProjectSymbolRegistry fromFiles(List<java.nio.file.Path> javaFiles) {
        if (javaFiles == null || javaFiles.isEmpty()) {
            return empty();
        }
        // Pass 1 runs on a worker pool — one resolver-less parser per thread (JavaParser is
        // not thread-safe to share), collecting into a concurrent set. Type names land in a
        // Set, so worker completion order never changes the registry's content.
        int parallelism = Math.max(1, Math.min(javaFiles.size(), Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                parallelism, r -> {
                    Thread t = new Thread(r, "vg-registry-worker");
                    t.setDaemon(true);
                    return t;
                });
        ThreadLocal<JavaParser> threadParser = ThreadLocal.withInitial(() ->
                new JavaParser(new ParserConfiguration()
                        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)));
        Set<String> typeFullNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>(javaFiles.size());
            for (java.nio.file.Path file : javaFiles) {
                tasks.add(executor.submit(() -> {
                    try {
                        threadParser.get().parse(file).getResult()
                                .ifPresent(cu -> collectTypeNames(cu, typeFullNames));
                    } catch (Exception ignored) {
                        // Pass 1 should stay best-effort; parse errors are handled in the main pass.
                    }
                }));
            }
            for (java.util.concurrent.Future<?> task : tasks) {
                try {
                    task.get();
                } catch (Exception ignored) {
                    // Best-effort pass 1 — the main pass reports parse failures properly.
                }
            }
        } finally {
            executor.shutdown();
        }
        return new ProjectSymbolRegistry(typeFullNames);
    }

    private static void collectTypeNames(CompilationUnit compilationUnit, Set<String> sink) {
        String packageName = compilationUnit.getPackageDeclaration()
                .map(pkg -> pkg.getNameAsString())
                .orElse("");
        for (TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
            collectTypeNames(typeDeclaration, packageName, null, sink);
        }
    }

    public static Scope open(ProjectSymbolRegistry registry) {
        ProjectSymbolRegistry previous = CURRENT.get();
        CURRENT.set(registry);
        return new Scope(previous);
    }

    public static Optional<ProjectSymbolRegistry> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public boolean contains(String typeFullName) {
        return typeFullName != null && typeFullNames.contains(typeFullName);
    }

    public boolean isEmpty() {
        return typeFullNames.isEmpty();
    }

    private static void collectTypeNames(TypeDeclaration<?> typeDeclaration, String packageName,
            String ownerPrefix, Set<String> typeFullNames) {
        if (typeDeclaration == null) {
            return;
        }
        String currentFullName = ownerPrefix == null
                ? qualify(packageName, typeDeclaration.getNameAsString())
                : ownerPrefix + "." + typeDeclaration.getNameAsString();
        typeFullNames.add(currentFullName);

        for (BodyDeclaration<?> member : typeDeclaration.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedType) {
                collectTypeNames(nestedType, packageName, currentFullName, typeFullNames);
            }
        }
    }

    private static String qualify(String packageName, String simpleName) {
        if (packageName == null || packageName.isBlank()) {
            return simpleName;
        }
        return packageName + "." + simpleName;
    }

    public static final class Scope implements AutoCloseable {
        private final ProjectSymbolRegistry previous;
        private boolean closed;

        private Scope(ProjectSymbolRegistry previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
