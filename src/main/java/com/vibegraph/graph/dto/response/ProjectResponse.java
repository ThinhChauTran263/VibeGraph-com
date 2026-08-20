package com.vibegraph.graph.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProjectResponse {
    private String id;
    private String name;
    /**
     * Absolute server-side path. Used internally by the analyze/import flows via
     * {@code getRootPath()}, but excluded from API responses so the absolute server
     * path is never leaked to clients.
     */
    @JsonIgnore
    private String rootPath;
    private Instant createdAt;
    private Instant lastAnalyzedAt;
    private int totalFiles;
    private int totalNodes;
    private int totalEdges;
    private String status;
    /** Analysis progress 0-100: 0 until analysis finishes, 100 when ANALYZED. */
    private int progress;
    /**
     * Bytes of materialized {@code .java} source stored for this project - the exact amount
     * counted against the owner's storage quota. Absent when unknown (legacy rows).
     */
    private Long storedBytes;
    /**
     * Commit SHA of the imported source (GitHub imports only); absent for other sources.
     * Shown shortened on the project card so owners recognize which commit was imported.
     */
    private String sourceRef;
    /** Branch/ref the GitHub import was taken from; absent for other sources. */
    private String sourceBranch;
}
