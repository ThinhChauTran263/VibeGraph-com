package com.vibegraph.parser.visitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.MethodSkipPolicy;
import com.vibegraph.parser.ProjectSymbolRegistry;
import com.vibegraph.parser.Signatures;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;

/**
 * Extracts pure Spring implicit-flow facts from syntax.
 *
 * <p>This visitor does not perform global joins. It only enriches method nodes with
 * annotation facts and emits direct event publication/listener facts that later
 * post-processors can join once the full project graph is available.
 */
public class SpringImplicitFlowVisitor extends VoidVisitorAdapter<Object> {

    private static final Set<String> EVENT_LISTENER_ANNOTATIONS = Set.of(
            "EventListener", "TransactionalEventListener");
    private static final Set<String> ASYNC_ANNOTATIONS = Set.of("Async");
    private static final Set<String> SCHEDULED_ANNOTATIONS = Set.of("Scheduled");
    private static final String PUBLISHER_SIMPLE_TYPE = "ApplicationEventPublisher";

    private final List<EdgeData> extractedEdges = new ArrayList<>();
    private final Map<String, Map<String, Object>> methodProperties = new LinkedHashMap<>();
    private final ArrayDeque<Set<String>> publisherVariables = new ArrayDeque<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        publisherVariables.push(publisherVariableNames(n));
        super.visit(n, arg);
        publisherVariables.pop();
    }

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        if (MethodSkipPolicy.shouldSkip(n)) {
            return;
        }
        String methodFullName = methodFullName(n).orElse(null);
        if (methodFullName == null) {
            super.visit(n, arg);
            return;
        }

        Map<String, Object> props = annotationProperties(n.getAnnotations());
        if (!props.isEmpty()) {
            methodProperties.put(methodFullName, props);
        }

        extractListenerFacts(n, methodFullName);
        extractPublisherFacts(n, methodFullName);
        super.visit(n, arg);
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    public Map<String, Map<String, Object>> getMethodProperties() {
        return methodProperties;
    }

    private Set<String> publisherVariableNames(ClassOrInterfaceDeclaration owner) {
        Set<String> names = new LinkedHashSet<>();
        owner.getFields().forEach(field -> {
            if (isApplicationEventPublisherType(field.getElementType().asString())) {
                field.getVariables().forEach(variable -> names.add(variable.getNameAsString()));
            }
        });
        owner.getConstructors().forEach(constructor -> {
            for (Parameter parameter : constructor.getParameters()) {
                if (isApplicationEventPublisherType(parameter.getType().asString())) {
                    names.add(parameter.getNameAsString());
                }
            }
        });
        return names;
    }

    private Map<String, Object> annotationProperties(List<AnnotationExpr> annotations) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getName().getIdentifier();
            if (SCHEDULED_ANNOTATIONS.contains(name)) {
                props.put("isScheduled", true);
                props.put("entrypoint", true);
                props.put("entrypointKind", "SCHEDULED");
            }
            if (ASYNC_ANNOTATIONS.contains(name)) {
                props.put("isAsync", true);
            }
        }
        return props;
    }

    private void extractListenerFacts(MethodDeclaration method, String methodFullName) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String annotationName = annotation.getName().getIdentifier();
            if (!EVENT_LISTENER_ANNOTATIONS.contains(annotationName)) {
                continue;
            }
            Optional<String> eventType = listenerEventType(annotation, method);
            if (eventType.isEmpty()) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("annotation", annotationName);
            props.put("lineNumber", annotation.getBegin().map(p -> p.line).orElse(0));
            annotationAttribute(annotation, "phase").ifPresent(phase -> props.put("phase", phase));
            annotationAttribute(annotation, "condition").ifPresent(condition -> props.put("condition", condition));
            extractedEdges.add(EdgeData.of("LISTENS_EVENT", methodFullName, eventType.get(), props));
        }
    }

    private Optional<String> listenerEventType(AnnotationExpr annotation, MethodDeclaration method) {
        Optional<String> explicit = eventTypeFromAnnotationValue(annotation, "value")
                .or(() -> eventTypeFromAnnotationValue(annotation, "classes"));
        if (explicit.isPresent()) {
            return explicit;
        }
        if (!method.getParameters().isEmpty()) {
            return TypeReferenceSupport.resolveTypeReference(method.getParameter(0).getType(), method);
        }
        return Optional.empty();
    }

    private Optional<String> eventTypeFromAnnotationValue(AnnotationExpr annotation, String attributeName) {
        if (annotation instanceof SingleMemberAnnotationExpr single && "value".equals(attributeName)) {
            return eventTypeFromExpression(single.getMemberValue(), annotation);
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals(attributeName)) {
                    return eventTypeFromExpression(pair.getValue(), annotation);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> eventTypeFromExpression(Expression expression, com.github.javaparser.ast.Node context) {
        if (expression instanceof ClassExpr classExpr) {
            return TypeReferenceSupport.resolveTypeReference(classExpr.getType(), context);
        }
        if (expression instanceof ArrayInitializerExpr arrayInitializerExpr) {
            for (Expression value : arrayInitializerExpr.getValues()) {
                Optional<String> resolved = eventTypeFromExpression(value, context);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    private void extractPublisherFacts(MethodDeclaration method, String methodFullName) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"publishEvent".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
                return;
            }
            if (isNestedExecutableBody(call, method)) {
                return;
            }
            if (!isApplicationEventPublisherCall(call)) {
                return;
            }
            Optional<String> eventType = publishedEventType(call.getArgument(0));
            if (eventType.isEmpty()) {
                return;
            }
            extractedEdges.add(EdgeData.of("PUBLISHES_EVENT", methodFullName, eventType.get(), Map.of(
                    "lineNumber", call.getBegin().map(p -> p.line).orElse(0)
            )));
        });
    }

    private boolean isApplicationEventPublisherCall(MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return false;
        }
        Expression scope = call.getScope().get();
        try {
            var type = scope.calculateResolvedType();
            if (type.isReferenceType()
                    && isApplicationEventPublisherType(type.asReferenceType().getQualifiedName())) {
                return true;
            }
        } catch (Exception ignored) {
            // Fall back to syntax-declared publisher field/parameter names below.
        }
        if (scope instanceof NameExpr nameExpr) {
            return publisherVariables.peek() != null
                    && publisherVariables.peek().contains(nameExpr.getNameAsString());
        }
        return false;
    }

    private boolean isNestedExecutableBody(com.github.javaparser.ast.Node node, MethodDeclaration owner) {
        Optional<com.github.javaparser.ast.Node> current = node.getParentNode();
        while (current.isPresent()) {
            com.github.javaparser.ast.Node candidate = current.get();
            if (candidate == owner) {
                return false;
            }
            if (candidate instanceof LambdaExpr
                    || candidate instanceof MethodDeclaration
                    || candidate instanceof ConstructorDeclaration
                    || (candidate instanceof ObjectCreationExpr creation && creation.getAnonymousClassBody().isPresent())) {
                return true;
            }
            current = candidate.getParentNode();
        }
        return false;
    }

    private Optional<String> publishedEventType(Expression argument) {
        if (argument instanceof ObjectCreationExpr creation) {
            return TypeReferenceSupport.resolveTypeReference(creation.getType(), creation);
        }
        try {
            var resolvedType = argument.calculateResolvedType();
            if (resolvedType.isReferenceType()) {
                String candidate = resolvedType.asReferenceType().getQualifiedName();
                if (isVerifiedProjectType(candidate)) {
                    return Optional.of(candidate);
                }
            }
        } catch (Exception ignored) {
            // Variable type could not be resolved; do not guess.
        }
        return Optional.empty();
    }

    private boolean isVerifiedProjectType(String candidate) {
        if (candidate == null || candidate.isBlank() || TypeReferenceSupport.shouldSkipImportedType(candidate)) {
            return false;
        }
        return ProjectSymbolRegistry.current()
                .map(registry -> registry.contains(candidate))
                .orElse(true);
    }

    private Optional<String> methodFullName(MethodDeclaration method) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                .map(owner -> Signatures.method(
                        owner,
                        method.getNameAsString(),
                        method.getParameters().stream().map(p -> p.getType().asString()).toList()));
    }

    private Optional<String> annotationAttribute(AnnotationExpr annotation, String attributeName) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return Optional.empty();
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (pair.getNameAsString().equals(attributeName)) {
                return Optional.of(cleanAnnotationValue(pair.getValue().toString()));
            }
        }
        return Optional.empty();
    }

    private String cleanAnnotationValue(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private boolean isApplicationEventPublisherType(String typeName) {
        return typeName != null
                && (PUBLISHER_SIMPLE_TYPE.equals(typeName)
                || typeName.endsWith("." + PUBLISHER_SIMPLE_TYPE));
    }
}
