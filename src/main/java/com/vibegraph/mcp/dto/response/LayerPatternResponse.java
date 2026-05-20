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
public class LayerPatternResponse {
    private String layer;
    private String description;
    private List<String> requiredAnnotations;
    private List<String> namingConvention;
    private List<String> doRules;
    private List<String> dontRules;
    private String exampleCode;
}
