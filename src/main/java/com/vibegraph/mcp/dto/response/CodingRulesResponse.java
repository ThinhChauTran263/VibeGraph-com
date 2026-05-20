package com.vibegraph.mcp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingRulesResponse {
    private List<String> doRules;
    private List<String> dontRules;
    private List<String> warnings;
}
