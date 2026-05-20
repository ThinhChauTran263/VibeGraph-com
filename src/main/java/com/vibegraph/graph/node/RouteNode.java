package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

@Node("Route")
@Data
@EqualsAndHashCode(callSuper = true)
public class RouteNode extends BaseNode {
    private String httpMethod;
    private String routePath;
    private String handlerMethod;
    private String filePath;
    private int lineNumber;
    private List<String> middleware;
}
