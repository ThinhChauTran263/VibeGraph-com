package com.vibegraph.graph.controller;

import com.vibegraph.common.dto.response.ApiResponse;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping
    public ResponseEntity<ApiResponse<GraphDataResponse>> getFullGraph(@PathVariable String projectId) {
        return ResponseEntity.ok(ApiResponse.success(graphService.getFullGraph(projectId)));
    }
}
