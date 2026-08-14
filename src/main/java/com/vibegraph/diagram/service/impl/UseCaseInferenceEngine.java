package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;
import com.vibegraph.diagram.service.impl.UseCaseActorGuesser.ActorGuess;
import com.vibegraph.diagram.service.impl.UseCaseActorGuesser.AuthKind;
import com.vibegraph.diagram.service.impl.UseCaseClassFallback.ClassFallback;
import com.vibegraph.diagram.service.impl.UseCaseDomainGuesser.DomainAgg;
import com.vibegraph.diagram.service.impl.UseCaseDomainGuesser.DomainGuess;
import com.vibegraph.diagram.service.impl.UseCaseEndpointRules.Endpoint;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;

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
 * <p>B-M2 split: this class is now the orchestrator only. The heuristics live in package-private
 * collaborators, behaviour extracted verbatim: {@link UseCaseEndpointRules} (route collection +
 * exclusion), {@link UseCaseDomainGuesser} (domain names + confidence), {@link UseCaseActorGuesser}
 * (actors + auth detection), {@link UseCaseClassFallback} (no-HTTP class-layer fallback), and
 * {@link UseCaseNameNormalizer} (pure string helpers).
 */
@Component
public class UseCaseInferenceEngine {

    private static final String INJECTS_EDGE = "INJECTS";

