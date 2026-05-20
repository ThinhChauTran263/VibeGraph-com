package com.vibegraph.graph.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDto {
    private String id;
    private String source;
    private String target;
    private String type;
    private Double confidence;
    private Integer lineNumber;
}
