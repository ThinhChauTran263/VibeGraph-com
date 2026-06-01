package com.vibegraph.graph.service.impl;

import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphServiceImpl implements GraphService {

    private final GraphRepository graphRepository;

    @Override
    public GraphDataResponse getFullGraph(String projectId) {
        return graphRepository.getFullGraph(projectId);
    }

    @Override
    public List<NodeDto> searchNodes(String projectId, String query) {
        return graphRepository.searchNodes(projectId, query);
    }
}
