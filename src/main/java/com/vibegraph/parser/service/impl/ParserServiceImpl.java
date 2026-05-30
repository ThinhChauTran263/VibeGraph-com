package com.vibegraph.parser.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.vibegraph.common.util.FileUtils;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.ParserService;
import com.vibegraph.parser.visitor.ClassVisitor;
import com.vibegraph.parser.visitor.FieldVisitor;
import com.vibegraph.parser.visitor.ImportVisitor;
import com.vibegraph.parser.visitor.MethodVisitor;
import com.vibegraph.parser.visitor.SpringAnnotationVisitor;

import lombok.extern.slf4j.Slf4j;

/**
 * JavaParser-based implementation of ParserService.
 * Parses .java files using visitors to extract nodes and edges for the knowledge graph.
 */
@Service
@Slf4j
public class ParserServiceImpl implements ParserService {

    @Override
    public ParseResult parseFile(Path filePath) {
        // Single-file parsing without project context — limited symbol resolution
        return parseFileInternal(filePath, null);
    }

    private ParseResult parseFileInternal(Path filePath, JavaParser parser) {
        List<String> warnings = new ArrayList<>();

        if (!Files.exists(filePath) || !FileUtils.isJavaFile(filePath)) {
            return ParseResult.builder()
                    .filePath(filePath.toString())
                    .warnings(List.of("File does not exist or is not a .java file: " + filePath))
                    .build();
        }

        // Use provided parser (project-wide) or create file-local one
        if (parser == null) {
            parser = createParser(filePath.getParent());
        }

        try {
            var parseResult = parser.parse(filePath);

            if (!parseResult.isSuccessful()) {
                parseResult.getProblems().forEach(problem ->
                        warnings.add("Parse problem: " + problem.getMessage()));
                return ParseResult.builder()
                        .filePath(filePath.toString())
                        .warnings(warnings)
                        .build();
            }

            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                return ParseResult.builder()
                        .filePath(filePath.toString())
                        .warnings(List.of("No compilation unit produced for: " + filePath))
                        .build();
            }

            // Apply visitors
            ClassVisitor classVisitor = new ClassVisitor();
            MethodVisitor methodVisitor = new MethodVisitor();
            FieldVisitor fieldVisitor = new FieldVisitor();
            SpringAnnotationVisitor springVisitor = new SpringAnnotationVisitor();

            classVisitor.visit(cu, null);
            methodVisitor.visit(cu, null);
            fieldVisitor.visit(cu, null);
            springVisitor.visit(cu, null);

            // ImportVisitor needs the source file's full name — use primary class FQCN
            String primaryFqcn = cu.getPrimaryTypeName().orElse("");
            String packageName = cu.getPackageDeclaration()
                    .map(p -> p.getNameAsString())
                    .orElse("");
            String sourceFullName = packageName.isEmpty() ? primaryFqcn : packageName + "." + primaryFqcn;
            ImportVisitor importVisitor = null;
            if (!sourceFullName.isEmpty()) {
                importVisitor = new ImportVisitor(sourceFullName);
                importVisitor.visit(cu, null);
            }

            // Aggregate nodes
            List<NodeData> nodes = new ArrayList<>();
            nodes.addAll(classVisitor.getExtractedNodes());
            nodes.addAll(methodVisitor.getExtractedMethods());
            nodes.addAll(fieldVisitor.getExtractedFields());
            // Route nodes must be aggregated too — otherwise HANDLES_ROUTE edges below
            // reference a target node that was never persisted and get silently dropped.
            nodes.addAll(springVisitor.getExtractedNodes());

            // Aggregate edges
            List<EdgeData> edges = new ArrayList<>();
            edges.addAll(classVisitor.getExtractedEdges());
            edges.addAll(methodVisitor.getExtractedEdges());
            edges.addAll(fieldVisitor.getExtractedEdges());
            edges.addAll(springVisitor.getExtractedEdges());
            if (importVisitor != null) {
                edges.addAll(importVisitor.getExtractedEdges());
            }

            return ParseResult.builder()
                    .filePath(filePath.toString())
                    .nodes(nodes)
                    .edges(edges)
                    .warnings(warnings)
                    .build();

        } catch (IOException e) {
            log.error("Failed to parse file: {}", filePath, e);
            return ParseResult.builder()
                    .filePath(filePath.toString())
                    .warnings(List.of("IOException parsing file: " + e.getMessage()))
                    .build();
        }
    }

    @Override
    public List<ParseResult> parseProject(Path projectRoot) {
        List<ParseResult> results = new ArrayList<>();

        try {
            List<Path> javaFiles = FileUtils.scanJavaFiles(projectRoot);
            log.info("Found {} .java files in project: {}", javaFiles.size(), projectRoot);

            // Build a single project-wide parser whose type solver indexes all source roots.
            // This lets MethodCallExpr.resolve() resolve cross-package, cross-class calls
            // (CALLS edges) instead of only same-directory ones.
            JavaParser parser = createProjectParser(projectRoot, javaFiles);

            for (Path javaFile : javaFiles) {
                try {
                    ParseResult result = parseFileInternal(javaFile, parser);
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Failed to parse file: {}", javaFile, e);
                    results.add(ParseResult.builder()
                            .filePath(javaFile.toString())
                            .warnings(List.of("Unexpected error: " + e.getMessage()))
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan project directory: {}", projectRoot, e);
        }

        return results;
    }

    @Override
    public ParseResult parseFileWithCache(Path filePath, String projectId) {
        // Cache-based incremental parsing deferred to Sprint 2
        throw new UnsupportedOperationException("Not implemented yet — deferred to Sprint 2");
    }

    private JavaParser createParser(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        // Add source root for resolving project types
        if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
            try {
                typeSolver.add(new JavaParserTypeSolver(sourceRoot));
            } catch (Exception e) {
                log.debug("Could not add source root to type solver: {}", sourceRoot);
            }
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        return new JavaParser(config);
    }

    /**
     * Builds a parser whose type solver indexes every source root in the project.
     * This is what enables cross-class CALLS edges to resolve — without it,
     * the type solver can only see types in the same directory as the file being parsed.
     *
     * Detection strategy:
     * 1. Look for standard layouts: src/main/java, src/test/java, src/main/kotlin (multi-module too).
     * 2. Fallback: derive source roots from each .java file's package declaration path.
     */
    private JavaParser createProjectParser(Path projectRoot, List<Path> javaFiles) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        java.util.Set<Path> sourceRoots = detectSourceRoots(projectRoot, javaFiles);
        for (Path root : sourceRoots) {
            try {
                typeSolver.add(new JavaParserTypeSolver(root));
                log.debug("Added type-solver source root: {}", root);
            } catch (Exception e) {
                log.debug("Could not add source root {}: {}", root, e.getMessage());
            }
        }
        log.info("Type solver indexed {} source root(s)", sourceRoots.size());

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        return new JavaParser(config);
    }

    /**
     * Detect Java source roots in a project. Handles:
     * - Standard Maven/Gradle: src/main/java, src/test/java
     * - Multi-module: {module}/src/main/java
     * - Non-standard: derived from package declarations in actual files
     */
    private java.util.Set<Path> detectSourceRoots(Path projectRoot, List<Path> javaFiles) {
        java.util.Set<Path> roots = new java.util.LinkedHashSet<>();

        // Strategy 1: well-known layouts at any depth
        try (var stream = Files.walk(projectRoot, 6)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.endsWith("/src/main/java") || s.endsWith("/src/test/java");
                    })
                    .forEach(roots::add);
        } catch (IOException e) {
            log.debug("Source-root walk failed: {}", e.getMessage());
        }

        // Strategy 2: derive from package declarations (covers non-standard layouts).
        // For a file at /a/b/c/com/example/Foo.java with `package com.example;`,
        // the source root is /a/b/c.
        // We do a quick read of the package line — cheap, no full parse.
        for (Path javaFile : javaFiles) {
            Path derived = deriveSourceRoot(javaFile);
            if (derived != null) {
                roots.add(derived);
            }
        }

        return roots;
    }

    private Path deriveSourceRoot(Path javaFile) {
        try {
            String pkg = readPackageDeclaration(javaFile);
            if (pkg == null || pkg.isBlank()) {
                // Default-package file — its parent directory is the source root.
                return javaFile.getParent();
            }
            // Strip package segments from the file's parent path.
            Path dir = javaFile.getParent();
            String[] segments = pkg.split("\\.");
            for (int i = segments.length - 1; i >= 0 && dir != null; i--) {
                if (!dir.getFileName().toString().equals(segments[i])) {
                    return null; // Path doesn't match package — skip.
                }
                dir = dir.getParent();
            }
            return dir;
        } catch (IOException e) {
            return null;
        }
    }

    private String readPackageDeclaration(Path javaFile) throws IOException {
        try (var lines = Files.lines(javaFile, java.nio.charset.StandardCharsets.UTF_8)) {
            return lines
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("//") && !l.startsWith("/*") && !l.startsWith("*"))
                    .findFirst()
                    .filter(l -> l.startsWith("package "))
                    .map(l -> l.substring("package ".length()).replaceAll(";.*", "").trim())
                    .orElse(null);
        } catch (java.nio.charset.MalformedInputException e) {
            // Some files may use non-UTF-8 encoding — skip silently.
            return null;
        }
    }
}
