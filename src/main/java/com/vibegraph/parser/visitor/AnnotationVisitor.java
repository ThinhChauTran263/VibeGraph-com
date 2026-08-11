package com.vibegraph.parser.visitor;

import java.util.List;

import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Deprecated compatibility visitor.
 *
 * <p>Annotation usages are metadata on the owning Class/Method/Field node through
 * {@code properties.annotations}. Project-defined {@code @interface}
 * declarations are emitted by {@link ClassVisitor}. This visitor intentionally
 * emits no usage nodes and no {@code ANNOTATED_BY} edges, preventing annotation
 * noise from entering the graph at parse time.
 */
@Deprecated(forRemoval = false)
public class AnnotationVisitor extends VoidVisitorAdapter<Object> {

    public List<NodeData> getExtractedNodes() {
        return List.of();
    }

    public List<EdgeData> getExtractedEdges() {
        return List.of();
    }
}
