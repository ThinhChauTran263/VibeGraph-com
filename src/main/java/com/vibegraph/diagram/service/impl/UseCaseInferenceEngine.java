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

    private static final String ACTOR_GUEST = "Guest";
    private static final String ACTOR_USER = "User";
    private static final String ACTOR_ADMIN = "Admin";

    // UML 2.5.1 relationship kinds (render-agnostic; see UmlUseCaseRenderer).
    private static final String REL_ASSOCIATION = "association";
    private static final String REL_GENERALIZATION = "generalization";

    private static final String LEVEL_BUSINESS = "business";

    private static final Pattern CONTROLLER_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)Controller");
    private static final Pattern PATH_VAR = Pattern.compile("\\{[^}]*}");
    private static final Pattern STATIC_ASSET = Pattern.compile(".*\\.(css|js|mjs|map|png|jpe?g|gif|svg|ico|woff2?|ttf|eot|html?|txt|webp|avif)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Result of inference: deterministic, render-agnostic graph of the use case diagram.
     */
    public record InferenceResult(
            List<Actor> actors,
            List<UseCaseElement> useCases,
            List<Relation> relations,
            List<String> warnings) {
    }

    /**
     * One inferred endpoint (a single HTTP operation) before it is folded into a domain goal.
     */
    private record Endpoint(
            String routeId,
            String httpMethod,
            String path,
            String controller) {
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

            String domain = inferDomain(ep);
            ActorGuess actor = inferActor(ep);
            roleGuessed |= actor.guessed();

            DomainAgg agg = domains.computeIfAbsent(domain, k -> new DomainAgg());
            agg.actors.add(actor.name());
            agg.actorMeta.putIfAbsent(actor.name(), actor);
            if (isMutating(ep.httpMethod())) {
                agg.hasWrite = true;
            }
        }

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

        // One goal-level use case per business domain.
        for (Map.Entry<String, DomainAgg> entry : domains.entrySet()) {
            String domain = entry.getKey();
            DomainAgg agg = entry.getValue();
            String plural = pluralName(domain);
            String name = (agg.hasWrite ? "Manage " : "View ") + plural;
            String idBase = "UC_" + (agg.hasWrite ? "Manage" : "View") + pascal(plural);
            UseCaseElement uc = UseCaseElement.builder()
                    .id(uniqueId(idBase, usedIds))
                    .name(name)
                    .domain(domain)
                    .level(LEVEL_BUSINESS)
                    .source("domain:" + domain)
                    .sourceEndpoint(null)
                    .confidence(0.8)
                    .build();
            useCases.add(uc);

            for (String actorName : agg.actors) {
                ActorGuess meta = agg.actorMeta.get(actorName);
                Actor a = registerActor(actorsByName, usedIds, actorName, meta.source(), meta.confidence());
                relations.add(association(a, uc));
            }
        }

        // UML actor generalization, building the full hierarchy Guest <|-- User <|-- Admin.
        // A Registered User is a specialized Guest, and an Administrator is a specialized User; each
        // inherits the goals of its parent actor.
        Actor adminActor = actorsByName.get(ACTOR_ADMIN);
        Actor userActor = actorsByName.get(ACTOR_USER);
        Actor guestActor = actorsByName.get(ACTOR_GUEST);
        if (userActor != null && guestActor != null) {
            relations.add(generalization(userActor, guestActor));
        }
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

        return new InferenceResult(actors, useCases, relations, warnings);
    }

    // ---- aggregation helpers -------------------------------------------------

    /** Mutable per-domain accumulator. */
    private static final class DomainAgg {
        final Set<String> actors = new TreeSet<>();
        final Map<String, ActorGuess> actorMeta = new LinkedHashMap<>();
        boolean hasWrite;
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
        for (EdgeDto edge : edges) {
            if (edge == null || !HANDLES_ROUTE_EDGE.equals(edge.getType())) {
                continue;
            }
            if (edge.getTarget() == null || edge.getTarget().isBlank()) {
                continue;
            }
            controllerByRoute.putIfAbsent(edge.getTarget(), controllerName(edge.getSource()));
        }

        // Deterministic by route id.
        Map<String, Endpoint> byRoute = new TreeMap<>();
        for (NodeDto node : nodes) {
            if (node == null || node.getId() == null || !isRouteNode(node)) {
                continue;
            }
            Endpoint ep = toEndpoint(node.getId(), node, controllerByRoute.get(node.getId()));
            if (ep != null && !isExcluded(ep.path())) {
                byRoute.putIfAbsent(ep.routeId(), ep);
            }
        }
        // Routes referenced only by an edge (no node present).
        for (String routeId : controllerByRoute.keySet()) {
            if (byRoute.containsKey(routeId)) {
                continue;
            }
            Endpoint ep = toEndpoint(routeId, null, controllerByRoute.get(routeId));
            if (ep != null && !isExcluded(ep.path())) {
                byRoute.putIfAbsent(routeId, ep);
            }
        }
        return new ArrayList<>(byRoute.values());
    }

    private Endpoint toEndpoint(String routeId, NodeDto node, String controller) {
        String method = null;
        String path = null;
        if (node != null && node.getProperties() != null) {
            Object m = node.getProperties().get("httpMethod");
            Object p = node.getProperties().get("routePath");
            method = m != null ? String.valueOf(m) : null;
            path = p != null ? String.valueOf(p) : null;
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
        return new Endpoint(routeId, method.toUpperCase(Locale.ROOT), path, controller);
    }

    private String controllerName(String fqcn) {
        if (fqcn == null) {
            return null;
        }
        Matcher m = CONTROLLER_NAME.matcher(fqcn);
        return m.find() ? m.group(1) : null;
    }

    private boolean isRouteNode(NodeDto node) {
        return ROUTE_NODE_TYPE.equals(node.getType()) || API_ENDPOINT_NODE_TYPE.equals(node.getType());
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
        // /api/auth: keep only the business goals (register/login); everything else under /auth is
        // technical plumbing (token, refresh, logout, "current user" probes) and is excluded.
        if (p.contains("/auth/") || p.endsWith("/auth")) {
            boolean business = p.contains("register") || p.contains("signup") || p.contains("sign-up")
                    || p.contains("login") || p.contains("signin") || p.contains("sign-in");
            return !business;
        }
        return false;
    }

    // ---- auth classification -------------------------------------------------

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
        if (ep.controller() != null && !ep.controller().isBlank()) {
            // Strip leading role/area words ("Admin", "Auth") so "AdminProductController" yields the
            // business entity "Product", not "Admin Product".
            String fromController = stripLeadingRoleWords(singularizeWords(splitCamel(ep.controller())));
            if (!fromController.isBlank() && !isRoleLikeDomain(fromController)) {
                return fromController;
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
            return capitalize(singularize(seg));
        }
        return "Resource";
    }

    private boolean isRoleLikeDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT);
        return lower.equals("admin") || lower.equals("auth") || lower.equals("home") || lower.equals("index");
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
