package com.vibegraph.graph.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class NodeDto {
    private String id;
    private String type;
    private String name;
    private String fullName;
    private String filePath;
    private Integer lineNumber;
    private Map<String, Object> properties;
}
