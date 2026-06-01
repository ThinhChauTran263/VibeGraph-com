package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.Signatures;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Detects Spring Boot annotations and produces Route nodes + HANDLES_ROUTE / INJECTS edges.
 */
public class SpringAnnotationVisitor extends VoidVisitorAdapter<Object> {

    private static final Set<String> INJECT_ANNOTATIONS = Set.of("Autowired", "Inject", "Value", "Resource");

    private final List<NodeData> extractedNodes = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        String classFqcn = n.getFullyQualifiedName().orElse(n.getNameAsString());
        String classPrefix = extractRequestMappingPath(n);

        for (MethodDeclaration method : n.getMethods()) {
            processMethodAnnotations(method, classFqcn, classPrefix);
        }

        boolean lombokConstructorInjection = hasLombokConstructorInjection(n);
        for (FieldDeclaration field : n.getFields()) {
            processFieldAnnotations(field, classFqcn, n, lombokConstructorInjection);
        }

        super.visit(n, arg);
    }

    public List<NodeData> getExtractedNodes() {
        return extractedNodes;
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void processMethodAnnotations(MethodDeclaration method, String classFqcn, String classPrefix) {
        String methodFqcn = Signatures.method(
                classFqcn,
                method.getNameAsString(),
                method.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .toList());

        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = annotation.getName().getIdentifier();
            String httpMethod = httpMethodFor(name);
            if (httpMethod != null) {
                String path = combinePaths(classPrefix, extractPath(annotation));
                if (path != null && !path.isBlank()) {
                    String routeId = httpMethod + " " + path;
                    Map<String, Object> routeProps = new HashMap<>();
                    routeProps.put("httpMethod", httpMethod);
                    // Property name must match the route_unique constraint key
                    // (projectId, httpMethod, routePath) in V1__init_schema.cypher.
                    // Using "path" here left routePath null on every Route node,
                    // which silently disabled the uniqueness constraint.
                    routeProps.put("routePath", path);

                    extractedNodes.add(NodeData.of(
                            "Route", routeId, routeId, "",
                            method.getBegin().map(p -> p.line).orElse(0),
                            method.getEnd().map(p -> p.line).orElse(0),
                            routeProps));

                    extractedEdges.add(EdgeData.of("HANDLES_ROUTE", methodFqcn, routeId, Map.of(
                            "httpMethod", httpMethod,
                            "lineNumber", method.getBegin().map(p -> p.line).orElse(0)
                    )));
                }
            }
        }
    }

    private void processFieldAnnotations(FieldDeclaration field, String classFqcn,
                                         ClassOrInterfaceDeclaration owner, boolean lombokConstructorInjection) {
        boolean isAnnotated = false;
        String annotationName = null;
        for (AnnotationExpr annotation : field.getAnnotations()) {
            if (INJECT_ANNOTATIONS.contains(annotation.getName().getIdentifier())) {
                isAnnotated = true;
                annotationName = annotation.getName().getIdentifier();
                break;
            }
        }

        boolean isLombokInjected = lombokConstructorInjection
                && field.isFinal()
                && !field.isStatic()
                && field.getAnnotations().stream()
                        .noneMatch(a -> a.getName().getIdentifier().equals("Value"));

        if (!isAnnotated && !isLombokInjected) {
            return;
        }

        String rawType = field.getElementType().asString();
        String resolvedType = resolveTypeName(rawType, owner);
        String injectionKind = isAnnotated ? annotationName : "RequiredArgsConstructor";

        extractedEdges.add(EdgeData.of("INJECTS", classFqcn, resolvedType, Map.of(
                "annotation", injectionKind,
                "lineNumber", field.getBegin().map(p -> p.line).orElse(0)
        )));
    }

    private boolean hasLombokConstructorInjection(ClassOrInterfaceDeclaration n) {
        return n.getAnnotations().stream()
                .map(a -> a.getName().getIdentifier())
                .anyMatch(name -> name.equals("RequiredArgsConstructor") || name.equals("AllArgsConstructor"));
    }

    private String resolveTypeName(String typeName, ClassOrInterfaceDeclaration context) {
        // Strip generics
        String baseName = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;
        if (baseName.contains(".")) {
            return baseName;
        }
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

    private String httpMethodFor(String annotationName) {
        return switch (annotationName) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            case "RequestMapping" -> "REQUEST";
            default -> null;
        };
    }

    private String extractRequestMappingPath(ClassOrInterfaceDeclaration n) {
        return n.getAnnotations().stream()
                .filter(a -> a.getName().getIdentifier().equals("RequestMapping"))
                .findFirst()
                .map(this::extractPath)
                .orElse("");
    }

    private String extractPath(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single
                && single.getMemberValue() instanceof StringLiteralExpr literal) {
            return literal.asString();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value") || p.getNameAsString().equals("path"))
                    .map(MemberValuePair::getValue)
                    .filter(StringLiteralExpr.class::isInstance)
                    .map(StringLiteralExpr.class::cast)
                    .map(StringLiteralExpr::asString)
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private String combinePaths(String prefix, String path) {
        if ((prefix == null || prefix.isBlank()) && (path == null || path.isBlank())) {
            return null;
        }
        String p = (prefix == null ? "" : prefix);
        String s = (path == null ? "" : path);
        String combined = (p.startsWith("/") ? p : "/" + p) + (s.startsWith("/") ? s : "/" + s);
        return combined.replaceAll("//+", "/");
    }
}
