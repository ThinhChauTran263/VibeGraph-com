package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

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

    private final List<ExtractedMethodNode> extractedMethods = new ArrayList<>();

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        // TODO: Extract Method node and add to extractedMethods
        super.visit(n, arg);
    }

    public List<ExtractedMethodNode> getExtractedMethods() {
        return extractedMethods;
    }

    /**
     * Stub class for extracted method data. Replace with proper node type when implementing.
     */
    public static class ExtractedMethodNode {
        private String name;
        private String returnType;
        private List<String> parameters;
        private List<String> throwsTypes;
        private String visibility;
        private String httpMethod;
        private String routePath;
        private boolean isStatic;
        private boolean isAbstract;

        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public List<String> getParameters() { return parameters; }
        public List<String> getThrowsTypes() { return throwsTypes; }
        public String getVisibility() { return visibility; }
        public String getHttpMethod() { return httpMethod; }
        public String getRoutePath() { return routePath; }
        public boolean isStatic() { return isStatic; }
        public boolean isAbstract() { return isAbstract; }
    }
}
