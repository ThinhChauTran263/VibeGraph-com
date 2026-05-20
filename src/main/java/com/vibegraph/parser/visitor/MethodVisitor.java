package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Extracts Method nodes from AST.
 *
 * TODO:
 * - Visit MethodDeclaration → create MethodNode
 * - Extract: name, returnType, parameters, throwsTypes, modifiers
 * - Detect HTTP routes (@GetMapping, @PostMapping...)
 * - Create HAS_METHOD edge from Class to Method
 */
public class MethodVisitor extends VoidVisitorAdapter<Object> {

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        // TODO: Extract Method node
        super.visit(n, arg);
    }
}
