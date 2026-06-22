package com.vibegraph.graph.service.impl;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.graph.config.AnalyzeLimitProperties;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.ParserService;

/**
 * Unit tests for AnalyzeServiceImpl. The key regression guard: the Project node must
 * receive the human-readable display name (repo/owner-repo or user-provided name) as
 * its {@code name}, NOT the projectId — otherwise the canvas/Node-Detail title shows a
 * numeric/opaque id. The stable graph id stays projectId.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyzeServiceImpl")
class AnalyzeServiceImplTest {

    @Mock
    ParserService parserService;
    @Mock
    GraphRepository graphRepository;

    private AnalyzeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnalyzeServiceImpl(parserService, graphRepository, new AnalyzeLimitProperties());
    }

    @Test
    @DisplayName("upserts the Project node with the human-readable display name, not the id")
    void upsertsProjectWithReadableName() {
        when(parserService.parseProject(any(Path.class), any())).thenReturn(List.of(ParseResult.builder().build()));

        service.analyzeProject("44786872", "ThinhChauTran263/Lab7_Java6", "/tmp/repo");

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(graphRepository).upsertProject(id.capture(), name.capture(), eq("/tmp/repo"));
        assertThat(id.getValue()).isEqualTo("44786872");
        assertThat(name.getValue()).isEqualTo("ThinhChauTran263/Lab7_Java6");
    }

    @Test
    @DisplayName("falls back to the projectId as name when no display name is supplied")
    void fallsBackToIdWhenNameBlank() {
        when(parserService.parseProject(any(Path.class), any())).thenReturn(List.of(ParseResult.builder().build()));

        service.analyzeProject("p1", "   ", "/tmp/p1");

        verify(graphRepository).upsertProject(eq("p1"), eq("p1"), anyString());
    }

    @Test
    @DisplayName("fails fast (no upsert) when the node count exceeds the configured cap")
    void failsFastWhenOverNodeCap() {
        AnalyzeLimitProperties tightLimits = new AnalyzeLimitProperties();
        tightLimits.setMaxNodes(1);
        AnalyzeServiceImpl tightService = new AnalyzeServiceImpl(parserService, graphRepository, tightLimits);

        ParseResult oversized = ParseResult.builder()
                .nodes(List.of(
                        NodeData.of("Class", "A", "com.A", "A.java", 1),
                        NodeData.of("Class", "B", "com.B", "B.java", 1)))
                .build();
        when(parserService.parseProject(any(Path.class), any())).thenReturn(List.of(oversized));

        assertThatThrownBy(() -> tightService.analyzeProject("p1", "p1", "/tmp/p1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too large to analyze");

        // The cap fires before any persistence — nothing is written to the graph.
        verify(graphRepository, never()).upsertNodes(any(), any());
        verify(graphRepository, never()).upsertEdges(any(), any());
    }
}
