package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

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

    private final List<ExtractedClassNode> extractedNodes = new ArrayList<>();

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
        // TODO: Extract Class/Interface node and add to extractedNodes
        super.visit(n, arg);
    }

    public List<ExtractedClassNode> getExtractedNodes() {
        return extractedNodes;
    }

    /**
     * Stub class for extracted class data. Replace with proper node type when implementing.
     */
    public static class ExtractedClassNode {
        private String name;
        private String fullName;
        private String visibility;
        private String type;
        private String springLayer;
        private boolean isAbstract;
        private boolean isFinal;

        public String getName() { return name; }
        public String getFullName() { return fullName; }
        public String getVisibility() { return visibility; }
        public String getType() { return type; }
        public String getSpringLayer() { return springLayer; }
        public boolean isAbstract() { return isAbstract; }
        public boolean isFinal() { return isFinal; }
    }
}
