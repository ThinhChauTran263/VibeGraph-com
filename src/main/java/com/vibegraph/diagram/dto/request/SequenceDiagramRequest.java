package com.vibegraph.diagram.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SequenceDiagramRequest {
    private String entryMethodId;
    private int maxDepth;
}
