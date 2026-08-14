package com.vibegraph.diagram.service.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.vibegraph.diagram.service.impl.UseCaseEndpointRules.Endpoint;

/**
 * B-M2 split (step 3): domain-name inference extracted verbatim from
 * {@link UseCaseInferenceEngine}. Turns an endpoint into a business domain name with an
 * evidence-based confidence (R3), and owns the per-domain accumulator the engine folds into goals.
 */
final class UseCaseDomainGuesser {

    // A reporting/read-only domain: an admin READS these, does not "manage" them. Used to keep the
    // verb honest ("View Analytics", not "Manage Analytics") even when the surface is admin-scoped.
    private static final Pattern REPORTING_DOMAIN = Pattern.compile(
            ".*(analytic|statistic|dashboard|audit|report|metric|insight).*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATH_VAR = Pattern.compile("\\{[^}]*}");

    private UseCaseDomainGuesser() {
    }

    /** Domain name plus the confidence and source of the evidence that produced it (R3). */
    record DomainGuess(String name, double confidence, String source) {
    }

    /** Mutable per-domain accumulator. */
    static final class DomainAgg {
        final Set<String> actors = new TreeSet<>();
        final Map<String, UseCaseActorGuesser.ActorGuess> actorMeta = new LinkedHashMap<>();
        final Set<String> controllerFqcns = new TreeSet<>();
        String domainLabel;
        boolean adminScoped;
        int endpointCount;
        int mutatingCount;
        // Best (max) evidence for the domain name across this bucket's endpoints (R3).
        double domainConfidence;
    }

    static String inferDomain(Endpoint ep) {
        return inferDomainGuess(ep).name();
    }

    /**
     * Domain inference with an evidence-based confidence (R3): a controller-derived name is strong
     * evidence (0.9), a path-segment fallback is weaker (0.6), and the "Resource" default is very
     * weak (0.3).
     */
    static DomainGuess inferDomainGuess(Endpoint ep) {
        if (ep.controller() != null && !ep.controller().isBlank()) {
            // Strip leading role/area words ("Admin", "Auth") so "AdminProductController" yields the
            // business entity "Product", not "Admin Product". Also strip trailing technical tokens
            // ("Rest", "Api") so "ProductRestController"/"UserApiController" yield "Product"/"User"
            // rather than the implementation-flavoured "Product Rest"/"User Api".
            String fromController = stripTechWords(
                    stripLeadingRoleWords(UseCaseNameNormalizer.singularizeWords(
                            UseCaseNameNormalizer.splitCamel(ep.controller()))));
            if (!fromController.isBlank() && !isRoleLikeDomain(fromController)
                    && !isTechDomain(fromController)) {
                return new DomainGuess(fromController, 0.9, "controller");
            }
        }
        for (String seg : ep.path().split("/")) {
            if (seg.isBlank() || PATH_VAR.matcher(seg).matches()) {
                continue;
            }
            String lower = seg.toLowerCase(Locale.ROOT);
            if (lower.equals("api") || lower.matches("v\\d+") || lower.equals("admin")
                    || lower.equals("auth")) {
                continue;
            }
            return new DomainGuess(
                    UseCaseNameNormalizer.capitalize(UseCaseNameNormalizer.singularize(seg)), 0.6, "path");
        }
        return new DomainGuess("Resource", 0.3, "fallback");
    }

    static boolean isRoleLikeDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT);
        return lower.equals("admin") || lower.equals("auth") || lower.equals("home") || lower.equals("index");
    }

    /** A reporting/read-only domain whose admin surface should read as "View", not "Manage". */
    static boolean isReportingDomain(String domain) {
        return domain != null && REPORTING_DOMAIN.matcher(domain).matches();
    }

    /** A domain that is purely a technical artefact (REST scaffolding, raw API, integrity probe). */
    static boolean isTechDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT).trim();
        return lower.equals("rest") || lower.equals("api") || lower.isEmpty()
                || lower.equals("delete check") || lower.equals("check delete");
    }

    /** Drop trailing technical tokens (Rest/Api) from a controller-derived domain. */
    static String stripTechWords(String domain) {
        String[] parts = domain.trim().split("\\s+");
        int end = parts.length;
        while (end > 1) {
            String last = parts[end - 1].toLowerCase(Locale.ROOT);
            if (last.equals("rest") || last.equals("api")) {
                end--;
            } else {
                break;
            }
        }
        if (end == parts.length) {
            return domain;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** Remove leading role/area words so role-prefixed controllers map to the bare business entity. */
    static String stripLeadingRoleWords(String domain) {
        String[] parts = domain.trim().split("\\s+");
        int start = 0;
        while (start < parts.length - 1 && isRoleLikeDomain(parts[start])) {
            start++;
        }
        if (start == 0) {
            return domain;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
