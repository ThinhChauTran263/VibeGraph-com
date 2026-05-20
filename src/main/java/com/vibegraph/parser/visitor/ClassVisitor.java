package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Extracts Class, Interface, Enum nodes from AST.
 *
 * TODO:
 * - Visit ClassOrInterfaceDeclaration → create ClassNode/InterfaceNode
 * - Extract: name, fullName, modifiers, isAbstract
 * - Detect inheritance (EXTENDS, IMPLEMENTS edges)
 * - Handle inner classes, anonymous classes
 */
public class ClassVisitor extends VoidVisitorAdapter<Object> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        // TODO: Extract Class/Interface node
        super.visit(n, arg);
    }
}
