package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Extracts IMPORTS edges from import statements.
 *
 * TODO:
 * - Visit ImportDeclaration → create IMPORTS edge from File to Class/Interface
 * - Skip java.lang.* (implicit imports)
 * - Handle wildcard imports (com.example.*)
 */
public class ImportVisitor extends VoidVisitorAdapter<Object> {

    @Override
    public void visit(ImportDeclaration n, Object arg) {
        // TODO: Extract IMPORTS edge
        super.visit(n, arg);
    }
}
