package com.vibegraph.graph.node;

import com.vibegraph.common.node.BaseNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

@Node("File")
@Data
@EqualsAndHashCode(callSuper = true)
public class FileNode extends BaseNode {
    private String name;
    private String filePath;
    private Instant lastModified;
    private String checksum;
    private long sizeBytes;
}
