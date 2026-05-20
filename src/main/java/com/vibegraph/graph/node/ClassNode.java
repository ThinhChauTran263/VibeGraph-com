package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

/**
 * Neo4j @Node for Java classes.
 */
@Node("Class")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClassNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private String visibility;
    private boolean isAbstract;
    private boolean isFinal;
    private boolean isStatic;
    private String springLayer;
    private List<String> springAnnotations;
}
