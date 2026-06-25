package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * Infers a single, canonical business-level UML use case model from the project graph.
 *
 * <p>This engine deliberately produces a <em>business actor/goal view</em> (OMG UML 2.5.1),
 * <strong>not</strong> an API/endpoint/CRUD map. Each business domain is collapsed into one
 * goal-level use case ("Manage Products" when the domain is write-capable, otherwise
 * "View Products"); authentication endpoints become the anonymous {@code Guest} goals
 * "Register account" / "Log in". The output is identical regardless of the requested mode,
 * because there is exactly one correct use case diagram for a given project.
 *
 * <p>Input is limited to what the parser captures: APIEndpoint/Route nodes carry
 * {@code httpMethod} + {@code routePath}, and {@code HANDLES_ROUTE} edges point from the
 * controller method FQCN to the route id. Security annotations are NOT in the graph, so actor
 * roles are inferred heuristically (path {@code /admin}, else the default authenticated user)
 * and flagged via a warning.
 */
@Component
public class UseCaseInferenceEngine {

    private static final String ROUTE_NODE_TYPE = "Route";
    private static final String API_ENDPOINT_NODE_TYPE = "APIEndpoint";
    private static final String HANDLES_ROUTE_EDGE = "HANDLES_ROUTE";
    private static final String INJECTS_EDGE = "INJECTS";

    private static final String ACTOR_GUEST = "Guest";
    private static final String ACTOR_USER = "User";
    private static final String ACTOR_ADMIN = "Admin";

    // UML 2.5.1 relationship kinds (render-agnostic; see UmlUseCaseRenderer).
    private static final String REL_ASSOCIATION = "association";
    private static final String REL_GENERALIZATION = "generalization";

    private static final String LEVEL_BUSINESS = "business";

    // Verb selection threshold (R2: anti-amplification). A domain becomes a "Manage" goal when it is
    // admin-scoped OR at least this FRACTION of its endpoints mutate state — NOT merely because a
    // single endpoint writes. 0.25 blocks a stray write in a read-mostly domain (1/5 = 0.20 stays
    // "View") while keeping genuine management surfaces (>=2/5 = 0.40 reads as "Manage"). Chosen so no
    // labelled fixture regresses; validated by the eval harness.
    private static final double WRITE_RATIO_THRESHOLD = 0.25;

    // Confidence below which a use case is flagged as needing human review (R3).
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

