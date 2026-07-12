package com.vibegraph.parser.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.vibegraph.parser.Signatures;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Extracts Method nodes and method-related edges (CALLS, RETURNS, PARAMETER_TYPE, THROWS) from AST.
 */
public class MethodVisitor extends VoidVisitorAdapter<Object> {

    private final List<NodeData> extractedMethods = new ArrayList<>();
    private final List<NodeData> extractedVariables = new ArrayList<>();
    private final List<EdgeData> extractedEdges = new ArrayList<>();
    /** When false (default), no LocalVariable nodes or READS/WRITES/CATCHES edges are emitted. */
    private final boolean deepCpg;

    public MethodVisitor() {
        this(false);
    }

    public MethodVisitor(boolean deepCpg) {
        this.deepCpg = deepCpg;
    }

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        NodeData methodNode = toNodeData(n);
        extractedMethods.add(methodNode);
        extractMethodEdges(n, methodNode.fullName());
        extractCallEdges(n, methodNode.fullName());
        extractInstantiations(n, methodNode.fullName());
        extractOverrides(n, methodNode.fullName());
        extractMethodReferenceEdges(n, methodNode.fullName());
        if (deepCpg) {
            extractDataFlow(n, methodNode.fullName(), n.getParameters());
            extractCatches(n, methodNode.fullName());
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(ConstructorDeclaration n, Object arg) {
        NodeData methodNode = toNodeData(n);
        extractedMethods.add(methodNode);
        extractConstructorEdges(n, methodNode.fullName());
        extractInstantiations(n, methodNode.fullName());
        extractCallEdges(n, methodNode.fullName());
        extractMethodReferenceEdges(n, methodNode.fullName());
        if (deepCpg) {
            extractDataFlow(n, methodNode.fullName(), n.getParameters());
            extractCatches(n, methodNode.fullName());
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(LambdaExpr n, Object arg) {
        super.visit(n, arg);
    }

    public List<NodeData> getExtractedMethods() {
        return extractedMethods;
    }

    /** LocalVariable nodes (parameters + local declarations), only when deep CPG is enabled. */
    public List<NodeData> getExtractedVariables() {
        return extractedVariables;
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

    private void extractCallEdges(com.github.javaparser.ast.Node declaration, String callerFullName) {
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
                    props.put("callKind", jpMethod.isStatic() ? "static" : "method");

                    extractedEdges.add(EdgeData.of("CALLS", callerFullName, targetFullName, props));
                }
                // Resolved library/JDK calls are intentionally skipped — no target node exists.
            } catch (Exception e) {
                // Unresolvable symbol (missing dependency, dynamic type, etc.) — emit low-confidence unresolved stub.
                String rawTarget = call.getScope().map(s -> s.toString() + ".").orElse("") + call.getNameAsString();
                
                String ownerName = "<unresolved>";
                if (call.getScope().isPresent()) {
                    Expression scope = call.getScope().get();
                    try {
                        var resolvedType = scope.calculateResolvedType();
                        if (resolvedType.isReferenceType()) {
                            ownerName = resolvedType.asReferenceType().getQualifiedName();
                        }
                    } catch (Exception ex) {
                        ownerName = resolveTypeName(scope.toString(), call);
                    }
                } else {
                    ownerName = declaration.findAncestor(ClassOrInterfaceDeclaration.class)
                            .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                            .orElse("<unknown>");
                }

                List<String> paramTypes = new ArrayList<>();
                for (Expression argExpr : call.getArguments()) {
                    try {
                        var resolvedType = argExpr.calculateResolvedType();
                        paramTypes.add(resolvedType.describe());
                    } catch (Exception ex) {
                        paramTypes.add("?");
                    }
                }

                String targetFullName = Signatures.method(ownerName, call.getNameAsString(), paramTypes);

                Map<String, Object> props = new HashMap<>();
                props.put("lineNumber", lineNumber);
                props.put("targetType", "unresolved");
                props.put("confidence", 0.3);
                props.put("rawTarget", rawTarget);
                props.put("callKind", "method");

                extractedEdges.add(EdgeData.of("CALLS", callerFullName, targetFullName, props));
            }
        });
    }

