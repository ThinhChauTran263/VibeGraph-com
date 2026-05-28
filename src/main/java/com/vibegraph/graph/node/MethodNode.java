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
    private List<String> paramTypes;
    private String filePath;
    private int lineNumber;
    private int endLine;
    private String kind;
    private String visibility;
    private boolean isAbstract;
    private boolean isStatic;
    private boolean isFinal;
    private boolean isSynchronized;
    private String returnType;
    private List<String> paramNames;
    private List<String> throwsTypes;
    private String httpMethod;
    private String routePath;
    private List<String> springAnnotations;
    private boolean isStub;
    private String snippet;
}
