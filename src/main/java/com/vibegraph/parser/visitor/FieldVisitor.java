package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Extracts Field nodes and field-related edges (HAS_FIELD, TYPE_OF, INJECTS) from AST.
 */
public class FieldVisitor extends VoidVisitorAdapter<Object> {

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
        TypeReferenceSupport.resolveTypeReference(variable.getType(), declaration)
                .ifPresent(resolvedType -> extractedEdges.add(EdgeData.of("TYPE_OF", fieldFullName, resolvedType)));

        // INJECTS edge if field has @Autowired or @Inject
        if (isInjected(declaration) && ownerFullName != null) {
            Map<String, Object> props = new HashMap<>();
            props.put("via", "field");
            props.put("fieldName", variable.getNameAsString());
            TypeReferenceSupport.resolveTypeReference(variable.getType(), declaration)
                    .ifPresent(resolvedType ->
                            extractedEdges.add(EdgeData.of("INJECTS", ownerFullName, resolvedType, props)));
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

    private NodeData toNodeData(FieldDeclaration declaration, VariableDeclarator variable) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", declaration.isStatic());
        properties.put("final", declaration.isFinal());
        properties.put("declaredType", variable.getType().asString());
        properties.put("injected", isInjected(declaration));

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
}
