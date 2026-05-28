package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Interface")
@Data
@EqualsAndHashCode(callSuper = true)
public class InterfaceNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private int endLine;
    private String visibility;
    private boolean isInner;
    private String signatureSnippet;
}
