package com.vibegraph.mcp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Architecture context returned to AI tools via MCP.
 * Contains everything AI needs to generate code aligned with project architecture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchitectureContextResponse {
    private List<String> layers;
    private Map<String, String> patterns;
    private Map<String, String> namingConventions;
    private String classDiagramMermaid;
    private List<String> warnings;
    private List<String> doRules;
    private List<String> dontRules;
}
