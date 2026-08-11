package com.vibegraph.graph.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphFilterRequest {
    private List<String> nodeTypes;
    private List<String> includeTypes;
    private List<String> edgeTypes;
    private String packagePath;
    private String packageFilter;
    private String layerFilter;
    private String searchQuery;
    private Integer maxDepth;

    public List<String> effectiveNodeTypes() {
        return includeTypes != null && !includeTypes.isEmpty() ? includeTypes : nodeTypes;
    }

    public String effectivePackagePath() {
        return packagePath != null && !packagePath.isBlank() ? packagePath : packageFilter;
    }

    public boolean isEmpty() {
        return (effectiveNodeTypes() == null || effectiveNodeTypes().isEmpty())
                && (edgeTypes == null || edgeTypes.isEmpty())
                && (effectivePackagePath() == null || effectivePackagePath().isBlank())
                && (layerFilter == null || layerFilter.isBlank())
                && (searchQuery == null || searchQuery.isBlank())
                && maxDepth == null;
    }
}