    private void extractMethodReferenceEdges(com.github.javaparser.ast.Node declaration, String callerFullName) {
        declaration.findAll(MethodReferenceExpr.class).forEach(ref -> {
            int lineNumber = ref.getBegin().map(p -> p.line).orElse(0);
            String identifier = ref.getIdentifier();
            String callKind = "new".equals(identifier) ? "constructor" : "method";

            try {
                var resolved = ref.resolve();
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
                    props.put("callKind", callKind);

                    extractedEdges.add(EdgeData.of("CALLS", callerFullName, targetFullName, props));
                }
            } catch (Exception e) {
                // Unresolved method reference — emit low-confidence stub.
                String rawTarget = ref.getScope().toString() + "::" + identifier;
                
                String ownerName = "<unresolved>";
                try {
                    var resolvedType = ref.getScope().calculateResolvedType();
                    if (resolvedType.isReferenceType()) {
                        ownerName = resolvedType.asReferenceType().getQualifiedName();
                    }
                } catch (Exception ex) {
                    ownerName = resolveTypeName(ref.getScope().toString(), ref);
                }

                String targetFullName = Signatures.method(ownerName, identifier, List.of());

                Map<String, Object> props = new HashMap<>();
                props.put("lineNumber", lineNumber);
                props.put("targetType", "unresolved");
                props.put("confidence", 0.3);
                props.put("rawTarget", rawTarget);
                props.put("callKind", callKind);

                extractedEdges.add(EdgeData.of("CALLS", callerFullName, targetFullName, props));
            }
        });
    }

    private void extractInstantiations(com.github.javaparser.ast.Node body, String callerFullName) {
        body.findAll(ObjectCreationExpr.class).forEach(creation -> {
            int lineNumber = creation.getBegin().map(p -> p.line).orElse(0);
            String targetFullName = resolveInstantiatedType(creation);
            if (targetFullName != null && !targetFullName.isBlank()) {
                extractedEdges.add(EdgeData.of("INSTANTIATES", callerFullName, targetFullName, Map.of(
                        "lineNumber", lineNumber
                )));
            }
        });
    }

    /**
     * Resolve the instantiated type of a {@code new X(...)} expression. Best-effort:
     * the symbol solver gives the qualified name when resolvable, otherwise we fall
     * back to import/same-package resolution. Out-of-project targets become External
     * stubs at persistence time.
     */
    private String resolveInstantiatedType(ObjectCreationExpr creation) {
        try {
            var resolved = creation.getType().resolve();
            if (resolved.isReferenceType()) {
                return resolved.asReferenceType().getQualifiedName();
            }
        } catch (Exception e) {
            // Unresolvable — fall back to lexical resolution below.
        }
        return resolveTypeName(creation.getType().getNameAsString(), creation);
    }

    /**
     * Conservatively emit an OVERRIDES edge (overriding method -> overridden method)
     * ONLY when the overridden method can be resolved to an in-project
     * (JavaParser-backed) method with a matching signature in an ancestor type.
     * If the type hierarchy cannot be fully resolved (external/unsolved supertype),
     * nothing is emitted — we never infer OVERRIDES from {@code @Override} alone.
     */
    private void extractOverrides(MethodDeclaration declaration, String methodFullName) {
        if (declaration.isStatic() || declaration.isPrivate()) {
            return;
        }
        try {
            ResolvedMethodDeclaration resolved = declaration.resolve();
            ResolvedReferenceTypeDeclaration declaringType = resolved.declaringType();
            for (ResolvedReferenceType ancestor : declaringType.getAllAncestors()) {
                Optional<ResolvedReferenceTypeDeclaration> ancestorDecl = ancestor.getTypeDeclaration();
                if (ancestorDecl.isEmpty()) {
                    continue;
                }
                for (ResolvedMethodDeclaration candidate : ancestorDecl.get().getDeclaredMethods()) {
                    if (overrides(resolved, candidate) && candidate instanceof JavaParserMethodDeclaration jp) {
                        MethodDeclaration target = jp.getWrappedNode();
                        List<String> paramTypes = target.getParameters().stream()
                                .map(parameter -> parameter.getType().asString())
                                .toList();
                        String targetFullName = Signatures.method(
                                ancestorDecl.get().getQualifiedName(),
                                target.getNameAsString(),
                                paramTypes);
                        extractedEdges.add(EdgeData.of("OVERRIDES", methodFullName, targetFullName));
                        return; // one OVERRIDES edge per method is sufficient
                    }
                }
            }
        } catch (Exception e) {
            // Unresolvable hierarchy — conservatively emit nothing.
        }
    }

    private boolean overrides(ResolvedMethodDeclaration overriding, ResolvedMethodDeclaration candidate) {
        if (!overriding.getName().equals(candidate.getName())) {
            return false;
        }
        if (overriding.getNumberOfParams() != candidate.getNumberOfParams()) {
            return false;
        }
        try {
            for (int i = 0; i < overriding.getNumberOfParams(); i++) {
                if (!overriding.getParam(i).describeType().equals(candidate.getParam(i).describeType())) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // ---- Phase 3: body-level CPG (READS / WRITES / CATCHES + LocalVariable nodes) ----
    // Only invoked when deepCpg is enabled. Conservative and intra-procedural: no
    // cross-method data-flow, no execution order (STEP_IN_FLOW is future Phase 4).

    private void extractDataFlow(Node owner, String ownerFullName, NodeList<Parameter> parameters) {
        ClassOrInterfaceDeclaration enclosing = owner.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        String classFqcn = enclosing != null
                ? enclosing.getFullyQualifiedName().orElse(enclosing.getNameAsString())
                : null;
        Set<String> fieldNames = declaredFieldNames(enclosing);

        // Scope: parameters + local declarations -> LocalVariable node ids.
        Map<String, String> scope = new LinkedHashMap<>();
        for (Parameter parameter : parameters) {
            String id = addVariableNode(ownerFullName, parameter.getNameAsString(),
                    parameter.getType().asString(), "parameter",
                    parameter.getBegin().map(p -> p.line).orElse(0));
            scope.put(parameter.getNameAsString(), id);
        }
        for (VariableDeclarationExpr vde : owner.findAll(VariableDeclarationExpr.class)) {
            if (!inOwner(vde, owner)) {
                continue;
            }
            for (VariableDeclarator v : vde.getVariables()) {
                String id = addVariableNode(ownerFullName, v.getNameAsString(),
                        v.getType().asString(), "local",
                        v.getBegin().map(p -> p.line).orElse(0));
                scope.put(v.getNameAsString(), id);
            }
        }

        Set<String> reads = new LinkedHashSet<>();
        Set<String> writes = new LinkedHashSet<>();

        // Declaration with initializer => the declared variable is written.
        for (VariableDeclarationExpr vde : owner.findAll(VariableDeclarationExpr.class)) {
            if (!inOwner(vde, owner)) {
                continue;
            }
            for (VariableDeclarator v : vde.getVariables()) {
                if (v.getInitializer().isPresent()) {
                    String id = scope.get(v.getNameAsString());
                    if (id != null) {
                        writes.add(id);
                    }
                }
            }
        }

        // Assignment targets are written (= / += / -= / ... ).
        for (AssignExpr assign : owner.findAll(AssignExpr.class)) {
            if (!inOwner(assign, owner)) {
                continue;
            }
            String id = resolveTarget(assign.getTarget(), scope, fieldNames, classFqcn);
            if (id != null) {
                writes.add(id);
            }
        }

        // ++ / -- operands are written (also read, via the general pass below).
        for (UnaryExpr unary : owner.findAll(UnaryExpr.class)) {
            if (!inOwner(unary, owner) || !isIncrementOrDecrement(unary)) {
                continue;
            }
            String id = resolveTarget(unary.getExpression(), scope, fieldNames, classFqcn);
            if (id != null) {
                writes.add(id);
            }
        }

        // Reads: every resolvable name/this-field that is NOT a plain `=` write target.
        for (NameExpr name : owner.findAll(NameExpr.class)) {
            if (!inOwner(name, owner) || isPlainAssignTarget(name)) {
                continue;
            }
            String id = resolveName(name.getNameAsString(), scope, fieldNames, classFqcn);
            if (id != null) {
                reads.add(id);
            }
        }
        for (FieldAccessExpr fieldAccess : owner.findAll(FieldAccessExpr.class)) {
            if (!inOwner(fieldAccess, owner) || classFqcn == null) {
                continue;
            }
            if (!(fieldAccess.getScope() instanceof ThisExpr) || isPlainAssignTarget(fieldAccess)) {
                continue;
            }
            reads.add(classFqcn + "." + fieldAccess.getNameAsString());
        }

        // Emit deduped edges: at most one READS / one WRITES per target per method.
        for (String target : reads) {
            extractedEdges.add(EdgeData.of("READS", ownerFullName, target));
        }
        for (String target : writes) {
            extractedEdges.add(EdgeData.of("WRITES", ownerFullName, target));
        }
    }

    private void extractCatches(Node owner, String ownerFullName) {
        for (CatchClause catchClause : owner.findAll(CatchClause.class)) {
            if (!inOwner(catchClause, owner)) {
                continue;
            }
            int line = catchClause.getBegin().map(p -> p.line).orElse(0);
            Type type = catchClause.getParameter().getType();
            List<Type> types = new ArrayList<>();
            if (type instanceof UnionType union) {
                union.getElements().forEach(types::add); // multi-catch -> one edge per type
            } else {
                types.add(type);
            }
            for (Type exceptionType : types) {
                String resolved = resolveTypeName(exceptionType.asString(), catchClause);
                extractedEdges.add(EdgeData.of("CATCHES", ownerFullName, resolved, Map.of(
                        "lineNumber", line
                )));
            }
        }
    }

    /** True when {@code node}'s nearest enclosing callable is {@code owner} (lambdas are transparent; nested type methods are not). */
    private boolean inOwner(Node node, Node owner) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node candidate = current.get();
            if (candidate == owner) {
                return true;
            }
            if (candidate instanceof MethodDeclaration || candidate instanceof ConstructorDeclaration) {
                return false;
            }
            current = candidate.getParentNode();
        }
        return false;
    }

    private Set<String> declaredFieldNames(ClassOrInterfaceDeclaration enclosing) {
        Set<String> names = new LinkedHashSet<>();
        if (enclosing != null) {
            enclosing.getFields().forEach(field ->
                    field.getVariables().forEach(v -> names.add(v.getNameAsString())));
        }
        return names;
    }

    /** Create (deduped) a LocalVariable node and return its stable id (methodId#name@line). */
    private String addVariableNode(String ownerFullName, String name, String declaredType, String kind, int line) {
        String id = ownerFullName + "#" + name + "@" + line;
        boolean exists = extractedVariables.stream().anyMatch(v -> v.fullName().equals(id));
        if (!exists) {
            Map<String, Object> props = new HashMap<>();
            props.put("declaredType", declaredType);
            props.put("kind", kind);
            // Empty filePath: a LocalVariable is connected via READS/WRITES, not via a
            // File-[:DEFINES] edge, so it must not match fileDefinesEdges by path.
            extractedVariables.add(NodeData.of("LocalVariable", name, id, "", line, line, props));
        }
        return id;
    }

    private boolean isIncrementOrDecrement(UnaryExpr unary) {
        return switch (unary.getOperator()) {
            case PREFIX_INCREMENT, POSTFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_DECREMENT -> true;
            default -> false;
        };
    }

    private boolean isPlainAssignTarget(Expression expr) {
        return expr.getParentNode()
                .filter(AssignExpr.class::isInstance)
                .map(AssignExpr.class::cast)
                .filter(assign -> assign.getOperator() == AssignExpr.Operator.ASSIGN)
                .map(assign -> assign.getTarget() == expr)
                .orElse(false);
    }

    private String resolveTarget(Expression target, Map<String, String> scope, Set<String> fieldNames, String classFqcn) {
        if (target instanceof NameExpr name) {
            return resolveName(name.getNameAsString(), scope, fieldNames, classFqcn);
        }
        if (target instanceof FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof ThisExpr && classFqcn != null) {
            return classFqcn + "." + fieldAccess.getNameAsString();
        }
        return null; // arr[i], obj.field, etc. — ambiguous target, intentionally skipped
    }

    private String resolveName(String simpleName, Map<String, String> scope, Set<String> fieldNames, String classFqcn) {
        if (scope.containsKey(simpleName)) {
            return scope.get(simpleName);
        }
        if (classFqcn != null && fieldNames.contains(simpleName)) {
            return classFqcn + "." + simpleName;
        }
        return null;
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
                .orElseGet(() -> {
                    // Implicitly-imported java.lang types must not be mis-qualified to the
                    // current package (e.g. String -> com.app.String).
                    if (JAVA_LANG_TYPES.contains(baseName)) {
                        return "java.lang." + baseName;
                    }
                    return context.findCompilationUnit()
                            .flatMap(cu -> cu.getPackageDeclaration())
                            .map(pkg -> pkg.getNameAsString() + "." + baseName)
                            .orElse(baseName);
                });
    }

    private boolean isPrimitive(String type) {
        return switch (type) {
            case "int", "long", "short", "byte", "float", "double", "boolean", "char", "void" -> true;
            default -> false;
        };
    }

    /** Commonly-used implicitly-imported {@code java.lang} types, used to avoid mis-qualifying
     * them to the current package when they appear unqualified and without an explicit import. */
    private static final java.util.Set<String> JAVA_LANG_TYPES = java.util.Set.of(
            "String", "Object", "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean",
            "Character", "Number", "CharSequence", "StringBuilder", "StringBuffer", "Math", "System",
            "Thread", "Runnable", "Iterable", "Comparable", "Cloneable", "Class", "Enum", "Void",
            "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
            "IllegalStateException", "NullPointerException", "UnsupportedOperationException");

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
                "Constructor",
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
        return Signatures.method(ownerName, methodName, paramTypes);
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
