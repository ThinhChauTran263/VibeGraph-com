package com.vibegraph.mcp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassContextResponse {
    private String className;
    private String fullName;
    private String layer;
    private List<String> methods;
    private List<String> fields;
    private List<String> dependencies;
    private List<String> usedBy;
    private String classDiagramMermaid;
}
