package com.vibegraph.parser.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseFileRequest {
    private String projectId;
    private String filePath;
    private boolean useCache;
}
