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
    private String name;
    private String rootPath;
    private String description;
    private Instant createdAt;
    private Instant lastAnalyzedAt;
    private String analysisStatus;
    private int totalFiles;
    private int totalNodes;
    private int totalEdges;
}
