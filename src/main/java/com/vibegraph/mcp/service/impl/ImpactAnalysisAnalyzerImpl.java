package com.vibegraph.mcp.service.impl;

import java.util.Comparator;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImpactAnalysisAnalyzerImpl implements ImpactAnalysisAnalyzer {

    private static final int MAX_PROJECT_ID_LENGTH = 512;
    private static final int MAX_NODE_QUERY_LENGTH = 512;
    private static final int MAX_DIRECT_IMPACT = 50;
    private static final int MAX_TRANSITIVE_IMPACT = 100;
    private static final Set<Integer> ALLOWED_DEPTHS = Set.of(1, 2, 3, 5);

    private final GraphService graphService;

    @Override
    public ImpactAnalysisContextResponse analyzeImpact(String projectId, String nodeQuery, int depth, String profile) {
        String normalizedProjectId = validate(projectId, "projectId", MAX_PROJECT_ID_LENGTH);
        String normalizedNodeQuery = validate(nodeQuery, "nodeQuery", MAX_NODE_QUERY_LENGTH);
        validateDepth(depth);
        ImpactProfile impactProfile = ImpactProfile.fromApiValue(profile);
        try {
            ImpactAnalysisResponse impact = resolveImpact(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
            return toResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile, impact);
        } catch (ProjectNotFoundException ex) {
            throw ex;
        } catch (NodeNotFoundException ex) {
            return notFoundResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
        } catch (RuntimeException ex) {
            return unavailableResponse(normalizedProjectId, normalizedNodeQuery, depth, impactProfile);
        }
    }

    private ImpactAnalysisResponse resolveImpact(String projectId, String nodeQuery, int depth, ImpactProfile profile) {
        // Default dependency profile delegates through the existing 3-arg contract to preserve backward compatibility.
        if (profile == ImpactProfile.DEPENDENCY) {
            return graphService.getImpactAnalysis(projectId, nodeQuery, depth);
        }
        return graphService.getImpactAnalysis(projectId, nodeQuery, depth, profile);
    }

    private ImpactAnalysisContextResponse toResponse(String projectId, String nodeQuery, int depth, ImpactProfile profile, ImpactAnalysisResponse impact) {
        NodeDto target = impact.getTarget();
        List<ImpactAnalysisContextResponse.NodeImpact> directImpact = impactNodes(impact.getWillBreak(), "WILL_BREAK", 1, MAX_DIRECT_IMPACT);
        List<ImpactAnalysisContextResponse.NodeImpact> transitiveImpact = transitiveImpact(impact);
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
                .riskLevel(impact.getRiskLevel())
                .notes(notes(profile))
                .warnings(warnings(impact, directImpact, transitiveImpact))
                .build();
    }

    private ImpactAnalysisContextResponse notFoundResponse(String projectId, String nodeQuery, int depth, ImpactProfile profile) {
        return ImpactAnalysisContextResponse.builder()
                .projectId(projectId)
                .nodeQuery(nodeQuery)
                .depth(depth)
                .profile(profile.apiValue())
                .directImpact(List.of())
                .transitiveImpact(List.of())
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
                .id(node.getId())
                .type(node.getType())
                .name(node.getName())
                .fullName(node.getFullName())
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
