package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

@Node("Enum")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnumNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private List<String> values;
}
