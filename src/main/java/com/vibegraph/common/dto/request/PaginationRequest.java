package com.vibegraph.common.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {
    private int page;
    private int size;
    private String sortBy;
    private String sortDirection;
}
