package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * Derives OMG UML 2.5.1 {@code «include»} relationships GENERICALLY from the project graph, with no
 * per-domain hardcoding. Runs at the display layer, after {@link UseCaseInferenceEngine} +
 * {@link BaLabelBeautifier} and before {@link UmlUseCaseRenderer}.
 *
 * <p><b>Why generic, not hardcoded.</b> The previous {@code SrsUseCaseEnricher} fabricated canonical
 * use cases for one domain (shipment tracking) that the analyzed code did not actually contain. That
 * looked polished on one project but is dishonest reverse-engineering and does not generalize. This
 * component instead reads a real signal the parser already captures — Spring {@code INJECTS} edges —
 * and only proposes an include when the evidence supports it.
 *
 * <p><b>The include rule (deliberately conservative).</b> A service type injected by the controllers
 * of {@code >= 2} distinct business use cases is a genuinely <em>shared sub-behaviour</em>. When that
 * service's name reads like a business capability ({@code PaymentService}, {@code ValidationService},
 * {@code NotificationService} — see {@link #BUSINESS_SERVICE}), it is promoted to an included use
 * case ({@code "Process Payment"}, {@code "Validate Input"}, ...) that each sharing base use case
 * {@code «include»}s. Infrastructure services (repository, mapper, logger, config, client, util,
 * gateway) are never promoted — they are implementation, not business behaviour.
 *
 * <p><b>No {@code «extend»} inference.</b> UML 2.5.1 {@code «extend»} models an OPTIONAL flow inserted
 * at a conditional extension point. That semantics cannot be recovered from a static call/inject
 * graph without guessing, so we never emit it; doing so would be a false positive. This limitation is
 * surfaced in the diagram warnings, which is the honest engineering position.
 *
 * <p>Inferred elements carry a low {@code confidence} (≈0.5) so the renderer can visually mark them as
 * heuristic rather than certain.
 */
@Component
public class GenericRelationInferer {

    private static final String REL_ASSOCIATION = "association";
    private static final String REL_INCLUDE = "include";
    private static final String LEVEL_BUSINESS = "business";

    /** Minimum distinct base use cases that must share a service before it becomes an include. */
    private static final int MIN_SHARING_USE_CASES = 2;

    /** Confidence stamped on every inferred include (heuristic, not certain). */
    private static final double INCLUDE_CONFIDENCE = 0.5;

    /**
     * A service whose simple name matches this reads like a business capability worth surfacing as an
     * included use case. Matched case-insensitively against the class simple name.
     */
    private static final Pattern BUSINESS_SERVICE = Pattern.compile(
            ".*(validat|verif|authentic|authoriz|notif|email|payment|billing|invoic|"
                    + "calculat|pricing|tax|audit|search|index|report|export|import|schedul|"
                    + "discount|inventory|shipping|delivery).*",
            Pattern.CASE_INSENSITIVE);

    /**
     * A service whose simple name matches this is infrastructure, never a business use case, even if
     * shared. Checked BEFORE {@link #BUSINESS_SERVICE} so e.g. {@code AuditRepository} stays out.
     */
    private static final Pattern INFRA_SERVICE = Pattern.compile(
            ".*(repository|dao|mapper|converter|logger|logging|config|properties|util|utils|"
                    + "helper|factory|client|gateway|adapter|template|holder|context|registry|"
                    + "provider|filter|interceptor|aspect|listener|publisher|serializer).*",
            Pattern.CASE_INSENSITIVE);

    /** Verb chosen by capability keyword, so the promoted use case reads as a goal (Verb + Noun). */
    private static final Map<String, String> CAPABILITY_VERB = new LinkedHashMap<>();
    static {
        CAPABILITY_VERB.put("validat", "Validate");
        CAPABILITY_VERB.put("verif", "Verify");
        CAPABILITY_VERB.put("authentic", "Authenticate");
        CAPABILITY_VERB.put("authoriz", "Authorize");
        CAPABILITY_VERB.put("notif", "Send Notification");
        CAPABILITY_VERB.put("email", "Send Email");
        CAPABILITY_VERB.put("payment", "Process Payment");
        CAPABILITY_VERB.put("billing", "Process Billing");
        CAPABILITY_VERB.put("invoic", "Generate Invoice");
        CAPABILITY_VERB.put("calculat", "Calculate");
        CAPABILITY_VERB.put("pricing", "Calculate Pricing");
        CAPABILITY_VERB.put("tax", "Calculate Tax");
        CAPABILITY_VERB.put("audit", "Record Audit");
        CAPABILITY_VERB.put("search", "Search");
        CAPABILITY_VERB.put("index", "Index");
        CAPABILITY_VERB.put("report", "Generate Report");
        CAPABILITY_VERB.put("export", "Export Data");
        CAPABILITY_VERB.put("import", "Import Data");
        CAPABILITY_VERB.put("schedul", "Schedule");
        CAPABILITY_VERB.put("discount", "Apply Discount");
        CAPABILITY_VERB.put("inventory", "Check Inventory");
        CAPABILITY_VERB.put("shipping", "Arrange Shipping");
        CAPABILITY_VERB.put("delivery", "Arrange Delivery");
    }

    /** Enriched, render-ready model. Same shape the renderer consumes. */
    public record EnrichedModel(List<Actor> actors, List<UseCaseElement> useCases, List<Relation> relations) {
    }

    /**
     * Add inferred shared-service includes to the model.
     *
     * @param actors          actors from the inference engine (unchanged)
     * @param useCases        business use cases (the engine's domain goals)
     * @param relations       existing relations (associations/generalizations)
     * @param useCaseServices ucId &rarr; injected service FQCNs, from
     *                        {@link UseCaseInferenceEngine.InferenceResult#useCaseServices()}
     * @return a model with extra include use cases + edges, or the input unchanged when no shared
     *         business service is found
     */
    public EnrichedModel enrich(List<Actor> actors, List<UseCaseElement> useCases,
            List<Relation> relations, Map<String, Set<String>> useCaseServices) {
        List<Actor> safeActors = actors == null ? List.of() : actors;
        List<UseCaseElement> safeUseCases = useCases == null ? List.of() : useCases;
        List<Relation> safeRelations = relations == null ? List.of() : relations;
        Map<String, Set<String>> services = useCaseServices == null ? Map.of() : useCaseServices;

        if (services.isEmpty() || safeUseCases.isEmpty()) {
            return new EnrichedModel(safeActors, safeUseCases, safeRelations);
        }

        // Invert: service FQCN -> set of base use case ids that inject it (deterministic order).
        Map<String, Set<String>> baseUseCasesByService = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
            String ucId = entry.getKey();
            for (String service : entry.getValue()) {
                baseUseCasesByService.computeIfAbsent(service, k -> new TreeSet<>()).add(ucId);
            }
        }

        List<UseCaseElement> newUseCases = new ArrayList<>(safeUseCases);
        List<Relation> newRelations = new ArrayList<>(safeRelations);
        Map<String, String> ucIdByLabel = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : baseUseCasesByService.entrySet()) {
            String serviceFqcn = entry.getKey();
            Set<String> bases = entry.getValue();
            if (bases.size() < MIN_SHARING_USE_CASES) {
                continue;
            }
            String simpleName = simpleName(serviceFqcn);
            if (INFRA_SERVICE.matcher(simpleName).matches() || !BUSINESS_SERVICE.matcher(simpleName).matches()) {
                continue;
            }
            String label = capabilityLabel(simpleName);
            if (label == null) {
                continue;
            }
            // De-duplicate by label so two services mapping to the same capability share one use case.
            String includeId = ucIdByLabel.get(label);
            if (includeId == null) {
                includeId = "UC_" + pascal(label);
                ucIdByLabel.put(label, includeId);
                newUseCases.add(UseCaseElement.builder()
                        .id(includeId)
                        .name(label)
                        .domain("shared")
                        .level(LEVEL_BUSINESS)
                        .source("inferred:shared-service:" + simpleName)
                        .sourceEndpoint(null)
                        .confidence(INCLUDE_CONFIDENCE)
                        .build());
            }
            for (String baseId : bases) {
                newRelations.add(includeEdge(baseId, includeId));
            }
        }

        return new EnrichedModel(safeActors, newUseCases, newRelations);
    }

    private String capabilityLabel(String simpleName) {
        String lower = simpleName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CAPABILITY_VERB.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private String simpleName(String fqcn) {
        if (fqcn == null) {
            return "";
        }
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 && dot < fqcn.length() - 1 ? fqcn.substring(dot + 1) : fqcn;
    }

    private Relation includeEdge(String from, String to) {
        return Relation.builder()
                .from(from).to(to)
                .type(REL_INCLUDE).label("<<include>>")
                .confidence(INCLUDE_CONFIDENCE).build();
    }

    private String pascal(String raw) {
        if (raw == null || raw.isBlank()) {
            return "X";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : raw.split("[^A-Za-z0-9]+")) {
            if (!token.isEmpty()) {
                sb.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "X" : out;
    }
}