    private static final String ACTOR_USER = "User";
    private static final String ACTOR_ADMIN = "Admin";
    private static final String ACTOR_GUEST = "Guest";

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
     * Build the canonical business use case model for the given graph.
     *
     * @param graph the project graph (may be null/empty)
     * @param mode  retained for API compatibility but ignored — the model is mode-independent
     */
    public InferenceResult infer(GraphDataResponse graph, String mode) {
        List<Endpoint> endpoints = UseCaseEndpointRules.collectEndpoints(graph);

        Map<String, Actor> actorsByName = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();
        boolean roleGuessed = false;

        // Per-domain accumulation for business (non-auth) endpoints, deterministic by domain.
        Map<String, DomainAgg> domains = new TreeMap<>();
        boolean hasRegister = false;
        boolean hasLogin = false;

        for (Endpoint ep : endpoints) {
            AuthKind auth = UseCaseActorGuesser.authKind(ep);
            if (auth == AuthKind.REGISTER) {
                hasRegister = true;
                continue;
            }
            if (auth == AuthKind.LOGIN) {
                hasLogin = true;
                continue;
            }

            DomainGuess domainGuess = UseCaseDomainGuesser.inferDomainGuess(ep);
            String domain = domainGuess.name();
            ActorGuess actor = UseCaseActorGuesser.inferActor(ep);
            roleGuessed |= actor.guessed();

            // Separate the administrative surface of a domain from its public surface. The same
            // entity is often exposed twice — e.g. GET /api/brands (a shopper viewing brands) and
            // /api/admin/brands (an admin managing them). Collapsing both into one goal forces a
            // single actor and loses the Admin assignment, so bucket admin endpoints on their own.
            boolean adminScoped = ACTOR_ADMIN.equals(actor.name());
            String domainKey = (adminScoped ? "admin:" : "") + domain;

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
            if (UseCaseActorGuesser.isMutating(ep.httpMethod())) {
                agg.mutatingCount++;
            }
        }

        // FALLBACK (no HTTP layer): many real projects have business behaviour but expose NO REST/MVC
        // endpoints — service-only libraries, batch/CLI apps, domain modules, or codebases whose web
        // layer the parser could not capture. Reporting an empty diagram for such a project is wrong:
        // the capabilities live in the service/controller classes and their public methods. When no
        // endpoint produced a domain, derive goals from the class layer so a use case diagram is still
        // generated. Actor roles cannot be proven without the HTTP/security layer, so they are guessed.
        boolean usedClassFallback = false;
        if (domains.isEmpty() && !hasRegister && !hasLogin) {
            ClassFallback fb = UseCaseClassFallback.inferDomainsFromClasses(graph, domains);
            hasRegister |= fb.hasRegister();
            hasLogin |= fb.hasLogin();
            roleGuessed |= fb.roleGuessed();
            usedClassFallback = fb.produced();
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
            String plural = UseCaseNameNormalizer.pluralName(domain);
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
            if (UseCaseDomainGuesser.isReportingDomain(domain) && !hasWriteEvidence) {
                manage = false;
            } else {
                manage = agg.adminScoped || hasWriteEvidence;
            }
            String verb = manage ? "Manage " : "View ";
            String name = verb + plural;
            String idBase = "UC_" + (agg.adminScoped ? "Admin" : "") + verb.trim()
                    + UseCaseNameNormalizer.pascal(plural);
            // Confidence (R3): weakest-link of the domain-name evidence and the weakest actor evidence,
            // so a goal is never reported as more certain than the weakest fact it was built from.
            double domainConf = agg.domainConfidence > 0 ? agg.domainConfidence : 0.3;
            double ucConfidence = Math.min(domainConf, UseCaseActorGuesser.minActorConfidence(agg.actorMeta));
            UseCaseElement uc = UseCaseElement.builder()
                    .id(UseCaseNameNormalizer.uniqueId(idBase, usedIds))
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
        // an /api/orders/admin/all and an /api/orders/... bucket read as "Manage Orders"). Two ovals with
        // identical text is wrong in UML, so collapse them onto one node, rewiring every relation and
        // merging their injected services. The lowest-sorted id wins for determinism.
        UseCaseModelMerge.mergeDuplicateNamedUseCases(useCases, relations, useCaseServices);

        // After merging genuine same-scope duplicates, two SURVIVING goals can still share a display
        // name across DIFFERENT scopes (a customer's "Manage Orders" vs an admin's "Manage Orders").
        // Two identically-labelled ovals is confusing UML, so append a deterministic, fact-based scope
        // qualifier. The scope is a fact (admin-scoped id), not an LLM guess — so this stays testable.
        UseCaseModelMerge.disambiguateScopedDuplicates(useCases);

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
                .comparingInt((Actor actor) -> UseCaseActorGuesser.actorOrder(actor.getName()))
                .thenComparing(Actor::getName));

        List<String> warnings = new ArrayList<>();
        if (usedClassFallback) {
            warnings.add("No HTTP endpoints (REST/MVC) were detected, so use cases were derived from "
                    + "the service/controller class layer and their public methods. These goals reflect "
                    + "implemented capabilities but are coarser than endpoint-derived ones; verify them.");
        }
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

    private Actor registerActor(Map<String, Actor> byName, Set<String> usedIds, String name,
            String source, double confidence) {
        return byName.computeIfAbsent(name, n -> Actor.builder()
                .id(UseCaseNameNormalizer.uniqueId("A_" + UseCaseNameNormalizer.pascal(n), usedIds))
                .name(n)
                .source(source)
                .confidence(confidence)
                .build());
    }

    private UseCaseElement businessUseCase(Set<String> usedIds, String name, String domain,
            String source, double confidence) {
        return UseCaseElement.builder()
                .id(UseCaseNameNormalizer.uniqueId("UC_" + UseCaseNameNormalizer.pascal(name), usedIds))
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

    // ---- thin delegators kept on the engine for the helper-test reflection surface ----

    String inferDomain(Endpoint ep) {
        return UseCaseDomainGuesser.inferDomain(ep);
    }

    DomainGuess inferDomainGuess(Endpoint ep) {
        return UseCaseDomainGuesser.inferDomainGuess(ep);
    }

    ActorGuess inferActor(Endpoint ep) {
        return UseCaseActorGuesser.inferActor(ep);
    }

    boolean isExcluded(String path) {
        return UseCaseEndpointRules.isExcluded(path);
    }
}
