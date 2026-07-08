package com.vibegraph.graph.service.impl;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDetailResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphServiceImpl implements GraphService {

    private static final Set<Integer> ALLOWED_DETAIL_HOPS = Set.of(0, 1, 2, 3, 5);
    private static final Set<Integer> ALLOWED_IMPACT_DEPTHS = Set.of(1, 2, 3, 5);
    private static final int MAX_IDENTIFIER_LENGTH = 512;

    private final GraphRepository graphRepository;

    @Override
    public GraphDataResponse getFullGraph(String projectId) {
        return graphRepository.getFullGraph(projectId);
    }

    @Override
    public NodeDetailResponse getNodeDetail(String projectId, String nodeId, int hops) {
        if (!ALLOWED_DETAIL_HOPS.contains(hops)) {
            throw new IllegalArgumentException("hops must be one of 0, 1, 2, 3, 5");
        }
        return graphRepository.getNodeDetail(projectId, nodeId, hops);
    }

    @Override
    public ImpactAnalysisResponse getImpactAnalysis(String projectId, String nodeId, int depth, ImpactProfile profile) {
        validateIdentifier("projectId", projectId);
        validateIdentifier("nodeId", nodeId);
        if (!ALLOWED_IMPACT_DEPTHS.contains(depth)) {
            throw new IllegalArgumentException("depth must be one of 1, 2, 3, 5");
        }
        return graphRepository.getImpact(projectId, nodeId, depth, profile == null ? ImpactProfile.DEPENDENCY : profile);
    }

    @Override
    public List<NodeDto> searchNodes(String projectId, String query) {
        return graphRepository.searchNodes(projectId, query);
    }

    private void validateIdentifier(String fieldName, String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be non-blank and at most 512 characters");
        }
    }
}
