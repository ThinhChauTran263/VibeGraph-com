package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

/**
 * Neo4j @Node for Java annotation type definitions.
 *
 * Represents the declaration site (e.g. {@code @AuditLog}), not usages.
 * Usages are modelled via the {@code ANNOTATED_BY} relationship.
 *
 * See VibeGraph-specs/neo4j-schema.md §2.9.
 */
@Node("Annotation")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnotationNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private String retention;
    private List<String> target;
}
