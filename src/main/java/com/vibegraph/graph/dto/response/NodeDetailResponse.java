package com.vibegraph.graph.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Node detail with INCOMING and OUTGOING connections.
 * Used by the right Node Detail Panel in frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDetailResponse {
    private NodeDto node;
    private List<ConnectionDto> incoming;
    private List<ConnectionDto> outgoing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionDto {
        private NodeDto otherNode;
        private String relationshipType;
        private String direction;
    }
}
