package com.vibegraph.mcp.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.vibegraph.mcp.dto.response.ReferenceSearchResponse;
import com.vibegraph.mcp.service.ReferenceAnalyzer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FindReferencesTool {

    private final ReferenceAnalyzer referenceAnalyzer;

    @Tool(name = "find_references", description = "Find graph references to a symbol (class, interface, method, or field) "
            + "by following relationship edges. Optionally filter by relationship types (e.g. CALLS, IMPORTS, INJECTS, "
            + "IMPLEMENTS, EXTENDS, TYPE_OF, PARAMETER_TYPE, RETURNS, READS, WRITES, HANDLES_ROUTE) and direction "
            + "(incoming, outgoing, both). Ambiguous queries return candidates instead of guessing.")
    public ReferenceSearchResponse findReferences(
            @ToolParam(description = "Project identifier to inspect") String projectId,
            @ToolParam(description = "Symbol node id, fully-qualified name, or an unambiguous simple name") String symbolQuery,
            @ToolParam(required = false, description = "Optional relationship type filter; values must be valid graph relationship types") List<String> relationshipTypes,
            @ToolParam(required = false, description = "Edge direction: incoming, outgoing, or both (default both)") String direction,
            @ToolParam(required = false, description = "Maximum references to return (default 50, hard cap 200)") Integer maxResults) {
        return referenceAnalyzer.findReferences(projectId, symbolQuery, relationshipTypes, direction, maxResults);
    }
}
