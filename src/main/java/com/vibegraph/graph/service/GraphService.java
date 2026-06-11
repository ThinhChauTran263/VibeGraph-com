package com.vibegraph.graph.service;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;

import java.util.List;

/**
 * Graph data service — Sprint 1 scope: getFullGraph only.
 */
public interface GraphService {

    GraphDataResponse getFullGraph(String projectId);

    NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops);

    ImpactAnalysisResponse getImpactAnalysis(String projectId, String nodeId, int depth);

    List<NodeDto> searchNodes(String projectId, String query);
}
