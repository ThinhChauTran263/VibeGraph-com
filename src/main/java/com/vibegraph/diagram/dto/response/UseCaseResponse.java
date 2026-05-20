package com.vibegraph.diagram.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UseCaseResponse {
    private List<String> actors;
    private List<String> useCases;
    private String mermaidSyntax;
}
