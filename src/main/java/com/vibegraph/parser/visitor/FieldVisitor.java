package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

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

    private final List<ExtractedFieldNode> extractedFields = new ArrayList<>();

    @Override
    public void visit(FieldDeclaration n, Object arg) {
        // TODO: Extract Field node and add to extractedFields
        super.visit(n, arg);
    }

    public List<ExtractedFieldNode> getExtractedFields() {
        return extractedFields;
    }

    /**
     * Stub class for extracted field data. Replace with proper node type when implementing.
     */
    public static class ExtractedFieldNode {
        private String name;
        private String declaredType;
        private String visibility;
        private boolean isInjected;
        private boolean isStatic;
        private boolean isFinal;

        public String getName() { return name; }
        public String getDeclaredType() { return declaredType; }
        public String getVisibility() { return visibility; }
        public boolean isInjected() { return isInjected; }
        public boolean isStatic() { return isStatic; }
        public boolean isFinal() { return isFinal; }
    }
}
