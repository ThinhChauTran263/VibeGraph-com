package com.vibegraph.diagram.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseView;

import lombok.RequiredArgsConstructor;

/**
 * Projects the canonical UML Use Case model into per-actor and per-domain views (Requirement 4).
 *
 * <p>Each view is a <em>faithful induced sub-graph</em> of the single inferred model — it filters
 * nodes and keeps only relations whose endpoints both survive. It never re-runs inference, so a view
 * can never disagree with the full diagram (no second, divergent error source). The full diagram
 * remains the source of truth; views are projections for readability and modular documentation.
 *
 * <ul>
 *   <li><b>Actor view</b> — the actor, its generalization ancestors, and every use case any of them
 *       reaches (plus shared {@code «include»}/{@code «extend»} targets). Answers "what can this
 *       actor do?".</li>
 *   <li><b>Domain view</b> — the use cases of one business domain, the actors that reach them, and
 *       the relations among that set. Answers "what happens in this module?".</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UseCaseViewProjector {

    private static final String REL_ASSOCIATION = "association";
    private static final String REL_GENERALIZATION = "generalization";
    private static final String REL_INCLUDE = "include";
    private static final String REL_EXTEND = "extend";
    private static final String DOMAIN_SHARED = "shared";

    private final UmlUseCaseRenderer renderer;

    public List<UseCaseView> project(String systemName, List<Actor> actors,
            List<UseCaseElement> useCases, List<Relation> relations) {
        List<Actor> safeActors = actors == null ? List.of() : actors;
        List<UseCaseElement> safeUseCases = useCases == null ? List.of() : useCases;
        List<Relation> safeRelations = relations == null ? List.of() : relations;

        Map<String, Actor> actorById = new LinkedHashMap<>();
        for (Actor a : safeActors) {
            actorById.put(a.getId(), a);
        }
        Map<String, UseCaseElement> useCaseById = new LinkedHashMap<>();
        for (UseCaseElement uc : safeUseCases) {
            useCaseById.put(uc.getId(), uc);
        }

        List<UseCaseView> views = new ArrayList<>();
        for (Actor actor : safeActors) {
            views.add(projectActor(systemName, actor, actorById, useCaseById, safeRelations));
        }
        for (String domain : distinctDomains(safeUseCases)) {
            views.add(projectDomain(systemName, domain, actorById, useCaseById, safeRelations));
        }
        return views;
    }

    private UseCaseView projectActor(String systemName, Actor actor, Map<String, Actor> actorById,
            Map<String, UseCaseElement> useCaseById, List<Relation> relations) {
        // Generalization ancestors of this actor (actor --|> parent), transitively.
        Set<String> actorIds = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(actor.getId());
        actorIds.add(actor.getId());
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Relation rel : relations) {
                if (REL_GENERALIZATION.equals(rel.getType()) && current.equals(rel.getFrom())
                        && actorById.containsKey(rel.getTo()) && actorIds.add(rel.getTo())) {
                    queue.add(rel.getTo());
                }
            }
        }
        // Use cases any included actor is associated with, then expand include/extend targets.
        Set<String> useCaseIds = new LinkedHashSet<>();
        for (Relation rel : relations) {
            if (REL_ASSOCIATION.equals(rel.getType()) && actorIds.contains(rel.getFrom())
                    && useCaseById.containsKey(rel.getTo())) {
                useCaseIds.add(rel.getTo());
            }
        }
        expandIncludes(useCaseIds, useCaseById, relations);

        Set<String> nodeIds = new LinkedHashSet<>(actorIds);
        nodeIds.addAll(useCaseIds);
        return buildView(systemName, "actor", actor.getName(), actorById, useCaseById, relations,
                actorIds, useCaseIds, nodeIds);
    }

    private UseCaseView projectDomain(String systemName, String domain, Map<String, Actor> actorById,
            Map<String, UseCaseElement> useCaseById, List<Relation> relations) {
        Set<String> useCaseIds = new LinkedHashSet<>();
        for (UseCaseElement uc : useCaseById.values()) {
            if (domain.equals(uc.getDomain())) {
                useCaseIds.add(uc.getId());
            }
        }
        expandIncludes(useCaseIds, useCaseById, relations);
        // Actors that reach any of these use cases.
        Set<String> actorIds = new LinkedHashSet<>();
        for (Relation rel : relations) {
            if (REL_ASSOCIATION.equals(rel.getType()) && useCaseIds.contains(rel.getTo())
                    && actorById.containsKey(rel.getFrom())) {
                actorIds.add(rel.getFrom());
            }
        }
        Set<String> nodeIds = new LinkedHashSet<>(actorIds);
        nodeIds.addAll(useCaseIds);
        return buildView(systemName, "domain", domain, actorById, useCaseById, relations,
                actorIds, useCaseIds, nodeIds);
    }

    /** Pull in {@code «include»}/{@code «extend»} target use cases of the current set (one fixpoint). */
    private void expandIncludes(Set<String> useCaseIds, Map<String, UseCaseElement> useCaseById,
            List<Relation> relations) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Relation rel : relations) {
                boolean linking = REL_INCLUDE.equals(rel.getType()) || REL_EXTEND.equals(rel.getType());
                if (linking && useCaseIds.contains(rel.getFrom()) && useCaseById.containsKey(rel.getTo())
                        && useCaseIds.add(rel.getTo())) {
                    changed = true;
                }
            }
        }
    }

    private UseCaseView buildView(String systemName, String viewType, String title,
            Map<String, Actor> actorById, Map<String, UseCaseElement> useCaseById, List<Relation> relations,
            Set<String> actorIds, Set<String> useCaseIds, Set<String> nodeIds) {
        List<Actor> viewActors = new ArrayList<>();
        for (Map.Entry<String, Actor> e : actorById.entrySet()) {
            if (actorIds.contains(e.getKey())) {
                viewActors.add(e.getValue());
            }
        }
        List<UseCaseElement> viewUseCases = new ArrayList<>();
        for (Map.Entry<String, UseCaseElement> e : useCaseById.entrySet()) {
            if (useCaseIds.contains(e.getKey())) {
                viewUseCases.add(e.getValue());
            }
        }
        List<Relation> viewRelations = new ArrayList<>();
        for (Relation rel : relations) {
            if (nodeIds.contains(rel.getFrom()) && nodeIds.contains(rel.getTo())) {
                viewRelations.add(rel);
            }
        }
        return UseCaseView.builder()
                .viewType(viewType)
                .title(title)
                .actors(viewActors)
                .useCases(viewUseCases)
                .relations(viewRelations)
                .mermaidSyntax(renderer.toMermaid(systemName, viewActors, viewUseCases, viewRelations))
                .plantUmlSyntax(renderer.toPlantUml(systemName, viewActors, viewUseCases, viewRelations))
                .build();
    }

    /** Distinct business domains in deterministic order; the synthetic "shared" bucket is skipped. */
    private Set<String> distinctDomains(List<UseCaseElement> useCases) {
        Set<String> domains = new TreeSet<>();
        for (UseCaseElement uc : useCases) {
            String d = uc.getDomain();
            if (d != null && !d.isBlank() && !DOMAIN_SHARED.equals(d)) {
                domains.add(d);
            }
        }
        return domains;
    }
}