    // A reporting/read-only domain: an admin READS these, does not "manage" them. Used to keep the
    // verb honest ("View Analytics", not "Manage Analytics") even when the surface is admin-scoped.
    private static final Pattern REPORTING_DOMAIN = Pattern.compile(
            ".*(analytic|statistic|dashboard|audit|report|metric|insight).*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTROLLER_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)Controller");
    private static final Pattern PATH_VAR = Pattern.compile("\\{[^}]*}");
    private static final Pattern STATIC_ASSET = Pattern.compile(".*\\.(css|js|mjs|map|png|jpe?g|gif|svg|ico|woff2?|ttf|eot|html?|txt|webp|avif)$",
            Pattern.CASE_INSENSITIVE);
    // Frontend SPA screen/page routes (not business REST resources). A REST resource segment is a
    // plain noun ("products", "user-accounts"); a screen route carries a presentational prefix
    // ("add-new-cards", "edit-personal-infos", "my-orders") or a demo/test/detail suffix
    // ("mobile-demos", "order-details"). These are presentation, never a business goal.
    private static final Pattern UI_SCREEN_PREFIX = Pattern.compile(
            "(add|edit|view|new|my|mobile|reset|check|cc)-[a-z0-9-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern UI_SCREEN_SUFFIX = Pattern.compile(
            ".+-(demo|demos|test|tests|info|infos|detail|details)", Pattern.CASE_INSENSITIVE);
    // Technical/plumbing route segments that are implementation artefacts, never business goals:
    // REST scaffolding ("product-rests", "rest"), raw API management routes ("user-apis", "apis"),
    // and soft-delete/integrity probes ("delete-checks", "check-deletes").
    private static final Pattern TECH_SEGMENT = Pattern.compile(
            "(.+-)?(rest|rests|api|apis)|.*(delete-check|check-delete)[a-z]*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Result of inference: deterministic, render-agnostic graph of the use case diagram.
     *
     * @param useCaseServices ucId &rarr; set of service-type FQCNs the domain's controllers inject.
     *                        Empty when no INJECTS edges are present. Consumed by
     *                        {@code GenericRelationInferer} to derive shared-service includes.
     */
    public record InferenceResult(
            List<Actor> actors,
            List<UseCaseElement> useCases,
            List<Relation> relations,
            List<String> warnings,
            Map<String, Set<String>> useCaseServices) {
    }

    /**
     * One inferred endpoint (a single HTTP operation) before it is folded into a domain goal.
     */
    private record Endpoint(
            String routeId,
            String httpMethod,
            String path,
            String controller,
            String controllerFqcn,
            String requiredRole) {
    }

    /**
     * Build the canonical business use case model for the given graph.
     *
     * @param graph the project graph (may be null/empty)
     * @param mode  retained for API compatibility but ignored — the model is mode-independent
     */
    public InferenceResult infer(GraphDataResponse graph, String mode) {
        List<Endpoint> endpoints = collectEndpoints(graph);

        Map<String, Actor> actorsByName = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();
        boolean roleGuessed = false;

        // Per-domain accumulation for business (non-auth) endpoints, deterministic by domain.
        Map<String, DomainAgg> domains = new TreeMap<>();
        boolean hasRegister = false;
        boolean hasLogin = false;

        for (Endpoint ep : endpoints) {
            AuthKind auth = authKind(ep);
            if (auth == AuthKind.REGISTER) {
                hasRegister = true;
                continue;
            }
            if (auth == AuthKind.LOGIN) {
                hasLogin = true;
                continue;
            }

            DomainGuess domainGuess = inferDomainGuess(ep);
            String domain = domainGuess.name();
            ActorGuess actor = inferActor(ep);
            roleGuessed |= actor.guessed();

            // Separate the administrative surface of a domain from its public surface. The same
            // entity is often exposed twice — e.g. GET /api/brands (a shopper viewing brands) and
            // /api/admin/brands (an admin managing them). Collapsing both into one goal forces a
            // single actor and loses the Admin assignment, so bucket admin endpoints on their own.
            boolean adminScoped = ACTOR_ADMIN.equals(actor.name());
            String domainKey = (adminScoped ? "admin:" : "") + domain;

            DomainAgg agg = domains.computeIfAbsent(domainKey, k -> new DomainAgg());
            agg.domainLabel = domain;
            agg.adminScoped = adminScoped;
            agg.actors.add(actor.name());
            agg.actorMeta.putIfAbsent(actor.name(), actor);
            agg.endpointCount++;
            agg.domainConfidence = Math.max(agg.domainConfidence, domainGuess.confidence());
            if (ep.controllerFqcn() != null && !ep.controllerFqcn().isBlank()) {
                agg.controllerFqcns.add(ep.controllerFqcn());
            }
            if (isMutating(ep.httpMethod())) {
                agg.mutatingCount++;
            }
        }

        // Controller class FQCN -> injected service-type FQCNs (for shared-service include inference).
        Map<String, Set<String>> servicesByController = collectInjects(graph);
        Map<String, Set<String>> useCaseServices = new LinkedHashMap<>();

        List<UseCaseElement> useCases = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();

        // Anonymous Guest goals: register/login are the canonical pre-authentication use cases.
        if (hasRegister || hasLogin) {
            Actor guest = registerActor(actorsByName, usedIds, ACTOR_GUEST, "anonymous", 0.9);
            if (hasRegister) {
                UseCaseElement uc = businessUseCase(usedIds, "Register account", "auth", "auth:register", 0.85);
                useCases.add(uc);
                relations.add(association(guest, uc));
            }
            if (hasLogin) {
                UseCaseElement uc = businessUseCase(usedIds, "Log in", "auth", "auth:login", 0.85);
                useCases.add(uc);
                relations.add(association(guest, uc));
            }
        }

        // One goal-level use case per business domain (admin and public surfaces are separate keys).
        for (Map.Entry<String, DomainAgg> entry : domains.entrySet()) {
            DomainAgg agg = entry.getValue();
            String domain = agg.domainLabel;
            String plural = pluralName(domain);
            // Anti-amplification (R2): a domain becomes a "Manage" goal when it is admin-scoped OR its
            // mutating-endpoint ratio exceeds the threshold, NOT merely because a single endpoint
            // writes. A public surface keeps the read/write distinction (a shopper browsing brands is
            // "View", not "Manage").
            double writeRatio = agg.endpointCount == 0
                    ? 0.0 : (double) agg.mutatingCount / agg.endpointCount;
            boolean hasWriteEvidence = writeRatio >= WRITE_RATIO_THRESHOLD && agg.mutatingCount > 0;
            // A read-only reporting domain (analytics, statistics, dashboard, audit, report) is a VIEW
            // goal even for an admin — the admin reads reports, does not "manage" them. Without this,
            // admin-scope would force "Manage Analytics"/"Manage Audit Logs", misreading a pure read
            // surface. Entity domains (Product, Brand, ...) keep the admin => Manage convention.
            boolean manage;
            if (isReportingDomain(domain) && !hasWriteEvidence) {
                manage = false;
            } else {
                manage = agg.adminScoped || hasWriteEvidence;
            }
            String verb = manage ? "Manage " : "View ";
            String name = verb + plural;
            String idBase = "UC_" + (agg.adminScoped ? "Admin" : "") + verb.trim() + pascal(plural);
            // Confidence (R3): weakest-link of the domain-name evidence and the weakest actor evidence,
            // so a goal is never reported as more certain than the weakest fact it was built from.
            double domainConf = agg.domainConfidence > 0 ? agg.domainConfidence : 0.3;
            double ucConfidence = Math.min(domainConf, minActorConfidence(agg.actorMeta));
            UseCaseElement uc = UseCaseElement.builder()
                    .id(uniqueId(idBase, usedIds))
                    .name(name)
                    .domain(domain)
                    .level(LEVEL_BUSINESS)
                    .source("domain:" + entry.getKey())
                    .sourceEndpoint(null)
                    .confidence(ucConfidence)
                    .build();
            useCases.add(uc);

            // Collect the services this domain's controllers inject, keyed by the use case id.
            Set<String> services = new TreeSet<>();
            for (String controllerFqcn : agg.controllerFqcns) {
                Set<String> injected = servicesByController.get(controllerFqcn);
                if (injected != null) {
                    services.addAll(injected);
                }
            }
            if (!services.isEmpty()) {
                useCaseServices.put(uc.getId(), services);
            }

            for (String actorName : agg.actors) {
                ActorGuess meta = agg.actorMeta.get(actorName);
                Actor a = registerActor(actorsByName, usedIds, actorName, meta.source(), meta.confidence());
                relations.add(association(a, uc));
            }
        }

        // Merge use cases that ended up with the same display name. The admin and public surfaces of
        // a domain are separated upstream, but they can still beautify to the SAME label (e.g. both
        // an /api/orders/admin/all and /api/orders/... bucket read as "Manage Orders"). Two ovals with
        // identical text is wrong in UML, so collapse them onto one node, rewiring every relation and
        // merging their injected services. The lowest-sorted id wins for determinism.
        mergeDuplicateNamedUseCases(useCases, relations, useCaseServices);

        // After merging genuine same-scope duplicates, two SURVIVING goals can still share a display
        // name across DIFFERENT scopes (a customer's "Manage Orders" vs an admin's "Manage Orders").
        // Two identically-labelled ovals is confusing UML, so append a deterministic, fact-based scope
        // qualifier. The scope is a fact (admin-scoped id), not an LLM guess — so this stays testable.
        disambiguateScopedDuplicates(useCases);

        // UML actor generalization. Only Administrator --|> Registered User is modelled: an admin IS
        // a privileged user (a true role specialization), so inheriting the user's goals is correct.
        //
        // We deliberately DO NOT generalize Registered User --|> Guest. Guest and Registered User are
        // authentication STATES of the same person, not a subtype hierarchy. Generalizing them would
        // (per UML 2.5 actor-generalization semantics) let a logged-in Registered User perform the
        // Guest-only goals "Register Account" / "Log In" — which is semantically wrong. Guest stays an
        // independent actor wired only to the pre-authentication goals.
        Actor adminActor = actorsByName.get(ACTOR_ADMIN);
        Actor userActor = actorsByName.get(ACTOR_USER);
        if (adminActor != null && userActor != null) {
            relations.add(generalization(adminActor, userActor));

            // Because Admin inherits User's goals through the generalization, a direct Admin
            // association to a use case that User already reaches is redundant and clutters the
            // diagram. Drop those duplicates; keep only Admin-exclusive associations.
            Set<String> userTargets = new HashSet<>();
            for (Relation rel : relations) {
                if (REL_ASSOCIATION.equals(rel.getType()) && userActor.getId().equals(rel.getFrom())) {
                    userTargets.add(rel.getTo());
                }
            }
            relations.removeIf(rel -> REL_ASSOCIATION.equals(rel.getType())
                    && adminActor.getId().equals(rel.getFrom())
                    && userTargets.contains(rel.getTo()));
        }

        List<Actor> actors = new ArrayList<>(actorsByName.values());
        actors.sort(Comparator
                .comparingInt((Actor actor) -> actorOrder(actor.getName()))
                .thenComparing(Actor::getName));

        List<String> warnings = new ArrayList<>();
        if (roleGuessed) {
            warnings.add("Actor roles were inferred from HTTP path heuristics because the parser does "
                    + "not capture security annotations. Verify Guest/User/Admin assignments.");
        }
        if (useCases.isEmpty()) {
            warnings.add("No business use cases were detected after applying the exclusion list.");
        }
        // R3: surface low-confidence goals so the reader knows which ones to verify.
        List<String> lowConfidence = new ArrayList<>();
        for (UseCaseElement uc : useCases) {
            if (uc.getConfidence() != null && uc.getConfidence() < LOW_CONFIDENCE_THRESHOLD) {
                lowConfidence.add(uc.getName());
            }
        }
        if (!lowConfidence.isEmpty()) {
            warnings.add("Low-confidence use cases (inferred from weak evidence, please verify): "
                    + String.join(", ", lowConfidence) + ".");
        }

        return new InferenceResult(actors, useCases, relations, warnings, useCaseServices);
    }

    /**
     * Collapse use cases sharing the same display name onto a single node. Keeps the lowest-sorted
     * id (deterministic), rewrites every relation's endpoints to the survivor, drops self-loops and
     * duplicate edges, and merges injected-service sets so shared-service include inference still
     * sees the union.
     */
    private void mergeDuplicateNamedUseCases(List<UseCaseElement> useCases, List<Relation> relations,
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
        // Drop the merged-away use case nodes.
        useCases.removeIf(uc -> remap.containsKey(uc.getId()));
        // Merge their services into the survivor.
        for (Map.Entry<String, String> e : remap.entrySet()) {
            Set<String> moved = useCaseServices.remove(e.getKey());
            if (moved != null && !moved.isEmpty()) {
                useCaseServices.computeIfAbsent(e.getValue(), k -> new TreeSet<>()).addAll(moved);
            }
        }
        // Rewrite relation endpoints, then drop self-loops and exact duplicates.
        Set<String> seen = new HashSet<>();
        List<Relation> rewritten = new ArrayList<>();
        for (Relation rel : relations) {
            String from = remap.getOrDefault(rel.getFrom(), rel.getFrom());
            String to = remap.getOrDefault(rel.getTo(), rel.getTo());
            if (from.equals(to)) {
                continue;
            }
            String key = rel.getType() + "|" + from + "|" + to;
            if (!seen.add(key)) {
                continue;
            }
            rewritten.add(Relation.builder()
                    .from(from).to(to).type(rel.getType())
                    .label(rel.getLabel()).confidence(rel.getConfidence()).build());
        }
        // An administrative goal (its surviving id is admin-scoped) belongs to the Administrator only.
        // A public surface that merged into it (e.g. /api/user/current beautified to the same
        // "Manage User Accounts" label) must not grant that authority to the Registered User, so drop
        // non-admin associations to admin-scoped goals.
        rewritten.removeIf(rel -> REL_ASSOCIATION.equals(rel.getType())
                && isAdminScopedId(rel.getTo())
                && !rel.getFrom().equals("A_" + ACTOR_ADMIN));
        relations.clear();
        relations.addAll(rewritten);
    }

    /**
     * Append a deterministic scope qualifier to use cases whose display name still collides across
     * scopes after merge. The colliding pair is always one admin-scoped goal and one non-admin goal
     * (same-scope collisions were merged away), so: admin-scoped &rarr; " (All)" (manages every
     * resource), non-admin &rarr; " (Own)" (acts on their own resources). Names that appear once are
     * left untouched. Only the display name changes; relations key off ids, so they are unaffected.
     */
    private void disambiguateScopedDuplicates(List<UseCaseElement> useCases) {
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
    private String preferredSurvivor(String a, String b) {
        boolean aAdmin = isAdminScopedId(a);
        boolean bAdmin = isAdminScopedId(b);
        if (aAdmin != bAdmin) {
            return aAdmin ? a : b;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private boolean isAdminScopedId(String id) {
        return id != null && id.startsWith("UC_Admin");
    }

    /**
     * Merge key that keeps admin-scoped and non-admin goals separate even when they share a display
     * name, so a same-label collision between two actors' goals cannot erase the non-admin one.
     */
    private String scopedNameKey(UseCaseElement uc) {
        return (isAdminScopedId(uc.getId()) ? "admin|" : "user|") + uc.getName();
    }

    /**
     * The source of an INJECTS edge is the owning class FQCN (e.g. {@code com.x.ProductController}),
     * which joins the controller FQCN derived from HANDLES_ROUTE. Returns an empty map when the graph
     * carries no INJECTS edges (e.g. a frontend-only project), making shared-service inference a no-op.
     */
    private Map<String, Set<String>> collectInjects(GraphDataResponse graph) {
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();
        Map<String, Set<String>> byController = new LinkedHashMap<>();
        for (EdgeDto edge : edges) {
            if (edge == null || !INJECTS_EDGE.equals(edge.getType())) {
                continue;
            }
            String owner = edge.getSource();
            String service = edge.getTarget();
            if (owner == null || owner.isBlank() || service == null || service.isBlank()) {
                continue;
            }
            byController.computeIfAbsent(owner, k -> new TreeSet<>()).add(service);
        }
        return byController;
    }

    // ---- aggregation helpers -------------------------------------------------

    /** Mutable per-domain accumulator. */
    private static final class DomainAgg {
        final Set<String> actors = new TreeSet<>();
        final Map<String, ActorGuess> actorMeta = new LinkedHashMap<>();
        final Set<String> controllerFqcns = new TreeSet<>();
        String domainLabel;
        boolean adminScoped;
        int endpointCount;
        int mutatingCount;
        // Best (max) evidence for the domain name across this bucket's endpoints (R3).
        double domainConfidence;
    }

    /** Domain name plus the confidence and source of the evidence that produced it (R3). */
    private record DomainGuess(String name, double confidence, String source) {
    }

    /** Weakest actor confidence in a domain bucket; 0.5 when none is recorded. */
    private double minActorConfidence(Map<String, ActorGuess> actorMeta) {
        double min = 1.0;
        boolean any = false;
        for (ActorGuess g : actorMeta.values()) {
            if (g != null && g.confidence() != null) {
                min = Math.min(min, g.confidence());
                any = true;
            }
        }
        return any ? min : 0.5;
    }

    private Actor registerActor(Map<String, Actor> byName, Set<String> usedIds, String name,
            String source, double confidence) {
        return byName.computeIfAbsent(name, n -> Actor.builder()
                .id(uniqueId("A_" + pascal(n), usedIds))
                .name(n)
                .source(source)
                .confidence(confidence)
                .build());
    }

    private UseCaseElement businessUseCase(Set<String> usedIds, String name, String domain,
            String source, double confidence) {
        return UseCaseElement.builder()
                .id(uniqueId("UC_" + pascal(name), usedIds))
                .name(name)
                .domain(domain)
                .level(LEVEL_BUSINESS)
                .source(source)
                .sourceEndpoint(null)
                .confidence(confidence)
                .build();
    }

    private Relation association(Actor actor, UseCaseElement useCase) {
        return Relation.builder()
                .from(actor.getId()).to(useCase.getId())
                .type(REL_ASSOCIATION).label(null)
                .confidence(actor.getConfidence()).build();
    }

    /** Actor generalization {@code child --|> parent} (child inherits the parent's goals). */
    private Relation generalization(Actor child, Actor parent) {
        return Relation.builder()
                .from(child.getId()).to(parent.getId())
                .type(REL_GENERALIZATION).label("generalizes")
                .confidence(0.6).build();
    }

    // ---- endpoint collection -------------------------------------------------

    private List<Endpoint> collectEndpoints(GraphDataResponse graph) {
        List<NodeDto> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();

        Map<String, String> controllerByRoute = new LinkedHashMap<>();
        Map<String, String> controllerFqcnByRoute = new LinkedHashMap<>();
        for (EdgeDto edge : edges) {
            if (edge == null || !HANDLES_ROUTE_EDGE.equals(edge.getType())) {
                continue;
            }
            if (edge.getTarget() == null || edge.getTarget().isBlank()) {
                continue;
            }
            controllerByRoute.putIfAbsent(edge.getTarget(), controllerName(edge.getSource()));
            controllerFqcnByRoute.putIfAbsent(edge.getTarget(), classFqcnFromMethod(edge.getSource()));
        }

        // Deterministic by route id.
        Map<String, Endpoint> byRoute = new TreeMap<>();
        Set<String> viewPageRoutes = new HashSet<>();
        for (NodeDto node : nodes) {
            if (node == null || node.getId() == null || !isRouteNode(node)) {
                continue;
            }
            // Drop server-side view (page) GET routes: a page is presentation, not a business goal.
            // A mutating view route (e.g. a form POST returning a redirect) is a real action — keep it.
            if (isViewPageRoute(node)) {
                viewPageRoutes.add(node.getId());
                continue;
            }
            Endpoint ep = toEndpoint(node.getId(), node, controllerByRoute.get(node.getId()),
                    controllerFqcnByRoute.get(node.getId()));
            if (ep != null && !isExcluded(ep.path())) {
                byRoute.putIfAbsent(ep.routeId(), ep);
            }
        }
        // Routes referenced only by an edge (no node present).
        for (String routeId : controllerByRoute.keySet()) {
            if (byRoute.containsKey(routeId) || viewPageRoutes.contains(routeId)) {
                continue;
            }
            Endpoint ep = toEndpoint(routeId, null, controllerByRoute.get(routeId),
                    controllerFqcnByRoute.get(routeId));
            if (ep != null && !isExcluded(ep.path())) {
                byRoute.putIfAbsent(routeId, ep);
            }
        }
        return new ArrayList<>(byRoute.values());
    }

    private Endpoint toEndpoint(String routeId, NodeDto node, String controller, String controllerFqcn) {
        String method = null;
        String path = null;
        String requiredRole = null;
        if (node != null && node.getProperties() != null) {
            Object m = node.getProperties().get("httpMethod");
            Object p = node.getProperties().get("routePath");
            Object role = node.getProperties().get("requiredRole");
            method = m != null ? String.valueOf(m) : null;
            path = p != null ? String.valueOf(p) : null;
            requiredRole = role != null ? String.valueOf(role) : null;
        }
        if ((method == null || path == null) && routeId != null) {
            // Fall back to parsing the route id, e.g. "GET /api/users".
            int sp = routeId.indexOf(' ');
            if (sp > 0) {
                if (method == null) {
                    method = routeId.substring(0, sp);
                }
                if (path == null) {
                    path = routeId.substring(sp + 1).trim();
                }
            }
        }
        if (method == null || path == null || path.isBlank()) {
            return null;
        }
        return new Endpoint(routeId, method.toUpperCase(Locale.ROOT), path, controller, controllerFqcn, requiredRole);
    }

    private String controllerName(String fqcn) {
        if (fqcn == null) {
            return null;
        }
        Matcher m = CONTROLLER_NAME.matcher(fqcn);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Strip the trailing {@code .method} from a HANDLES_ROUTE source so it joins INJECTS edges,
     * whose source is the controller <em>class</em> FQCN. Example:
     * {@code com.x.ProductController.create} &rarr; {@code com.x.ProductController}.
     */
    private String classFqcnFromMethod(String methodFqcn) {
        if (methodFqcn == null || methodFqcn.isBlank()) {
            return null;
        }
        int lastDot = methodFqcn.lastIndexOf('.');
        if (lastDot <= 0) {
            return methodFqcn;
        }
        return methodFqcn.substring(0, lastDot);
    }

    private boolean isRouteNode(NodeDto node) {
        return ROUTE_NODE_TYPE.equals(node.getType()) || API_ENDPOINT_NODE_TYPE.equals(node.getType());
    }

    /**
     * True when a node is a server-side view (page) route AND is a read/navigation request. Such a
     * route is pure presentation (the parser marks it with {@code view=true} for a plain
     * {@code @Controller} handler that returns a template/redirect). A mutating view route (form POST)
     * performs a real business action and is NOT treated as a page.
     */
    private boolean isViewPageRoute(NodeDto node) {
        if (node.getProperties() == null) {
            return false;
        }
        Object v = node.getProperties().get("view");
        boolean isView = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
        if (!isView) {
            return false;
        }
        Object p = node.getProperties().get("routePath");
        String path = p != null ? String.valueOf(p).toLowerCase(Locale.ROOT) : "";
        // Login/registration are pre-auth business goals (Guest's "Log In"/"Register Account") even
        // when served as a server-side form page — never drop them as mere presentation.
        if (isAuthBusinessPath(path)) {
            return false;
        }
        Object m = node.getProperties().get("httpMethod");
        String method = m != null ? String.valueOf(m).toUpperCase(Locale.ROOT) : "";
        return !isMutating(method);
    }

    /** Path that denotes a pre-authentication business goal (login/registration). */
    private boolean isAuthBusinessPath(String p) {
        return p.contains("register") || p.contains("signup") || p.contains("sign-up")
                || p.contains("login") || p.contains("signin") || p.contains("sign-in");
    }

    // ---- exclusion -----------------------------------------------------------

    boolean isExcluded(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (p.startsWith("/actuator") || p.equals("/error") || p.startsWith("/health")
                || p.startsWith("/metrics") || p.startsWith("/swagger-ui") || p.startsWith("/v3/api-docs")
                || p.startsWith("/api/docs") || p.startsWith("/static") || p.startsWith("/assets")
                || p.startsWith("/webjars") || p.equals("/favicon.ico")) {
            return true;
        }
        // Internal / debug / test plumbing is not a business use case.
        if (p.contains("/debug") || p.contains("/internal") || p.contains("/__")
                || p.startsWith("/test") || p.contains("/test/")) {
            return true;
        }
        if (STATIC_ASSET.matcher(p).matches()) {
            return true;
        }
        // Non-business landing / index routes are not use cases.
        if (p.equals("/") || p.equals("/home") || p.startsWith("/home/")
                || p.equals("/index") || p.startsWith("/index/")) {
            return true;
        }
        // Frontend SPA screen/page routes are presentation, not business goals. A path whose last
        // meaningful segment is a presentational screen name ("add-new-cards", "mobile-demos",
        // "edit-personal-infos") is dropped so the diagram stays at the actor-goal altitude OMG UML
        // 2.5.1 expects, instead of mirroring the UI navigation tree.
        if (isUiScreenRoute(p)) {
            return true;
        }
        // /api/auth: keep only the business goals (register/login); everything else under /auth is
        // technical plumbing (token, refresh, logout, "current user" probes) and is excluded.
        if (p.contains("/auth/") || p.endsWith("/auth")) {
            boolean business = p.contains("register") || p.contains("signup") || p.contains("sign-up")
                    || p.contains("login") || p.contains("signin") || p.contains("sign-in");
            return !business;
        }
        return false;
    }

    /**
     * True when the last meaningful path segment is a presentational SPA screen name rather than a
     * business resource. Catches {@code /add-new-cards}, {@code /mobile-demos},
     * {@code /edit-personal-infos}, {@code /cc-doctors}, {@code /order-details} — page routes a CRUD
     * scaffold emits per screen. Plain resource segments ({@code /products}, {@code /user-accounts})
     * are preserved, so genuine read goals like {@code View Categories} survive.
     */
    private boolean isUiScreenRoute(String lowerPath) {
        String last = null;
        for (String seg : lowerPath.split("/")) {
            if (seg.isBlank() || PATH_VAR.matcher(seg).matches()) {
                continue;
            }
            if (seg.equals("api") || seg.matches("v\\d+")) {
                continue;
            }
            // A technical segment ANYWHERE in the path marks the whole route as plumbing, e.g.
            // /api/admin/check-delete/brand/{id} — the business tail ("brand") is just a parameter of
            // an integrity probe, not a goal.
            if (TECH_SEGMENT.matcher(seg).matches()) {
                return true;
            }
            last = seg;
        }
        if (last == null) {
            return false;
        }
        return UI_SCREEN_PREFIX.matcher(last).matches()
                || UI_SCREEN_SUFFIX.matcher(last).matches();
    }

    private enum AuthKind { REGISTER, LOGIN }

    private AuthKind authKind(Endpoint ep) {
        // Login/registration are pre-authentication business goals wherever they appear. Match the
        // keywords directly instead of requiring a literal "/auth/" segment, because real projects
        // expose them as /login, /api/register, /users/signin, AuthController#login(), etc. Without
        // this, such endpoints fall through to CRUD and produce zombie goals like "View Logins".
        String p = ep.path().toLowerCase(Locale.ROOT);
        String controller = ep.controller() == null ? "" : ep.controller().toLowerCase(Locale.ROOT);
        String signal = p + " " + controller;
        if (signal.contains("register") || signal.contains("signup") || signal.contains("sign-up")) {
            return AuthKind.REGISTER;
        }
        if (signal.contains("login") || signal.contains("signin") || signal.contains("sign-in")) {
            return AuthKind.LOGIN;
        }
        return null;
    }

    // ---- domain inference ----------------------------------------------------

    String inferDomain(Endpoint ep) {
        return inferDomainGuess(ep).name();
    }

    /**
     * Domain inference with an evidence-based confidence (R3): a controller-derived name is strong
     * evidence (0.9), a path-segment fallback is weaker (0.6), and the "Resource" default is very
     * weak (0.3).
     */
    DomainGuess inferDomainGuess(Endpoint ep) {
        if (ep.controller() != null && !ep.controller().isBlank()) {
            // Strip leading role/area words ("Admin", "Auth") so "AdminProductController" yields the
            // business entity "Product", not "Admin Product". Also strip trailing technical tokens
            // ("Rest", "Api") so "ProductRestController"/"UserApiController" yield "Product"/"User"
            // rather than the implementation-flavoured "Product Rest"/"User Api".
            String fromController = stripTechWords(
                    stripLeadingRoleWords(singularizeWords(splitCamel(ep.controller()))));
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
            return new DomainGuess(capitalize(singularize(seg)), 0.6, "path");
        }
        return new DomainGuess("Resource", 0.3, "fallback");
    }

    private boolean isRoleLikeDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT);
        return lower.equals("admin") || lower.equals("auth") || lower.equals("home") || lower.equals("index");
    }

    /** A reporting/read-only domain whose admin surface should read as "View", not "Manage". */
    private boolean isReportingDomain(String domain) {
        return domain != null && REPORTING_DOMAIN.matcher(domain).matches();
    }

    /** A domain that is purely a technical artefact (REST scaffolding, raw API, integrity probe). */
    private boolean isTechDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT).trim();
        return lower.equals("rest") || lower.equals("api") || lower.isEmpty()
                || lower.equals("delete check") || lower.equals("check delete");
    }

    /** Drop trailing technical tokens (Rest/Api) from a controller-derived domain. */
    private String stripTechWords(String domain) {
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
    private String stripLeadingRoleWords(String domain) {
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

    // ---- actor inference -----------------------------------------------------

    private record ActorGuess(String name, String source, Double confidence, boolean guessed) {
    }

    ActorGuess inferActor(Endpoint ep) {
        // Strongest signal: an explicit Spring Security role mined from @PreAuthorize/@Secured/
        // @RolesAllowed. This is a real authorization fact, not a guess, so it wins over path/URL
        // heuristics and is not flagged as guessed.
        String role = ep.requiredRole();
        if (role != null && !role.isBlank()) {
            String r = role.toUpperCase(Locale.ROOT);
            if (r.contains("ADMIN")) {
                return new ActorGuess(ACTOR_ADMIN, "security:@PreAuthorize", 0.95, false);
            }
            // Any other declared role is an authenticated, non-anonymous user.
            return new ActorGuess(ACTOR_USER, "security:@PreAuthorize", 0.9, false);
        }
        String p = ep.path().toLowerCase(Locale.ROOT);
        if (p.contains("/admin")) {
            return new ActorGuess(ACTOR_ADMIN, "path:/admin", 0.9, false);
        }
        // Default: an authenticated end user. We no longer guess Admin from the HTTP method —
        // a write operation does not imply an administrator.
        return new ActorGuess(ACTOR_USER, "default-authenticated", 0.7, true);
    }

    private boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private int actorOrder(String actorName) {
        if (ACTOR_GUEST.equals(actorName)) {
            return 0;
        }
        if (ACTOR_USER.equals(actorName)) {
            return 1;
        }
        if (ACTOR_ADMIN.equals(actorName)) {
            return 2;
        }
        return 3;
    }

    // ---- string helpers ------------------------------------------------------

    private String splitCamel(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
    }

    private String singularizeWords(String words) {
        String[] parts = words.split("\\s+");
        if (parts.length == 0) {
            return words;
        }
        parts[parts.length - 1] = capitalize(singularize(parts[parts.length - 1]));
        for (int i = 0; i < parts.length - 1; i++) {
            parts[i] = capitalize(parts[i]);
        }
        return String.join(" ", parts);
    }

    private String singularize(String word) {
        String w = word;
        String lower = w.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && w.length() > 3) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
                || lower.endsWith("ches") || lower.endsWith("shes")) {
            return w.substring(0, w.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && w.length() > 1) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** Pluralize a possibly multi-word domain name while preserving word casing. */
    private String pluralName(String domain) {
        String[] parts = domain.trim().split("\\s+");
        if (parts.length == 0 || (parts.length == 1 && parts[0].isEmpty())) {
            return domain;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (i == parts.length - 1) {
                w = pluralizeWord(w);
            }
            sb.append(capitalize(w));
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private String pluralizeWord(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        String lower = word.toLowerCase(Locale.ROOT);
        String suffix;
        if (lower.endsWith("y") && lower.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            suffix = "es";
        } else {
            suffix = "s";
        }
        return word + suffix;
    }

    private boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
        if (out.isEmpty()) {
            return "X";
        }
        if (Character.isDigit(out.charAt(0))) {
            out = "X" + out;
        }
        return out;
    }

    private String uniqueId(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "_" + suffix;
        while (!used.add(candidate)) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }
}
