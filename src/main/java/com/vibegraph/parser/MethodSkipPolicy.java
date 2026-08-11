package com.vibegraph.parser;

import java.util.Set;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;

/**
 * Central routine-member skip policy for parser visitors.
 */
public final class MethodSkipPolicy {

    private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

    private MethodSkipPolicy() {
    }

    public static boolean shouldSkip(MethodDeclaration declaration) {
        if (declaration == null) {
            return true;
        }
        String name = declaration.getNameAsString();
        if (OBJECT_METHODS.contains(name)) {
            return true;
        }
        if (name.startsWith("get") || name.startsWith("is")) {
            return isSimpleGetter(declaration);
        }
        if (name.startsWith("set")) {
            return isSimpleSetter(declaration);
        }
        return false;
    }

    public static boolean shouldSkip(ConstructorDeclaration declaration) {
        if (declaration == null) {
            return true;
        }
        return isNoOpConstructor(declaration);
    }

    private static boolean isSimpleGetter(MethodDeclaration declaration) {
        if (declaration.getBody().isEmpty() || declaration.getBody().get().getStatements().size() != 1) {
            return false;
        }
        Statement statement = declaration.getBody().get().getStatement(0);
        if (!(statement instanceof ReturnStmt returnStmt) || returnStmt.getExpression().isEmpty()) {
            return false;
        }
        return isFieldLikeExpression(returnStmt.getExpression().get());
    }

    private static boolean isSimpleSetter(MethodDeclaration declaration) {
        if (declaration.getParameters().size() != 1
                || declaration.getBody().isEmpty()
                || declaration.getBody().get().getStatements().size() != 1) {
            return false;
        }
        Statement statement = declaration.getBody().get().getStatement(0);
        if (!(statement instanceof ExpressionStmt expressionStmt)
                || !(expressionStmt.getExpression() instanceof AssignExpr assign)
                || assign.getOperator() != AssignExpr.Operator.ASSIGN) {
            return false;
        }
        return isFieldLikeExpression(assign.getTarget());
    }

    private static boolean isNoOpConstructor(ConstructorDeclaration declaration) {
        if (declaration.getBody().getStatements().isEmpty()) {
            return true;
        }
        if (declaration.getBody().getStatements().size() != 1) {
            return false;
        }
        Statement statement = declaration.getBody().getStatement(0);
        if (!(statement instanceof ExplicitConstructorInvocationStmt invocation)) {
            return false;
        }
        return invocation.getArguments().isEmpty();
    }

    private static boolean isFieldLikeExpression(Expression expression) {
        return expression instanceof NameExpr
                || (expression instanceof FieldAccessExpr fieldAccess && fieldAccess.getScope() instanceof ThisExpr);
    }

    public static boolean isSkipped(MethodDeclaration declaration) {
        return shouldSkip(declaration);
    }

    public static boolean isSkipped(ConstructorDeclaration declaration) {
        return shouldSkip(declaration);
    }
}
