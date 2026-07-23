package com.vibegraph.parser;

import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.type.WildcardType;

/**
 * Shared type-resolution helper for parser visitors.
 *
 * <p>It unwraps common generic containers to their inner domain type and skips
 * primitive/common/framework types that should not become graph targets.
 */
public final class TypeReferenceSupport {

    private static final Set<String> SKIPPED_SIMPLE_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Short", "Byte", "Double", "Float", "Boolean",
            "Character", "Number", "CharSequence", "StringBuilder", "StringBuffer", "Math", "System",
            "Thread", "Runnable", "Iterable", "Comparable", "Cloneable", "Class", "Enum", "Void",
            "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
            "IllegalStateException", "NullPointerException", "UnsupportedOperationException",
            "HttpStatus", "ResponseEntity", "HttpEntity", "Optional", "List", "Set", "Map",
            "Collection", "Stream", "Future", "CompletableFuture", "Mono", "Flux",
            "Page", "Slice", "OptionalInt", "OptionalLong", "OptionalDouble", "BigDecimal",
            "BigInteger", "Supplier", "Function", "Predicate", "Consumer", "BiFunction");

    private static final Set<String> SKIPPED_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.", "org.springframework.", "org.slf4j.", "lombok.");

    private static final Set<String> CONTAINER_SIMPLE_TYPES = Set.of(
            "Collection", "Iterable", "List", "Set", "Map", "Optional", "ResponseEntity", "HttpEntity",
            "Stream", "Future", "CompletableFuture", "Mono", "Flux", "Page", "Slice",
            "OptionalInt", "OptionalLong", "OptionalDouble");

    private TypeReferenceSupport() {
    }

    public static Optional<String> resolveTypeReference(Type type, Node context) {
        if (type == null || type instanceof VoidType || type instanceof PrimitiveType || type instanceof UnknownType) {
            return Optional.empty();
        }
        if (type instanceof ArrayType arrayType) {
            return resolveTypeReference(arrayType.getComponentType(), context);
        }
        if (type instanceof WildcardType wildcardType) {
            if (wildcardType.getExtendedType().isPresent()) {
                return resolveTypeReference(wildcardType.getExtendedType().get(), context);
            }
            if (wildcardType.getSuperType().isPresent()) {
                return resolveTypeReference(wildcardType.getSuperType().get(), context);
            }
            return Optional.empty();
        }
        if (type instanceof UnionType unionType) {
            for (Type element : unionType.getElements()) {
                Optional<String> resolved = resolveTypeReference(element, context);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            return Optional.empty();
        }
        if (type instanceof IntersectionType intersectionType) {
            for (Type element : intersectionType.getElements()) {
                Optional<String> resolved = resolveTypeReference(element, context);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            return Optional.empty();
        }
        if (type instanceof ClassOrInterfaceType classType) {
            if (classType.getTypeArguments().isPresent() && isContainerType(classType.getNameAsString())) {
                for (Type typeArgument : classType.getTypeArguments().get()) {
                    Optional<String> resolvedArgument = resolveTypeReference(typeArgument, context);
                    if (resolvedArgument.isPresent()) {
                        return resolvedArgument;
                    }
                }
            }
            return resolveSimpleType(classType.getNameAsString(), context);
        }
        return resolveSimpleType(type.asString(), context);
    }

    public static boolean shouldSkipImportedType(String importedName) {
        return resolveImportedType(importedName).isEmpty();
    }

    private static Optional<String> resolveImportedType(String importedName) {
        if (importedName == null || importedName.isBlank()) {
            return Optional.empty();
        }
        if (shouldSkipSimpleName(simpleName(importedName)) || hasSkippedPrefix(importedName)) {
            return Optional.empty();
        }
        return Optional.of(importedName);
    }

    private static Optional<String> resolveSimpleType(String simpleName, Node context) {
        if (simpleName == null || simpleName.isBlank() || shouldSkipSimpleName(simpleName)) {
            return Optional.empty();
        }
        if (simpleName.contains(".")) {
            return hasSkippedPrefix(simpleName) ? Optional.empty() : Optional.of(simpleName);
        }

        return context.findCompilationUnit()
                .flatMap(cu -> cu.getImports().stream()
                        .filter(imp -> !imp.isAsterisk())
                        .filter(imp -> imp.getName().getIdentifier().equals(simpleName))
                        .findFirst()
                        .map(imp -> imp.getNameAsString()))
                .map(imported -> shouldSkipImportedType(imported) ? null : imported)
                .flatMap(Optional::ofNullable)
                .or(() -> {
                    return context.findCompilationUnit()
                            .flatMap(cu -> cu.getPackageDeclaration())
                            .map(pkg -> pkg.getNameAsString() + "." + simpleName)
                            .filter(resolved -> !shouldSkipImportedType(resolved));
                })
                .or(() -> Optional.of(simpleName));
    }

    private static boolean shouldSkipSimpleName(String simpleName) {
        return isTypeVariable(simpleName) || SKIPPED_SIMPLE_TYPES.contains(simpleName);
    }

    private static boolean isContainerType(String simpleName) {
        return CONTAINER_SIMPLE_TYPES.contains(simpleName);
    }

    private static boolean hasSkippedPrefix(String typeName) {
        return SKIPPED_PREFIXES.stream().anyMatch(typeName::startsWith);
    }

    private static boolean isTypeVariable(String name) {
        return name != null && name.length() == 1 && Character.isUpperCase(name.charAt(0));
    }

    private static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot >= 0 ? typeName.substring(dot + 1) : typeName;
    }
}
