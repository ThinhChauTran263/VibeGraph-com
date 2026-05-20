package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Extracts Field nodes from AST.
 *
 * TODO:
 * - Visit FieldDeclaration → create FieldNode
 * - Extract: name, declaredType, modifiers
 * - Detect @Autowired/@Inject → INJECTS edge
 * - Create HAS_FIELD edge from Class to Field
 */
public class FieldVisitor extends VoidVisitorAdapter<Object> {

    @Override
    public void visit(FieldDeclaration n, Object arg) {
        // TODO: Extract Field node
        super.visit(n, arg);
    }
}
