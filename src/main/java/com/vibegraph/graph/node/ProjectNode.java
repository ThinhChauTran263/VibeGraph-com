package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

@Node("Project")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectNode extends BaseNode {
    private String projectId;
    private String name;
    private String rootPath;
    private Instant createdAt;
    private Instant lastAnalyzedAt;
    private int totalFiles;
    private int totalNodes;
    private int totalEdges;
}
