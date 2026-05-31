package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extracts Method nodes and method-related edges (CALLS, RETURNS, PARAMETER_TYPE, THROWS) from AST.
 */
public class MethodVisitor extends VoidVisitorAdapter<Object> {

    private final List<NodeData> extractedMethods = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        NodeData methodNode = toNodeData(n);
        extractedMethods.add(methodNode);
        extractMethodEdges(n, methodNode.fullName());
        extractCallEdges(n, methodNode.fullName());
        super.visit(n, arg);
    }

    @Override
    public void visit(ConstructorDeclaration n, Object arg) {
        NodeData methodNode = toNodeData(n);
        extractedMethods.add(methodNode);
        extractConstructorEdges(n, methodNode.fullName());
        super.visit(n, arg);
    }

    public List<NodeData> getExtractedMethods() {
        return extractedMethods;
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void extractMethodEdges(MethodDeclaration declaration, String methodFullName) {
        // RETURNS edge
        String returnType = declaration.getType().asString();
        if (!returnType.equals("void")) {
            String resolvedReturn = resolveTypeName(returnType, declaration);
            extractedEdges.add(EdgeData.of("RETURNS", methodFullName, resolvedReturn));
        }

        // PARAMETER_TYPE edges
        for (int i = 0; i < declaration.getParameters().size(); i++) {
            String paramType = declaration.getParameter(i).getType().asString();
            String resolvedParam = resolveTypeName(paramType, declaration);
            extractedEdges.add(EdgeData.of("PARAMETER_TYPE", methodFullName, resolvedParam, Map.of(
                    "position", i
            )));
        }

        // THROWS edges
        for (var thrownType : declaration.getThrownExceptions()) {
            String resolvedThrown = resolveTypeName(thrownType.asString(), declaration);
            extractedEdges.add(EdgeData.of("THROWS", methodFullName, resolvedThrown));
        }

        // HAS_METHOD edge from owner class
        declaration.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .ifPresent(ownerFullName ->
                        extractedEdges.add(EdgeData.of("HAS_METHOD", ownerFullName, methodFullName)));
    }

    private void extractConstructorEdges(ConstructorDeclaration declaration, String methodFullName) {
        // PARAMETER_TYPE edges
        for (int i = 0; i < declaration.getParameters().size(); i++) {
            String paramType = declaration.getParameter(i).getType().asString();
            String resolvedParam = resolveTypeName(paramType, declaration);
            extractedEdges.add(EdgeData.of("PARAMETER_TYPE", methodFullName, resolvedParam, Map.of(
                    "position", i
            )));
        }

        // THROWS edges
        for (var thrownType : declaration.getThrownExceptions()) {
            String resolvedThrown = resolveTypeName(thrownType.asString(), declaration);
            extractedEdges.add(EdgeData.of("THROWS", methodFullName, resolvedThrown));
        }

        // HAS_METHOD edge from owner class
        declaration.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .ifPresent(ownerFullName ->
                        extractedEdges.add(EdgeData.of("HAS_METHOD", ownerFullName, methodFullName)));
    }

    private void extractCallEdges(MethodDeclaration declaration, String callerFullName) {
        declaration.findAll(MethodCallExpr.class).forEach(call -> {
            int lineNumber = call.getBegin().map(p -> p.line).orElse(0);

            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                // Only in-project methods wrap a JavaParser AST node — those become real CALLS edges
                if (resolved instanceof JavaParserMethodDeclaration jpMethod) {
                    MethodDeclaration target = jpMethod.getWrappedNode();
                    List<String> paramTypes = target.getParameters().stream()
                            .map(parameter -> parameter.getType().asString())
                            .toList();
                    String targetFullName = fullName(
                            target.getNameAsString(),
                            target.findAncestor(ClassOrInterfaceDeclaration.class),
                            paramTypes);

                    Map<String, Object> props = new HashMap<>();
                    props.put("lineNumber", lineNumber);
                    props.put("targetType", "resolved");
                    props.put("confidence", 1.0);
                    props.put("callKind", "method");

                    extractedEdges.add(EdgeData.of("CALLS", callerFullName, targetFullName, props));
                }
                // Resolved library/JDK calls are intentionally skipped — no target node exists.
            } catch (Exception e) {
                // Unresolvable symbol (missing dependency, dynamic type, etc.) — skip silently.
                // A second-pass resolver in Sprint 2 can revisit these.
            }
        });
    }

    private String resolveTypeName(String typeName, com.github.javaparser.ast.Node context) {
        // Strip generics for resolution
        String baseName = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;

        // Primitives and common types don't need resolution
        if (isPrimitive(baseName) || baseName.startsWith("java.lang.")) {
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
                .orElseGet(() -> context.findCompilationUnit()
                        .flatMap(cu -> cu.getPackageDeclaration())
                        .map(pkg -> pkg.getNameAsString() + "." + baseName)
                        .orElse(baseName));
    }

    private boolean isPrimitive(String type) {
        return switch (type) {
            case "int", "long", "short", "byte", "float", "double", "boolean", "char", "void" -> true;
            default -> false;
        };
    }

    private NodeData toNodeData(MethodDeclaration declaration) {
        List<String> paramTypes = declaration.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .toList();
        List<String> paramNames = declaration.getParameters().stream()
                .map(parameter -> parameter.getNameAsString())
                .toList();
        List<String> throwsTypes = declaration.getThrownExceptions().stream()
                .map(type -> type.asString())
                .toList();
        RouteMapping routeMapping = routeMapping(declaration.getAnnotations(), classRoutePrefix(declaration));

        Map<String, Object> properties = new HashMap<>();
        properties.put("kind", "METHOD");
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", declaration.isStatic());
        properties.put("abstract", declaration.isAbstract());
        properties.put("final", declaration.isFinal());
        properties.put("synchronized", declaration.isSynchronized());
        properties.put("returnType", declaration.getType().asString());
        properties.put("paramTypes", paramTypes);
        properties.put("paramNames", paramNames);
        properties.put("throwsTypes", throwsTypes);
        properties.put("httpMethod", routeMapping.httpMethod());
        properties.put("routePath", routeMapping.routePath());

        return NodeData.of(
                "Method",
                declaration.getNameAsString(),
                fullName(declaration.getNameAsString(), declaration.findAncestor(ClassOrInterfaceDeclaration.class), paramTypes),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private NodeData toNodeData(ConstructorDeclaration declaration) {
        List<String> paramTypes = declaration.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .toList();
        List<String> paramNames = declaration.getParameters().stream()
                .map(parameter -> parameter.getNameAsString())
                .toList();

        Map<String, Object> properties = new HashMap<>();
        properties.put("kind", "CONSTRUCTOR");
        properties.put("visibility", declaration.getAccessSpecifier().asString());
        properties.put("static", false);
        properties.put("abstract", false);
        properties.put("final", false);
        properties.put("synchronized", false);
        properties.put("returnType", null);
        properties.put("paramTypes", paramTypes);
        properties.put("paramNames", paramNames);
        properties.put("throwsTypes", declaration.getThrownExceptions().stream().map(type -> type.asString()).toList());
        properties.put("httpMethod", null);
        properties.put("routePath", null);

        return NodeData.of(
                "Method",
                "<init>",
                fullName("<init>", declaration.findAncestor(ClassOrInterfaceDeclaration.class), paramTypes),
                filePath(declaration),
                declaration.getBegin().map(position -> position.line).orElse(0),
                declaration.getEnd().map(position -> position.line).orElse(0),
                properties
        );
    }

    private String fullName(String methodName, Optional<ClassOrInterfaceDeclaration> owner, List<String> paramTypes) {
        String ownerName = owner
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .orElse(owner.map(ClassOrInterfaceDeclaration::getNameAsString).orElse("<unknown>"));
        return ownerName + "." + methodName + "(" + String.join(",", paramTypes) + ")";
    }

    private String filePath(com.github.javaparser.ast.Node node) {
        return node.findCompilationUnit()
                .flatMap(compilationUnit -> compilationUnit.getStorage().map(storage -> storage.getPath().toString()))
                .orElse("");
    }

    private String classRoutePrefix(MethodDeclaration declaration) {
        return declaration.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(owner -> owner.getAnnotations().stream()
                        .filter(annotation -> annotation.getName().getIdentifier().equals("RequestMapping"))
                        .findFirst()
                        .map(this::routePath))
                .orElse("");
    }

    private RouteMapping routeMapping(List<AnnotationExpr> annotations, String classPrefix) {
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getName().getIdentifier();
            String routePath = combinePaths(classPrefix, routePath(annotation));
            switch (name) {
                case "GetMapping":
                    return new RouteMapping("GET", routePath);
                case "PostMapping":
                    return new RouteMapping("POST", routePath);
                case "PutMapping":
                    return new RouteMapping("PUT", routePath);
                case "DeleteMapping":
                    return new RouteMapping("DELETE", routePath);
                case "PatchMapping":
                    return new RouteMapping("PATCH", routePath);
                case "RequestMapping":
                    return new RouteMapping(requestMethod(annotation), routePath);
                default:
                    break;
            }
        }
        return new RouteMapping(null, null);
    }

    private String routePath(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr singleMember
                && singleMember.getMemberValue() instanceof StringLiteralExpr literal) {
            return literal.asString();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path"))
                    .map(MemberValuePair::getValue)
                    .filter(StringLiteralExpr.class::isInstance)
                    .map(StringLiteralExpr.class::cast)
                    .map(StringLiteralExpr::asString)
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private String requestMethod(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return null;
        }
        return normal.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("method"))
                .map(pair -> pair.getValue().toString())
                .map(value -> value.substring(value.lastIndexOf('.') + 1))
                .findFirst()
                .orElse(null);
    }

    private String combinePaths(String prefix, String path) {
        String combined = List.of(prefix, path).stream()
                .filter(part -> part != null && !part.isBlank())
                .map(part -> part.startsWith("/") ? part : "/" + part)
                .collect(Collectors.joining());
        return combined.isBlank() ? null : combined.replaceAll("//+", "/");
    }

    private record RouteMapping(String httpMethod, String routePath) {
    }
}
