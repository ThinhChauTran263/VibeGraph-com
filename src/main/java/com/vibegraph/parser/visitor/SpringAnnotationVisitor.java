package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.Signatures;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Detects Spring Boot annotations and produces APIEndpoint nodes + HANDLES_ROUTE / INJECTS edges.
 *
 * <p>Where present, Spring Security authorization annotations
 * ({@code @PreAuthorize}, {@code @Secured}, {@code @RolesAllowed}) are mined for the required role
 * and attached to the APIEndpoint node as a {@code requiredRole} property, so the use case engine can
 * assign the correct actor (Admin vs User) instead of guessing from the URL path. Method-level
 * annotations win over the class-level default.
 */
public class SpringAnnotationVisitor extends VoidVisitorAdapter<Object> {

    private static final Set<String> INJECT_ANNOTATIONS = Set.of("Autowired", "Inject", "Value", "Resource");
    private static final Set<String> SECURITY_ANNOTATIONS = Set.of("PreAuthorize", "Secured", "RolesAllowed");
    // Captures the role token from hasRole('ADMIN'), hasAuthority('ROLE_ADMIN'), "ROLE_ADMIN",
    // @RolesAllowed("ADMIN"), etc. The ROLE_ prefix is optional and stripped by the caller.
    private static final Pattern ROLE_TOKEN = Pattern.compile("(?:ROLE_)?([A-Z][A-Z0-9_]+)");
    // Captures the HTTP verb(s) from @RequestMapping(method = RequestMethod.GET) or
    // method = {RequestMethod.GET, RequestMethod.POST}. Only the REST-relevant verbs are mined.
    private static final Pattern REQUEST_METHOD_TOKEN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b");

    private final List<NodeData> extractedNodes = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        String classFqcn = n.getFullyQualifiedName().orElse(n.getNameAsString());
        String classPrefix = extractRequestMappingPath(n);
        String classRole = extractRole(n.getAnnotations());
        // A plain @Controller renders server-side views (templates/redirects); a @RestController
        // always returns response bodies. GET view routes are presentation, not REST business
        // endpoints, so the use case engine can drop them instead of minting "View Page" goals.
        boolean restController = hasAnnotationNamed(n, "RestController");
        boolean classRendersView = hasAnnotationNamed(n, "Controller") && !restController;

        for (MethodDeclaration method : n.getMethods()) {
            processMethodAnnotations(method, classFqcn, classPrefix, classRole, classRendersView);
        }

        boolean lombokConstructorInjection = hasLombokConstructorInjection(n);
        for (FieldDeclaration field : n.getFields()) {
            processFieldAnnotations(field, classFqcn, n, lombokConstructorInjection);
        }

        processConstructorInjection(n, classFqcn);

