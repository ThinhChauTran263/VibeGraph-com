package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.MethodSkipPolicy;
import com.vibegraph.parser.Signatures;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Extracts {@code Annotation} nodes and {@code ANNOTATED_BY} edges.
 *
 * <p>Canonical direction: {@code (annotated element)-[:ANNOTATED_BY]->(:Annotation)}.
 * The annotated element is a Class/Interface/Enum/Record/DBModel, Method,
 * Constructor, or Field; the target is the annotation TYPE (one node per distinct
 * fully-qualified annotation type). Routine methods and no-op constructors obey
 * {@link MethodSkipPolicy}; they do not receive annotation edges because their
 * Method/Constructor nodes are not emitted.
 *
 * <p>Annotation type resolution is best-effort (imports → same package → java.lang
 * for the well-known built-ins → simple name). Unresolved names fall back to the
 * simple identifier; this is documented as a known limitation in neo4j-schema.md.
 */
public class AnnotationVisitor extends VoidVisitorAdapter<Object> {

    private static final Set<String> JAVA_LANG_ANNOTATIONS = Set.of(
            "Override", "Deprecated", "SuppressWarnings", "SafeVarargs", "FunctionalInterface");

    private final List<NodeData> extractedNodes = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();
    /** Dedupe Annotation nodes within a file by fullName (MERGE dedupes across files). */
    private final Map<String, NodeData> annotationNodes = new LinkedHashMap<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        String fqcn = n.getFullyQualifiedName().orElse(n.getNameAsString());
        annotate(n, fqcn);
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, Object arg) {
        annotate(n, n.getFullyQualifiedName().orElse(n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, Object arg) {
        annotate(n, n.getFullyQualifiedName().orElse(n.getNameAsString()));
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        if (MethodSkipPolicy.shouldSkip(n)) {
            super.visit(n, arg);
            return;
        }
        ownerFqcn(n).ifPresent(owner -> {
            List<String> paramTypes = n.getParameters().stream()
                    .map(p -> p.getType().asString())
                    .toList();
            annotate(n, Signatures.method(owner, n.getNameAsString(), paramTypes));
        });
        super.visit(n, arg);
    }

    @Override
    public void visit(ConstructorDeclaration n, Object arg) {
        if (MethodSkipPolicy.shouldSkip(n)) {
            super.visit(n, arg);
            return;
        }
        ownerFqcn(n).ifPresent(owner -> {
            List<String> paramTypes = n.getParameters().stream()
                    .map(p -> p.getType().asString())
                    .toList();
            annotate(n, Signatures.method(owner, "<init>", paramTypes));
        });
        super.visit(n, arg);
    }

    @Override
    public void visit(FieldDeclaration n, Object arg) {
        if (!n.getAnnotations().isEmpty()) {
            ownerFqcn(n).ifPresent(owner -> {
                for (VariableDeclarator variable : n.getVariables()) {
                    annotate(n, owner + "." + variable.getNameAsString());
                }
            });
        }
        super.visit(n, arg);
    }

    public List<NodeData> getExtractedNodes() {
        return new ArrayList<>(annotationNodes.values());
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void annotate(NodeWithAnnotations<?> annotated, String elementFullName) {
        for (AnnotationExpr annotation : annotated.getAnnotations()) {
            String simpleName = annotation.getName().getIdentifier();
            String annotationFqn = resolveAnnotationType(simpleName, (com.github.javaparser.ast.Node) annotated);

            annotationNodes.computeIfAbsent(annotationFqn, fqn -> NodeData.of(
                    "Annotation",
                    simpleName,
                    fqn,
                    // Empty filePath: an annotation TYPE referenced here is not
                    // "defined" by this file, so it must not collect a spurious
                    // File-[:DEFINES]->Annotation edge (fileDefinesEdges matches by
                    // filePath). Declared @interface nodes still come from ClassVisitor
                    // with a real filePath and keep their DEFINES edge.
                    "",
                    annotation.getBegin().map(p -> p.line).orElse(0),
                    annotation.getEnd().map(p -> p.line).orElse(0),
                    Map.of("simpleName", simpleName)));

            extractedEdges.add(EdgeData.of("ANNOTATED_BY", elementFullName, annotationFqn, Map.of(
                    "lineNumber", annotation.getBegin().map(p -> p.line).orElse(0)
            )));
        }
    }

    private Optional<String> ownerFqcn(com.github.javaparser.ast.Node node) {
        Optional<String> classOwner = node.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName);
        if (classOwner.isPresent()) {
            return classOwner;
        }
        Optional<String> enumOwner = node.findAncestor(EnumDeclaration.class)
                .flatMap(EnumDeclaration::getFullyQualifiedName);
        if (enumOwner.isPresent()) {
            return enumOwner;
        }
        return node.findAncestor(RecordDeclaration.class)
                .flatMap(RecordDeclaration::getFullyQualifiedName);
    }

    private String resolveAnnotationType(String simpleName, com.github.javaparser.ast.Node context) {
        if (simpleName.contains(".")) {
            return simpleName;
        }
        if (JAVA_LANG_ANNOTATIONS.contains(simpleName)) {
            return "java.lang." + simpleName;
        }
        return context.findCompilationUnit()
                .flatMap(cu -> cu.getImports().stream()
                        .filter(imp -> !imp.isAsterisk())
                        .filter(imp -> imp.getName().getIdentifier().equals(simpleName))
                        .findFirst()
                        .map(imp -> imp.getNameAsString()))
                .orElseGet(() -> context.findCompilationUnit()
                        .flatMap(cu -> cu.getPackageDeclaration())
                        .map(pkg -> pkg.getNameAsString() + "." + simpleName)
                        .orElse(simpleName));
    }
}
