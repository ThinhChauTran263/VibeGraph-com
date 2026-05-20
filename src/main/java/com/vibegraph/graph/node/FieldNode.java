package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Field")
@Data
@EqualsAndHashCode(callSuper = true)
public class FieldNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private String visibility;
    private boolean isStatic;
    private boolean isFinal;
    private String declaredType;
    private boolean isInjected;
}
