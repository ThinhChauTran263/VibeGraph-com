package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts Class, Interface, Enum, Annotation nodes and inheritance edges from AST.
 */
public class ClassVisitor extends VoidVisitorAdapter<Object> {

    private final List<NodeData> extractedNodes = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        NodeData node = toNodeData(n);
        extractedNodes.add(node);
        extractInheritanceEdges(n, node.fullName());
        super.visit(n, arg);
    }

    @Override
    public void visit(EnumDeclaration n, Object arg) {
        extractedNodes.add(toNodeData(n));
        super.visit(n, arg);
    }

    @Override
    public void visit(AnnotationDeclaration n, Object arg) {
        extractedNodes.add(toNodeData(n));
        super.visit(n, arg);
    }

    @Override
    public void visit(RecordDeclaration n, Object arg) {
        extractedNodes.add(toNodeData(n));
        super.visit(n, arg);
    }

    public List<NodeData> getExtractedNodes() {
        return extractedNodes;
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void extractInheritanceEdges(ClassOrInterfaceDeclaration declaration, String sourceFullName) {
        // EXTENDS edges
        for (ClassOrInterfaceType extendedType : declaration.getExtendedTypes()) {
            TypeReferenceSupport.resolveTypeReference(extendedType, declaration)
                    .ifPresent(targetFullName -> extractedEdges.add(EdgeData.of("EXTENDS", sourceFullName, targetFullName, Map.of(
                            "lineNumber", extendedType.getBegin().map(p -> p.line).orElse(0)
                    ))));
        }

        // IMPLEMENTS edges (only for classes)
        if (!declaration.isInterface()) {
            for (ClassOrInterfaceType implementedType : declaration.getImplementedTypes()) {
                TypeReferenceSupport.resolveTypeReference(implementedType, declaration)
                        .ifPresent(targetFullName -> extractedEdges.add(EdgeData.of("IMPLEMENTS", sourceFullName, targetFullName, Map.of(
                                "lineNumber", implementedType.getBegin().map(p -> p.line).orElse(0)
                        ))));
            }
        }

        // HAS_INNER edges for nested types
        if (declaration.isNestedType()) {
            Optional<String> parentFullName = declaration.findAncestor(ClassOrInterfaceDeclaration.class)
                    .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName);
            if (parentFullName.isPresent()) {
                extractedEdges.add(EdgeData.of("HAS_INNER", parentFullName.get(), sourceFullName));
            }
        }
    }

    private NodeData toNodeData(ClassOrInterfaceDeclaration declaration) {
        String type = declaration.isInterface() ? "Interface" : classNodeType(declaration);
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("abstract", declaration.isAbstract());
        properties.put("final", declaration.isFinal());
        properties.put("static", declaration.isStatic());
        properties.put("inner", declaration.isNestedType());
        List<String> annotations = annotationNames(declaration);
        properties.put("annotations", annotations);
        properties.put("springLayer", springLayer(annotations));

        return NodeData.of(
                type,
                declaration.getNameAsString(),
                declaration.getFullyQualifiedName().orElse(declaration.getNameAsString()),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private NodeData toNodeData(EnumDeclaration declaration) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", declaration.isStatic());
        properties.put("inner", declaration.isNestedType());
        List<String> annotations = annotationNames(declaration);
        properties.put("annotations", annotations);
        properties.put("springLayer", springLayer(annotations));
        properties.put("values", declaration.getEntries().stream()
                .map(entry -> entry.getNameAsString())
                .toList());

        return NodeData.of(
                "Enum",
                declaration.getNameAsString(),
                declaration.getFullyQualifiedName().orElse(declaration.getNameAsString()),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private NodeData toNodeData(AnnotationDeclaration declaration) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("annotations", annotationNames(declaration));

        return NodeData.of(
                "Annotation",
                declaration.getNameAsString(),
                declaration.getFullyQualifiedName().orElse(declaration.getNameAsString()),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private NodeData toNodeData(RecordDeclaration declaration) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", declaration.isStatic());
        properties.put("inner", declaration.isNestedType());
        List<String> annotations = annotationNames(declaration);
        properties.put("annotations", annotations);
        properties.put("springLayer", springLayer(annotations));
        properties.put("components", declaration.getParameters().stream()
                .map(parameter -> parameter.getNameAsString())
                .toList());

        return NodeData.of(
                "Record",
                declaration.getNameAsString(),
                declaration.getFullyQualifiedName().orElse(declaration.getNameAsString()),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private String classNodeType(ClassOrInterfaceDeclaration declaration) {
        List<String> annotations = annotationNames(declaration);
        return isDbModel(annotations) ? "DBModel" : "Class";
    }

    private List<String> annotationNames(NodeWithAnnotations<?> declaration) {
        return declaration.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .distinct()
                .toList();
    }

    private boolean isDbModel(List<String> annotations) {
        return annotations.contains("Entity") || annotations.contains("Node")
                || annotations.contains("Document") || annotations.contains("Table");
    }

    private String filePath(com.github.javaparser.ast.Node node) {
        return node.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getStorage().map(storage -> storage.getPath().toString()))
                .orElse("");
    }

    private String springLayer(List<String> annotations) {
        if (annotations.contains("RestController") || annotations.contains("Controller")) {
            return "CONTROLLER";
        }
        if (annotations.contains("Service")) {
            return "SERVICE";
        }
        if (annotations.contains("Repository")) {
            return "REPOSITORY";
        }
        if (annotations.contains("Configuration")) {
            return "CONFIG";
        }
        if (annotations.contains("Entity") || annotations.contains("Node")
                || annotations.contains("Document") || annotations.contains("Table")) {
            return "ENTITY";
        }
        if (annotations.contains("Component") || annotations.contains("ControllerAdvice")
                || annotations.contains("RestControllerAdvice")) {
            return "COMPONENT";
        }
        return "NONE";
    }
}
