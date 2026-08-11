package com.vibegraph.mcp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.vibegraph.common.exception.NodeNotFoundException;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.dto.response.ImpactAnalysisResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.model.ImpactProfile;
import com.vibegraph.graph.service.GraphService;
import com.vibegraph.mcp.dto.response.ImpactAnalysisContextResponse;
import com.vibegraph.mcp.service.ImpactAnalysisAnalyzer;
import com.vibegraph.mcp.source.GraphTraversals;
import com.vibegraph.mcp.source.GraphView;
import com.vibegraph.mcp.source.SourceGraphSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImpactAnalysisAnalyzerImpl implements ImpactAnalysisAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_NODE_QUERY_LENGTH = 512;
    private static final int MAX_DIRECT_IMPACT = 50;
    private static final int MAX_TRANSITIVE_IMPACT = 100;
    private static final int MAX_COUNTERPART_ROOTS = 3;
    private static final int MAX_AFFECTED_ROUTES = 20;
    private static final int MAX_ROUTE_DEPTH = 8;
    private static final Set<Integer> ALLOWED_DEPTHS = Set.of(1, 2, 3, 5);
    private static final Set<String> CLASSLIKE = Set.of("Class", "Interface", "Enum", "Record");

    private final GraphService graphService;
    private final SourceGraphSupport graphSupport;

    @Override
    public ImpactAnalysisContextResponse analyzeImpact(String projectId, String nodeQuery, int depth, String profile) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedNodeQuery = validate(nodeQuery, "nodeQuery", MAX_NODE_QUERY_LENGTH);
        validateDepth(depth);
        ImpactProfile impactProfile = ImpactProfile.fromApiValue(profile);
        try {
            ImpactAnalysisResponse impact = resolveImpact(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
            Enrichment enrichment = enrich(normalizedProjectId, impactProfile, impact);
            return toResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile, impact, enrichment);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (NodeNotFoundException ex) {
            return notFoundResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
        } catch (RuntimeException ex) {
            return unavailableResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
        }
    }

    // ---- Java-aware enrichment ---------------------------------------------------------------

    /** Extra Java-semantics context layered over the base traversal. Never breaks the base contract. */
    private record Enrichment(
            List<ImpactAnalysisContextResponse.RouteImpact> affectedRoutes,
            List<String> relatedRoots,
            List<ImpactAnalysisContextResponse.NodeImpact> counterpartImpacts,
            List<String> notes) {

        static Enrichment empty() {
            return new Enrichment(List.of(), List.of(), List.of(), List.of());
        }
    }

    private Enrichment enrich(String projectId, ImpactProfile profile, ImpactAnalysisResponse impact) {
        try {
            GraphView graph = graphSupport.load(projectId);
            NodeDto targetRef = impact.getTarget();
            if (graph == null || targetRef == null) {
                return Enrichment.empty();
            }
            NodeDto target = graph.byId(targetRef.getId());
            if (target == null) {
                target = graph.byFullName(targetRef.getFullName());
            }
            if (target == null) {
                return Enrichment.empty();
            }

            List<String> notes = new ArrayList<>();
            Set<String> seedIds = new LinkedHashSet<>();
            seedIds.add(target.getId());

            // Interface/override counterparts: changing this method also breaks callers that go
            // through the interface declaration or sibling implementations.
            List<String> counterparts = GraphTraversals.overrideCounterparts(graph, target, MAX_COUNTERPART_ROOTS);
            List<ImpactAnalysisContextResponse.NodeImpact> counterpartImpacts = new ArrayList<>();
            for (String counterpart : counterparts) {
                NodeDto counterpartNode = graph.byFullName(counterpart);
                if (counterpartNode != null) {
                    seedIds.add(counterpartNode.getId());
                }
                try {
                    ImpactAnalysisResponse counterpartImpact = resolveImpact(projectId, counterpart, 1, profile);
                    for (NodeDto dependent : safeList(counterpartImpact.getWillBreak())) {
                        counterpartImpacts.add(toNodeImpact(dependent, "VIA_OVERRIDE", 1));
                    }
                } catch (RuntimeException ignored) {
                    // A missing counterpart never degrades the base analysis.
                }
            }
            if (!counterparts.isEmpty()) {
                notes.add("Target participates in an override/implements hierarchy; direct callers of "
                        + counterparts + " are included with impactLevel VIA_OVERRIDE.");
            }

            List<ImpactAnalysisContextResponse.RouteImpact> routes =
                    GraphTraversals.affectedRoutes(graph, seedIds, MAX_ROUTE_DEPTH, MAX_AFFECTED_ROUTES).stream()
                            .map(route -> ImpactAnalysisContextResponse.RouteImpact.builder()
                                    .httpMethod(route.httpMethod())
                                    .routePath(route.routePath())
                                    .handlerFullName(route.handlerFullName())
                                    .build())
                            .toList();
            if (!routes.isEmpty()) {
                notes.add(routes.size() + " API route(s) can reach this symbol - treat as an API-affecting change.");
            }

            NodeDto owner = ownerClassOf(graph, target);
            if (owner != null) {
                long injections = graph.incoming(owner.getId()).stream()
                        .filter(edge -> "INJECTS".equals(edge.getType()))
                        .count();
                if (injections > 0) {
                    notes.add("Owner bean " + owner.getName() + " is injected in " + injections
                            + " place(s); public API changes ripple through Spring wiring.");
                }
            }

            return new Enrichment(routes, counterparts, counterpartImpacts, notes);
        } catch (RuntimeException ex) {
            return Enrichment.empty();
        }
    }

    private NodeDto ownerClassOf(GraphView graph, NodeDto target) {
        if (CLASSLIKE.contains(target.getType())) {
            return target;
        }
        String bare = GraphView.stripParens(target.getFullName() == null ? "" : target.getFullName());
        int lastDot = bare.lastIndexOf('.');
        return lastDot <= 0 ? null : graph.byFullName(bare.substring(0, lastDot));
    }

    private List<NodeDto> safeList(List<NodeDto> nodes) {
        return nodes == null ? List.of() : nodes;
    }

    private ImpactAnalysisResponse resolveImpact(String projectId, String nodeQuery, int depth, ImpactProfile profile) {
        // Default dependency profile delegates through the existing 3-arg contract to preserve backward compatibility.
        if (profile == ImpactProfile.DEPENDENCY) {
            return graphService.getImpactAnalysis(projectId, nodeQuery, depth);
        }
        return graphService.getImpactAnalysis(projectId, nodeQuery, depth, profile);
    }

    private ImpactAnalysisContextResponse toResponse(String projectId, String nodeQuery, int depth,
            ImpactProfile profile, ImpactAnalysisResponse impact, Enrichment enrichment) {
        NodeDto target = impact.getTarget();
        List<ImpactAnalysisContextResponse.NodeImpact> directImpact = impactNodes(impact.getWillBreak(), "WILL_BREAK", 1, MAX_DIRECT_IMPACT);
        List<ImpactAnalysisContextResponse.NodeImpact> transitiveImpact =
                mergeCounterparts(directImpact, transitiveImpact(impact), enrichment.counterpartImpacts());
        List<String> notes = new ArrayList<>(notes(profile));
        notes.addAll(enrichment.notes());
        return ImpactAnalysisContextResponse.builder()
                .projectId(projectId)
                .nodeQuery(nodeQuery)
                .depth(depth)
                .profile(profile.apiValue())
                .summary(ImpactAnalysisContextResponse.ImpactSummary.builder()
                        .targetId(target == null ? null : target.getId())
                        .targetType(target == null ? null : target.getType())
                        .targetName(target == null ? null : target.getName())
                        .targetFullName(target == null ? null : target.getFullName())
                        .directDependents(impact.getDirectDependents())
                        .totalDependents(impact.getTotalDependents())
                        .build())
                .directImpact(directImpact)
                .transitiveImpact(transitiveImpact)
                .affectedRoutes(enrichment.affectedRoutes())
                .relatedRoots(enrichment.relatedRoots())
                .riskLevel(impact.getRiskLevel())
                .notes(List.copyOf(notes))
                .warnings(warnings(impact, directImpact, transitiveImpact))
                .build();
    }

    /** Append counterpart (override) impacts not already present, within the transitive cap. */
    private List<ImpactAnalysisContextResponse.NodeImpact> mergeCounterparts(
            List<ImpactAnalysisContextResponse.NodeImpact> direct,
            List<ImpactAnalysisContextResponse.NodeImpact> transitive,
            List<ImpactAnalysisContextResponse.NodeImpact> counterparts) {
        if (counterparts.isEmpty()) {
            return transitive;
        }
        Set<String> seen = new LinkedHashSet<>();
        direct.forEach(node -> seen.add(node.getId()));
        transitive.forEach(node -> seen.add(node.getId()));
        List<ImpactAnalysisContextResponse.NodeImpact> merged = new ArrayList<>(transitive);
        for (ImpactAnalysisContextResponse.NodeImpact counterpart : counterparts) {
            if (merged.size() >= MAX_TRANSITIVE_IMPACT) {
                break;
            }
            if (seen.add(counterpart.getId())) {
                merged.add(counterpart);
            }
        }
        return List.copyOf(merged);
    }

    private ImpactAnalysisContextResponse notFoundResponse(String projectId, String nodeQuery, int depth, ImpactProfile profile) {
        return ImpactAnalysisContextResponse.builder()
                .projectId(projectId)
                .nodeQuery(nodeQuery)
                .depth(depth)
                .profile(profile.apiValue())
                .directImpact(List.of())
                .transitiveImpact(List.of())
                .affectedRoutes(List.of())
                .relatedRoots(List.of())
                .notes(List.of())
                .warnings(List.of("Impact target not found: " + nodeQuery))
                .build();
    }

    private ImpactAnalysisContextResponse unavailableResponse(String projectId, String nodeQuery, int depth, ImpactProfile profile) {
        return ImpactAnalysisContextResponse.builder()
                .projectId(projectId)
                .nodeQuery(nodeQuery)
                .depth(depth)
                .profile(profile.apiValue())
                .directImpact(List.of())
                .transitiveImpact(List.of())
                .affectedRoutes(List.of())
                .relatedRoots(List.of())
                .notes(List.of())
                .warnings(List.of("Impact analysis is temporarily unavailable."))
                .build();
    }

    private String validate(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength || hasControlCharacter(value)) {
            throw new IllegalArgumentException(fieldName + " must be non-blank, printable, and at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private void validateDepth(int depth) {
        if (!ALLOWED_DEPTHS.contains(depth)) {
            throw new IllegalArgumentException("depth must be one of 1, 2, 3, 5");
        }
    }

    private boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private List<ImpactAnalysisContextResponse.NodeImpact> transitiveImpact(ImpactAnalysisResponse impact) {
        List<ImpactAnalysisContextResponse.NodeImpact> likelyAffected = impactNodes(impact.getLikelyAffected(), "LIKELY_AFFECTED", 2, MAX_TRANSITIVE_IMPACT);
        int remaining = MAX_TRANSITIVE_IMPACT - likelyAffected.size();
        if (remaining <= 0) {
            return likelyAffected;
        }
        List<ImpactAnalysisContextResponse.NodeImpact> mayNeedTesting = impactNodes(impact.getMayNeedTesting(), "MAY_NEED_TESTING", 3, remaining);
        return List.copyOf(concat(likelyAffected, mayNeedTesting));
    }

    private List<ImpactAnalysisContextResponse.NodeImpact> impactNodes(List<NodeDto> nodes, String impactLevel, int depth, int limit) {
        if (nodes == null || nodes.isEmpty() || limit <= 0) {
            return List.of();
        }
        return nodes.stream()
                .filter(Objects::nonNull)
                .map(node -> toNodeImpact(node, impactLevel, depth))
                .sorted(nodeImpactComparator())
                .limit(limit)
                .toList();
    }

    private List<ImpactAnalysisContextResponse.NodeImpact> concat(
            List<ImpactAnalysisContextResponse.NodeImpact> first,
            List<ImpactAnalysisContextResponse.NodeImpact> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private ImpactAnalysisContextResponse.NodeImpact toNodeImpact(NodeDto node, String impactLevel, int depth) {
        return ImpactAnalysisContextResponse.NodeImpact.builder()
                .id(com.vibegraph.mcp.source.SourceGraphSupport.relativizePath(node.getId()))
                .type(node.getType())
                .name(node.getName())
                .fullName(com.vibegraph.mcp.source.SourceGraphSupport.relativizePath(node.getFullName()))
                .impactLevel(impactLevel)
                .depth(depth)
                .lineNumber(node.getLineNumber())
                .build();
    }

    private List<String> notes(ImpactProfile profile) {
        return List.of(
                "Impact profile: " + profile.apiValue() + " (relationships: " + profile.relationshipPattern() + ").",
                "Direct impact includes nodes related to the target at depth 1 under this profile.",
                "Transitive impact includes sampled nodes at depths 2 and 3.",
                "Risk level is computed by the existing graph impact service.");
    }

    private List<String> warnings(
            ImpactAnalysisResponse impact,
            List<ImpactAnalysisContextResponse.NodeImpact> directImpact,
            List<ImpactAnalysisContextResponse.NodeImpact> transitiveImpact) {
        long directCount = safeSize(impact.getWillBreak());
        long transitiveCount = safeSize(impact.getLikelyAffected()) + safeSize(impact.getMayNeedTesting());
        return java.util.stream.Stream.of(
                        limitWarning("directImpact", directImpact.size(), directCount),
                        limitWarning("transitiveImpact", transitiveImpact.size(), transitiveCount))
                .filter(Objects::nonNull)
                .toList();
    }

    private String limitWarning(String label, int returnedCount, long totalCount) {
        return totalCount > returnedCount ? label + " truncated to " + returnedCount + " of " + totalCount : null;
    }

    private long safeSize(List<NodeDto> nodes) {
        return nodes == null ? 0 : nodes.size();
    }

    private Comparator<ImpactAnalysisContextResponse.NodeImpact> nodeImpactComparator() {
        return Comparator.comparingInt(ImpactAnalysisContextResponse.NodeImpact::getDepth)
                .thenComparing(node -> safeString(node.getFullName()))
                .thenComparing(node -> safeString(node.getName()))
                .thenComparing(node -> safeString(node.getId()));
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
