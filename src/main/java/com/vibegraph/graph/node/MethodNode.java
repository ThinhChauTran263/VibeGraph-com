package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

@Node("Method")
@Data
@EqualsAndHashCode(callSuper = true)
public class MethodNode extends BaseNode {
    private String name;
    private String fullName;
    private String filePath;
    private int lineNumber;
    private String visibility;
    private boolean isAbstract;
    private boolean isStatic;
    private boolean isFinal;
    private String returnType;
    private List<String> parameters;
    private List<String> throwsTypes;
    private String httpMethod;
    private String routePath;
}
