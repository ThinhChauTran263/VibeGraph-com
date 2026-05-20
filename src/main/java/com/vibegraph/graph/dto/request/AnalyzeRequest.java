package com.vibegraph.graph.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {
    private boolean fullRebuild;
    private boolean useCache;
}