        super.visit(n, arg);
    }

    public List<NodeData> getExtractedNodes() {
        return extractedNodes;
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private void processMethodAnnotations(MethodDeclaration method, String classFqcn, String classPrefix,
            String classRole, boolean classRendersView) {
        String methodFqcn = Signatures.method(
                classFqcn,
                method.getNameAsString(),
                method.getParameters().stream()
                        .map(p -> p.getType().asString())
                        .toList());

        // Method-level security overrides the class-level default; fall back to the class role.
        String methodRole = extractRole(method.getAnnotations());
        String requiredRole = methodRole != null ? methodRole : classRole;

        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = annotation.getName().getIdentifier();
            for (String httpMethod : resolveHttpMethods(name, annotation)) {
                String path = combinePaths(classPrefix, extractPath(annotation));
                if (path != null && !path.isBlank()) {
                    String routeId = httpMethod + " " + path;
                    Map<String, Object> routeProps = new HashMap<>();
                    routeProps.put("httpMethod", httpMethod);
                    // Property name must match the route_unique constraint key
                    // (projectId, httpMethod, routePath) in V1__init_schema.cypher.
                    // Using "path" here left routePath null on every APIEndpoint node,
                    // which silently disabled the uniqueness constraint.
                    routeProps.put("routePath", path);
                    if (requiredRole != null) {
                        routeProps.put("requiredRole", requiredRole);
                    }
                    // Mark server-side view (page) routes so the use case engine can treat a GET page
                    // as presentation, not a business goal. Mutating view routes (e.g. a form POST
                    // returning a redirect) are kept — they perform a real business action.
                    if (classRendersView && rendersView(method)) {
                        routeProps.put("view", true);
                    }

                    extractedNodes.add(NodeData.of(
                            "APIEndpoint", routeId, routeId, "",
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

        String resolvedType = TypeReferenceSupport.resolveTypeReference(field.getElementType(), owner).orElse(null);
        if (resolvedType == null) {
            return;
        }
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

    /** Spring stereotype annotations that mark a class as a container-managed bean. */
    private static final Set<String> SPRING_STEREOTYPES = Set.of(
            "Component", "Service", "Repository", "Controller", "RestController",
            "Configuration", "RestControllerAdvice", "ControllerAdvice");
    /** Simple type names that are values/framework objects, never injected business beans. */
    private static final Set<String> NON_BEAN_TYPES = Set.of(
            "String", "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean",
            "Character", "Object", "BigDecimal", "BigInteger");

    private boolean isSpringBean(ClassOrInterfaceDeclaration n) {
        return n.getAnnotations().stream()
                .anyMatch(a -> SPRING_STEREOTYPES.contains(a.getName().getIdentifier()));
    }

    /**
     * Capture constructor-parameter injection - the modern Spring default where a bean declares its
     * dependencies as constructor parameters with no {@code @Autowired} needed. Field {@code @Autowired}
     * is handled by {@link #processFieldAnnotations}; this closes the gap for constructor-injected
     * beans (previously reported as {@code INJECTS = 0} on such projects).
     *
     * <p>Restricted to Spring-managed beans (stereotype annotated) to avoid treating every POJO
     * constructor as injection. When several constructors exist, only the {@code @Autowired}-annotated
     * one is wired (Spring's rule); a single constructor is auto-wired implicitly. Primitive and common
     * value types are skipped.
     */
    private void processConstructorInjection(ClassOrInterfaceDeclaration owner, String classFqcn) {
        if (!isSpringBean(owner)) {
            return;
        }
        List<ConstructorDeclaration> ctors = owner.getConstructors();
        if (ctors.isEmpty()) {
            return;
        }
        ConstructorDeclaration injectable;
        if (ctors.size() == 1) {
            injectable = ctors.get(0);
        } else {
            injectable = ctors.stream()
                    .filter(c -> c.getAnnotations().stream()
                            .anyMatch(a -> a.getName().getIdentifier().equals("Autowired")))
                    .findFirst()
                    .orElse(null);
        }
        if (injectable == null) {
            return;
        }
        for (Parameter param : injectable.getParameters()) {
            if (param.getType().isPrimitiveType()) {
                continue;
            }
            String baseName = param.getType().asString();
            if (baseName.contains("<")) {
                baseName = baseName.substring(0, baseName.indexOf('<'));
            }
            if (NON_BEAN_TYPES.contains(baseName)) {
                continue;
            }
            TypeReferenceSupport.resolveTypeReference(param.getType(), owner)
                    .ifPresent(resolvedType ->
                            extractedEdges.add(EdgeData.of("INJECTS", classFqcn, resolvedType, Map.of(
                                    "annotation", "Constructor",
                                    "lineNumber", injectable.getBegin().map(p -> p.line).orElse(0)
                            ))));
        }
    }

    private boolean hasAnnotationNamed(ClassOrInterfaceDeclaration n, String simpleName) {
        return n.getAnnotations().stream()
                .anyMatch(a -> a.getName().getIdentifier().equals(simpleName));
    }

    /**
     * True when a handler renders a server-side view (returns a template/redirect name) rather than a
     * response body. A method annotated {@code @ResponseBody} returns data, not a view, even on a
     * plain {@code @Controller}.
     */
    private boolean rendersView(MethodDeclaration method) {
        boolean responseBody = method.getAnnotations().stream()
                .anyMatch(a -> a.getName().getIdentifier().equals("ResponseBody"));
        if (responseBody) {
            return false;
        }
        String ret = method.getType().asString();
        String base = ret.contains("<") ? ret.substring(0, ret.indexOf('<')) : ret;
        return switch (base) {
            case "String", "ModelAndView", "View", "RedirectView", "CharSequence" -> true;
            default -> false;
        };
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

    /**
     * Resolve the concrete HTTP verb(s) an endpoint annotation maps to. The dedicated verb mappings
     * ({@code @GetMapping}, ...) each map to one verb. {@code @RequestMapping} is method-agnostic
     * unless it declares {@code method = RequestMethod.X} (or a set); when it does, emit the real
     * verb(s) instead of the generic {@code "REQUEST"} placeholder so a controller written in the
     * older {@code @RequestMapping(method = ...)} style is classified correctly.
     */
    private List<String> resolveHttpMethods(String annotationName, AnnotationExpr annotation) {
        String single = httpMethodFor(annotationName);
        if (single == null) {
            return List.of();
        }
        if (!"REQUEST".equals(single)) {
            return List.of(single);
        }
        List<String> verbs = extractRequestMethods(annotation);
        return verbs.isEmpty() ? List.of("REQUEST") : verbs;
    }

    /** Extract HTTP verbs from a {@code @RequestMapping}'s {@code method = ...} attribute. */
    private List<String> extractRequestMethods(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return List.of();
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (!pair.getNameAsString().equals("method")) {
                continue;
            }
            List<String> verbs = new ArrayList<>();
            Matcher m = REQUEST_METHOD_TOKEN.matcher(pair.getValue().toString());
            while (m.find()) {
                String verb = m.group(1);
                if (!verbs.contains(verb)) {
                    verbs.add(verb);
                }
            }
            return verbs;
        }
        return List.of();
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

    /**
     * Extract the required role from any Spring Security authorization annotation in the set
     * ({@code @PreAuthorize}, {@code @Secured}, {@code @RolesAllowed}). Returns the role token with
     * any {@code ROLE_} prefix stripped and upper-cased (e.g. {@code "ADMIN"}), or {@code null} when
     * no such annotation is present or no role token can be read. When several roles are mentioned,
     * an {@code ADMIN}-like token is preferred so the endpoint maps to the most privileged actor.
     */
    private String extractRole(List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if (!SECURITY_ANNOTATIONS.contains(annotation.getName().getIdentifier())) {
                continue;
            }
            String expr = annotationStringValue(annotation);
            if (expr == null || expr.isBlank()) {
                continue;
            }
            String role = pickRole(expr);
            if (role != null) {
                return role;
            }
        }
        return null;
    }

    /** Read the single string-ish argument of a security annotation (the SpEL or role literal). */
    private String annotationStringValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            if (single.getMemberValue() instanceof StringLiteralExpr literal) {
                return literal.asString();
            }
            return single.getMemberValue().toString();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .map(MemberValuePair::getValue)
                    .filter(StringLiteralExpr.class::isInstance)
                    .map(StringLiteralExpr.class::cast)
                    .map(StringLiteralExpr::asString)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** Pick the most privileged role token from a security expression, preferring ADMIN. */
    private String pickRole(String expr) {
        Matcher m = ROLE_TOKEN.matcher(expr);
        String first = null;
        while (m.find()) {
            String token = m.group(1).toUpperCase(Locale.ROOT);
            // Ignore SpEL keywords that the all-caps regex can otherwise catch.
            if (token.equals("T") || token.equals("AND") || token.equals("OR") || token.equals("ROLE")) {
                continue;
            }
            if (token.contains("ADMIN")) {
                return token;
            }
            if (first == null) {
                first = token;
            }
        }
        return first;
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
