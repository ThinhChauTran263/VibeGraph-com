package com.vibegraph.graph.service;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

import java.util.List;

/**
 * Graph data service — Sprint 1 scope: getFullGraph only.
 */
public interface GraphService {

    GraphDataResponse getFullGraph(String projectId);

    List<NodeDto> searchNodes(String projectId, String query);
}
