package com.vibegraph.diagram.service.impl;

import java.util.List;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * Tier 2 semantic refinement seam (Requirement 5). Refines ONLY the human-readable labels of use
 * cases already inferred deterministically — it may never add or remove use cases, actors, or
 * relations. The deterministic model is the source of truth and the fallback.
 *
 * <p>Default binding is {@link NoopUseCaseRefiner} (identity), so the system stays fully
 * deterministic and offline unless an LLM is explicitly enabled and reachable.
 */
public interface UseCaseSemanticRefiner {

    /**
     * Return a list with possibly-improved {@code name} labels. Implementations MUST:
     * <ul>
     *   <li>preserve every use case id, domain, level, source, confidence (only the name may change);</li>
     *   <li>never add or drop elements;</li>
     *   <li>fall back to the input unchanged on any failure.</li>
     * </ul>
     */
    List<UseCaseElement> refineLabels(String systemName, List<UseCaseElement> useCases);
}
