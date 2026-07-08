package com.vibegraph.diagram.service.impl;

import java.util.List;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * Default {@link UseCaseSemanticRefiner}: returns the deterministic labels unchanged. Active whenever
 * Tier 2 LLM refinement is disabled or no chat model is available — guaranteeing the system stays
 * deterministic and offline by default.
 */
public class NoopUseCaseRefiner implements UseCaseSemanticRefiner {

    @Override
    public List<UseCaseElement> refineLabels(String systemName, List<UseCaseElement> useCases) {
        return useCases;
    }
}
