package com.vibegraph.graph.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EdgeDto {
    private String id;
    private String source;
    private String target;
    private String type;
    private Double confidence;
    private Integer lineNumber;
    private Integer weight;
    private List<Integer> occurrences;
}
