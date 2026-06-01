package com.vibegraph.graph.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private String rootPath;
    private Instant createdAt;
    private Instant lastAnalyzedAt;
    private int totalFiles;
    private int totalNodes;
    private int totalEdges;
    private String status;
    /** Analysis progress 0-100: 0 until analysis finishes, 100 when ANALYZED. */
    private int progress;
}
