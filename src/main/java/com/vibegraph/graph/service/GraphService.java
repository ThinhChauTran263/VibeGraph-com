package com.vibegraph.graph.service;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;

import java.util.List;

/**
 * Graph data service — Sprint 1 scope: getFullGraph only.
 */
public interface GraphService {

    GraphDataResponse getFullGraph(String projectId);

    NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops);

    default ImpactAnalysisResponse getImpactAnalysis(String projectId, String nodeId, int depth) {
        return getImpactAnalysis(projectId, nodeId, depth, ImpactProfile.DEPENDENCY);
    }

    ImpactAnalysisResponse getImpactAnalysis(String projectId, String nodeId, int depth, ImpactProfile profile);

    List<NodeDto> searchNodes(String projectId, String query);
}
