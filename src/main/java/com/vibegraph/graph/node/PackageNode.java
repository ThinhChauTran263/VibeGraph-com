package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Package")
@Data
@EqualsAndHashCode(callSuper = true)
public class PackageNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
}
