package com.vibegraph.graph.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDto {
    private String id;
    private String type;
    private String name;
    private String fullName;
    private String filePath;
    private Integer lineNumber;
    private Map<String, Object> properties;
}
