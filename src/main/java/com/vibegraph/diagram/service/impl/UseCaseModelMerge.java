package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * B-M2 split (step 3): same-name collapse + scope disambiguation extracted verbatim from
 * {@link UseCaseInferenceEngine}. Operates on the assembled use case list in place; ids are the
 * relation contract, so only display names may change here.
 */
final class UseCaseModelMerge {

    private UseCaseModelMerge() {
    }

    /**
     * Collapse use cases sharing the same display name onto a single node. Keeps the lowest-sorted
     * id (deterministic), rewrites every relation's endpoints to the survivor, drops self-loops and
     * duplicate edges, and merges injected-service sets so shared-service include inference still
     * sees the union.
     */
    static void mergeDuplicateNamedUseCases(List<UseCaseElement> useCases, List<Relation> relations,
            Map<String, Set<String>> useCaseServices) {
        // name -> surviving id. An admin-scoped id ("UC_Admin...") wins so the merged goal keeps its
        // administrative identity; otherwise the lowest-sorted id wins for determinism.
        //
        // Key by (admin-scope, name), NOT name alone: an admin-scoped goal and a non-admin goal that
        // beautify to the SAME label are two DIFFERENT actors' goals — e.g. a customer's "Manage
        // Orders" (their own orders, from POST /checkout) vs an admin's "Manage Orders" (all orders,
        // from GET /admin/all). Merging across scope collapses them onto the admin-scoped survivor and,
        // via the admin-association-drop below, ERASES the non-admin actor's goal entirely. Only
        // collapse genuine duplicates within the same scope.
        Map<String, String> survivorByName = new LinkedHashMap<>();
        for (UseCaseElement uc : useCases) {
            survivorByName.merge(scopedNameKey(uc), uc.getId(), (a, b) -> preferredSurvivor(a, b));
        }
        // old id -> survivor id (only for ids that are being merged away).
        Map<String, String> remap = new LinkedHashMap<>();
        for (UseCaseElement uc : useCases) {
            String survivor = survivorByName.get(scopedNameKey(uc));
            if (!survivor.equals(uc.getId())) {
                remap.put(uc.getId(), survivor);
            }
        }
        if (remap.isEmpty()) {
            return;
        }

        List<UseCaseElement> merged = new ArrayList<>();
        for (UseCaseElement uc : useCases) {
            if (remap.containsKey(uc.getId())) {
                continue;
            }
            merged.add(uc);
        }
        useCases.clear();
        useCases.addAll(merged);

        // Rewire relations onto the survivor; drop self-loops and duplicated edges.
        Set<String> seen = new HashSet<>();
        List<Relation> kept = new ArrayList<>();
        for (Relation rel : relations) {
            String from = remap.getOrDefault(rel.getFrom(), rel.getFrom());
            String to = remap.getOrDefault(rel.getTo(), rel.getTo());
            if (from.equals(to)) {
                continue;
            }
            String dedupKey = from + "|" + rel.getType() + "|" + to;
            if (!seen.add(dedupKey)) {
                continue;
            }
            kept.add(Relation.builder()
                    .from(from)
                    .to(to)
                    .type(rel.getType())
                    .label(rel.getLabel())
                    .confidence(rel.getConfidence())
                    .build());
        }
        relations.clear();
        relations.addAll(kept);

        // Union the injected services of the merged-away goals onto the survivor.
        for (Map.Entry<String, String> entry : remap.entrySet()) {
            Set<String> moved = useCaseServices.remove(entry.getKey());
            if (moved != null && !moved.isEmpty()) {
                useCaseServices.computeIfAbsent(entry.getValue(), k -> new TreeSet<>()).addAll(moved);
            }
        }
    }

    /**
     * Append a deterministic scope qualifier to use cases whose display name still collides across
     * scopes after merge. The colliding pair is always one admin-scoped goal and one non-admin goal
     * (same-scope collisions were merged away), so: admin-scoped &rarr; " (All)" (manages every
     * resource), non-admin &rarr; " (Own)" (acts on their own resources). Names that appear once are
     * left untouched. Only the display name changes; relations key off ids, so they are unaffected.
     */
    static void disambiguateScopedDuplicates(List<UseCaseElement> useCases) {
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        for (UseCaseElement uc : useCases) {
            nameCounts.merge(uc.getName(), 1, Integer::sum);
        }
        for (int i = 0; i < useCases.size(); i++) {
            UseCaseElement uc = useCases.get(i);
            if (nameCounts.getOrDefault(uc.getName(), 0) <= 1) {
                continue;
            }
            String qualifier = isAdminScopedId(uc.getId()) ? " (All)" : " (Own)";
            useCases.set(i, UseCaseElement.builder()
                    .id(uc.getId())
                    .name(uc.getName() + qualifier)
                    .domain(uc.getDomain())
                    .level(uc.getLevel())
                    .source(uc.getSource())
                    .sourceEndpoint(uc.getSourceEndpoint())
                    .confidence(uc.getConfidence())
                    .build());
        }
    }

    /** Choose the surviving id when two use cases share a name: admin-scoped wins, else lowest id. */
    private static String preferredSurvivor(String a, String b) {
        boolean aAdmin = isAdminScopedId(a);
        boolean bAdmin = isAdminScopedId(b);
        if (aAdmin != bAdmin) {
            return aAdmin ? a : b;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static boolean isAdminScopedId(String id) {
        return id != null && id.startsWith("UC_Admin");
    }

    /**
     * Merge key that keeps admin-scoped and non-admin goals separate even when they share a display
     * name, so a same-label collision between two actors' goals cannot erase the non-admin one.
     */
    private static String scopedNameKey(UseCaseElement uc) {
        return (isAdminScopedId(uc.getId()) ? "admin|" : "user|") + uc.getName();
    }
}
