package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * Renders an inferred UML Use Case model to PlantUML and a Mermaid fallback.
 *
 * <p>PlantUML output is standard {@code @startuml ... @enduml} text only; it is NEVER sent to any
 * public PlantUML server. The Mermaid fallback uses {@code flowchart LR} syntax compatible with
 * mermaid ^11 (actor nodes, oval use cases, system boundary, plain associations).
 */
@Component
public class UmlUseCaseRenderer {

    private static final String NL = "\n";
    private static final String INDENT = "    ";

    /**
     * Render PlantUML source for a use case diagram.
     */
    public String toPlantUml(String systemName, List<Actor> actors, List<UseCaseElement> useCases,
            List<Relation> relations) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml").append(NL);
        sb.append("left to right direction").append(NL);

        for (Actor actor : actors) {
            sb.append("actor \"").append(escapePlant(actor.getName())).append("\" as ")
                    .append(actor.getId()).append(NL);
        }

        sb.append("rectangle \"").append(escapePlant(blankToDefault(systemName))).append("\" {").append(NL);
        for (UseCaseElement uc : useCases) {
            sb.append(INDENT).append("usecase \"").append(escapePlant(uc.getName())).append("\" as ")
                    .append(uc.getId()).append(NL);
        }
        sb.append("}").append(NL);

        for (Relation rel : relations) {
            switch (rel.getType()) {
                case "include" ->
                    sb.append(rel.getFrom()).append(" ..> ").append(rel.getTo()).append(" : <<include>>").append(NL);
                case "extend" ->
                    sb.append(rel.getFrom()).append(" ..> ").append(rel.getTo()).append(" : <<extend>>").append(NL);
                case "generalization" ->
                    sb.append(rel.getFrom()).append(" --|> ").append(rel.getTo()).append(NL);
                default ->
                    sb.append(rel.getFrom()).append(" -- ").append(rel.getTo()).append(NL);
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Render a Mermaid fallback for the same model.
     *
     * <p>Mermaid has no native Use Case diagram, so the fallback keeps the UML shape simple while
     * actively fighting "spaghetti" edge crossings:
     * <ul>
     *   <li>top-level {@code flowchart TB} so actors sit above the system boundary;</li>
     *   <li>a single {@code subgraph} system boundary with an inner {@code direction TB} printed on
     *       the line directly below the {@code subgraph} declaration, forcing use case nodes to
     *       stack vertically instead of spreading into one wide row;</li>
     *   <li><b>actor-grouped node declaration</b> — use cases reachable by the first actor
     *       ({@code User}) are declared first (top), the next actor's ({@code Admin}) after. Mermaid
     *       seeds row order from declaration order, so grouping a single actor's goals together keeps
     *       its association lines parallel instead of crossing the whole boundary. Within an actor
     *       group, read-only ({@code View ...}) goals come before mutating ones;</li>
     *   <li><b>deduplication</b> — each use case node is declared exactly once; multiple actors
     *       reaching the same goal produce multiple association lines, never duplicate nodes.</li>
     * </ul>
     */
    public String toMermaid(String systemName, List<Actor> actors, List<UseCaseElement> useCases,
            List<Relation> relations) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TB").append(NL);

        if (actors.isEmpty() && useCases.isEmpty()) {
            sb.append(INDENT).append("%% No business use cases detected for this project");
            return sb.toString();
        }

        // Actors live outside the system boundary, referenced by their stable id in relations.
        for (Actor actor : actors) {
            sb.append(INDENT).append(actor.getId()).append("(((")
                    .append(escapeMermaid(actor.getName())).append(")))").append(NL);
        }

        sb.append(NL);
        sb.append(INDENT).append("subgraph System[\"")
                .append(escapeMermaid(blankToDefault(systemName))).append("\"]").append(NL);
        // direction TB must sit on the line directly below the subgraph: stacks nodes vertically.
        sb.append(INDENT).append(INDENT).append("direction TB").append(NL);

        for (UseCaseElement uc : orderByActor(actors, useCases, relations)) {
            appendUseCaseNode(sb, uc);
        }
        sb.append(INDENT).append("end").append(NL);

        for (Relation rel : relations) {
            switch (rel.getType()) {
                case "include" ->
                    sb.append(INDENT).append(rel.getFrom()).append(" -.->|include| ").append(rel.getTo()).append(NL);
                case "extend" ->
                    sb.append(INDENT).append(rel.getFrom()).append(" -.->|extend| ").append(rel.getTo()).append(NL);
                case "generalization" ->
                    sb.append(INDENT).append(rel.getFrom()).append(" --> ").append(rel.getTo()).append(NL);
                default ->
                    sb.append(INDENT).append(rel.getFrom()).append(" --- ").append(rel.getTo()).append(NL);
            }
        }

        if (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Order use case nodes so each actor's goals are declared together (first actor first), with
     * read-only goals ahead of mutating ones inside each group. Shared goals are attached to their
     * earliest-ranked actor so they are still declared exactly once.
     */
    private List<UseCaseElement> orderByActor(List<Actor> actors, List<UseCaseElement> useCases,
            List<Relation> relations) {
        Map<String, Integer> actorRank = new HashMap<>();
        for (int i = 0; i < actors.size(); i++) {
            actorRank.put(actors.get(i).getId(), i);
        }
        // Earliest-ranked actor associated with each use case (User before Admin, etc.).
        Map<String, Integer> primaryActor = new HashMap<>();
        for (Relation rel : relations) {
            if (rel == null || !"association".equals(rel.getType())) {
                continue;
            }
            Integer rank = actorRank.get(rel.getFrom());
            if (rank != null) {
                primaryActor.merge(rel.getTo(), rank, Math::min);
            }
        }

        List<UseCaseElement> ordered = new ArrayList<>();
        Map<UseCaseElement, Integer> originalIndex = new IdentityHashMap<>();
        for (UseCaseElement uc : useCases) {
            if (uc != null) {
                originalIndex.put(uc, ordered.size());
                ordered.add(uc);
            }
        }
        ordered.sort(Comparator
                .comparingInt((UseCaseElement uc) -> primaryActor.getOrDefault(uc.getId(), Integer.MAX_VALUE))
                .thenComparingInt(uc -> isReadUseCase(uc) ? 0 : 1)
                .thenComparingInt(originalIndex::get));
        return ordered;
    }

    /** Read-only goals are the {@code View ...} use cases; everything else mutates state. */
    private boolean isReadUseCase(UseCaseElement uc) {
        String name = uc.getName();
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("view");
    }

    private void appendUseCaseNode(StringBuilder sb, UseCaseElement uc) {
        sb.append(INDENT).append(INDENT).append(uc.getId()).append("([\"")
                .append(escapeMermaid(uc.getName())).append("\"])").append(NL);
    }

    private String blankToDefault(String systemName) {
        return systemName == null || systemName.isBlank() ? "System" : systemName;
    }

    /** PlantUML labels: neutralize the double-quote delimiter and control chars. */
    private String escapePlant(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                sb.append('\'');
            } else if (c < 0x20) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** Mermaid labels inside {@code (["..."])}: escape quotes as HTML entity, strip control chars. */
    private String escapeMermaid(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                sb.append("#quot;");
            } else if (c < 0x20) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
