package com.vibegraph.parser.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResultResponse {
    private String filePath;
    private int nodesExtracted;
    private int edgesExtracted;
    private long parseTimeMs;
    private List<String> warnings;
}
