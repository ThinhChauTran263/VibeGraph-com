package com.vibegraph.diagram.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramResponse {
    private String diagramType;
    private String mermaidSyntax;
    private String plantUmlSyntax;
}
