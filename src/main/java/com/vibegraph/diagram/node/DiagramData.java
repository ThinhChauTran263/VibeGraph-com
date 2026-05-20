package com.vibegraph.diagram.node;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Internal model holding raw data extracted from Neo4j
 * before being transformed into Mermaid syntax.
 */
@Data
@Builder
public class DiagramData {
    private String diagramType;
    private List<Object> nodes;
    private List<Object> edges;
}
