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
    /**
     * Distinct packages that contain at least one classifier in the project, sorted.
     * Populated for the class diagram so the UI can offer the package filter as a
     * pick-list/autocomplete instead of making the user guess an exact package name.
     */
    private java.util.List<String> availablePackages;
}
