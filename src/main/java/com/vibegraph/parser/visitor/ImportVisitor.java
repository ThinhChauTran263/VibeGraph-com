package com.vibegraph.parser.visitor;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.TypeReferenceSupport;
import com.vibegraph.parser.node.EdgeData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts IMPORTS edges from import statements.
 */
public class ImportVisitor extends VoidVisitorAdapter<Object> {

    private final List<EdgeData> extractedEdges = new ArrayList<>();
    private String sourceFileFullName;

    public ImportVisitor(String sourceFileFullName) {
        this.sourceFileFullName = sourceFileFullName;
    }

    @Override
    public void visit(ImportDeclaration n, Object arg) {
        String importedName = n.getNameAsString();

        if (shouldSkip(importedName)) {
            super.visit(n, arg);
            return;
        }

        Map<String, Object> properties = Map.of(
                "isStatic", n.isStatic(),
                "isWildcard", n.isAsterisk(),
                "lineNumber", n.getBegin().map(p -> p.line).orElse(0)
        );

        extractedEdges.add(EdgeData.of("IMPORTS", sourceFileFullName, importedName, properties));
        super.visit(n, arg);
    }

    public List<EdgeData> getExtractedEdges() {
        return extractedEdges;
    }

    private boolean shouldSkip(String importedName) {
        return importedName.startsWith("java.lang.")
                || TypeReferenceSupport.shouldSkipImportedType(importedName);
    }
}
