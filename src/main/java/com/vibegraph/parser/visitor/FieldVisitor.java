package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Extracts Field nodes and field-related edges (HAS_FIELD, TYPE_OF, INJECTS) from AST.
 */
public class FieldVisitor extends VoidVisitorAdapter<Object> {

    private static final Map<String, String> JPA_RELATION_CARDINALITIES = Map.of(
            "OneToMany", "ONE_TO_MANY",
            "ManyToOne", "MANY_TO_ONE",
            "OneToOne", "ONE_TO_ONE",
            "ManyToMany", "MANY_TO_MANY");

    private final List<NodeData> extractedFields = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();

    @Override
    public void visit(FieldDeclaration n, Object arg) {
        for (VariableDeclarator variable : n.getVariables()) {
            NodeData fieldNode = toNodeData(n, variable);
            extractedFields.add(fieldNode);
            extractFieldEdges(n, variable, fieldNode.fullName());
        }
        super.visit(n, arg);
    }

    public List<NodeData> getExtractedFields() {
        return extractedFields;
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void extractFieldEdges(FieldDeclaration declaration, VariableDeclarator variable, String fieldFullName) {
        // HAS_FIELD edge from owner class/enum
        String ownerFullName = getOwnerFullName(variable);
        if (ownerFullName != null) {
            extractedEdges.add(EdgeData.of("HAS_FIELD", ownerFullName, fieldFullName));
        }

        // TYPE_OF edge to the field's declared type
        Optional<String> resolvedType = TypeReferenceSupport.resolveTypeReference(variable.getType(), declaration);
        resolvedType.ifPresent(type -> extractedEdges.add(EdgeData.of("TYPE_OF", fieldFullName, type)));

        // Direct entity/domain relationship for architecture projection. Raw field
        // facts are retained above; this verified class-to-class edge is what the UI
        // can show without pulling Field nodes into the baseline graph.
        relationCardinality(declaration).ifPresent(cardinality -> {
            if (ownerFullName != null && resolvedType.isPresent()) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("cardinality", cardinality);
                props.put("fieldName", variable.getNameAsString());
                props.put("lineNumber", variable.getBegin().map(p -> p.line).orElseGet(() ->
                        declaration.getBegin().map(p -> p.line).orElse(0)));
                extractedEdges.add(EdgeData.of("HAS_RELATION", ownerFullName, resolvedType.get(), props));
            }
        });

        // INJECTS edge if field has @Autowired or @Inject
        if (isInjected(declaration) && ownerFullName != null && resolvedType.isPresent()) {
            Map<String, Object> props = new HashMap<>();
            props.put("via", "field");
            props.put("fieldName", variable.getNameAsString());
            extractedEdges.add(EdgeData.of("INJECTS", ownerFullName, resolvedType.get(), props));
        }
    }

    private String getOwnerFullName(VariableDeclarator variable) {
        // Try ClassOrInterfaceDeclaration first
        Optional<String> classOwner = variable.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName);
        if (classOwner.isPresent()) {
            return classOwner.get();
        }

        // Try EnumDeclaration
        return variable.findAncestor(EnumDeclaration.class)
                .flatMap(EnumDeclaration::getFullyQualifiedName)
                .orElse(null);
    }

    private String resolveTypeName(String typeName, FieldDeclaration context) {
        // Strip generics for resolution
        String baseName = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;

        // Primitives don't need resolution
        if (isPrimitive(baseName)) {
            return typeName;
        }

        // Already qualified
        if (baseName.contains(".")) {
            return typeName;
        }

        // Try to resolve from imports
        return context.findCompilationUnit()
                .flatMap(cu -> cu.getImports().stream()
                        .filter(imp -> !imp.isAsterisk())
                        .filter(imp -> imp.getName().getIdentifier().equals(baseName))
                        .findFirst()
                        .map(imp -> imp.getNameAsString()))
                .orElseGet(() -> {
                    if (JAVA_LANG_TYPES.contains(baseName)) {
                        return "java.lang." + baseName;
                    }
                    return context.findCompilationUnit()
                            .flatMap(cu -> cu.getPackageDeclaration())
                            .map(pkg -> pkg.getNameAsString() + "." + baseName)
                            .orElse(baseName);
                });
    }

    /** Implicitly-imported {@code java.lang} types that must not be mis-qualified to the
     * current package when unqualified and not explicitly imported. */
    private static final java.util.Set<String> JAVA_LANG_TYPES = java.util.Set.of(
            "String", "Object", "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean",
            "Character", "Number", "CharSequence", "StringBuilder", "StringBuffer", "Math", "System",
            "Thread", "Runnable", "Iterable", "Comparable", "Cloneable", "Class", "Enum", "Void",
            "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
            "IllegalStateException", "NullPointerException", "UnsupportedOperationException");

    private boolean isPrimitive(String type) {
        String baseName = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
        return switch (baseName) {
            case "int", "long", "short", "byte", "float", "double", "boolean", "char",
                 "Integer", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Character",
                 "String", "Object" -> true;
            default -> false;
        };
    }

    private NodeData toNodeData(FieldDeclaration declaration, VariableDeclarator variable) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", declaration.isStatic());
        properties.put("final", declaration.isFinal());
        properties.put("declaredType", variable.getType().asString());
        properties.put("injected", isInjected(declaration));
        properties.put("annotations", annotationNames(declaration));

        return NodeData.of(
                "Field",
                variable.getNameAsString(),
                fullName(variable),
                filePath(declaration),
                variable.getBegin().map(position -> position.line).orElseGet(() ->
                        declaration.getBegin().map(position -> position.line).orElse(0)),
                variable.getEnd().map(position -> position.line).orElseGet(() ->
                        declaration.getEnd().map(position -> position.line).orElse(0)),
                properties
        );
    }

    private String fullName(VariableDeclarator variable) {
        // Try ClassOrInterfaceDeclaration first
        Optional<String> classOwner = variable.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName);
        if (classOwner.isPresent()) {
            return classOwner.get() + "." + variable.getNameAsString();
        }

        // Try EnumDeclaration
        Optional<String> enumOwner = variable.findAncestor(EnumDeclaration.class)
                .flatMap(EnumDeclaration::getFullyQualifiedName);
        if (enumOwner.isPresent()) {
            return enumOwner.get() + "." + variable.getNameAsString();
        }

        // Fallback to simple name from any TypeDeclaration
        String ownerName = variable.findAncestor(TypeDeclaration.class)
                .map(TypeDeclaration::getNameAsString)
                .orElse("<unknown>");
        return ownerName + "." + variable.getNameAsString();
    }

    private String filePath(com.github.javaparser.ast.Node node) {
        return node.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getStorage().map(storage -> storage.getPath().toString()))
                .orElse("");
    }

    private boolean isInjected(FieldDeclaration declaration) {
        return declaration.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .anyMatch(name -> name.equals("Autowired") || name.equals("Inject") || name.equals("Resource"));
    }

    private Optional<String> relationCardinality(FieldDeclaration declaration) {
        return annotationNames(declaration).stream()
                .filter(JPA_RELATION_CARDINALITIES::containsKey)
                .findFirst()
                .map(JPA_RELATION_CARDINALITIES::get);
    }

    private List<String> annotationNames(NodeWithAnnotations<?> declaration) {
        return declaration.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .distinct()
                .toList();
    }
}
