package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * B-M2 split (step 3): endpoint collection + route exclusion extracted verbatim from
 * {@link UseCaseInferenceEngine}. Turns graph nodes/edges into the {@link Endpoint} list the
 * engine folds into domain goals, and owns the exclusion heuristics for non-business routes.
 */
final class UseCaseEndpointRules {

    static final String ROUTE_NODE_TYPE = "Route";
    static final String API_ENDPOINT_NODE_TYPE = "APIEndpoint";
    static final String HANDLES_ROUTE_EDGE = "HANDLES_ROUTE";

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

    private UseCaseEndpointRules() {
    }

    /**
     * One inferred endpoint (a single HTTP operation) before it is folded into a domain goal.
     */
    record Endpoint(
            String routeId,
            String httpMethod,
            String path,
            String controller,
            String controllerFqcn,
            String requiredRole) {
    }

    static List<Endpoint> collectEndpoints(GraphDataResponse graph) {
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

    private static Endpoint toEndpoint(String routeId, NodeDto node, String controller, String controllerFqcn) {
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

    static String controllerName(String fqcn) {
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
    static String classFqcnFromMethod(String methodFqcn) {
        if (methodFqcn == null || methodFqcn.isBlank()) {
            return null;
        }
        int lastDot = methodFqcn.lastIndexOf('.');
        if (lastDot <= 0) {
            return methodFqcn;
        }
        return methodFqcn.substring(0, lastDot);
    }

    private static boolean isRouteNode(NodeDto node) {
        return ROUTE_NODE_TYPE.equals(node.getType()) || API_ENDPOINT_NODE_TYPE.equals(node.getType());
    }

    /**
     * True when a node is a server-side view (page) route AND is a read/navigation request. Such a
     * route is pure presentation (the parser marks it with {@code view=true} for a plain
     * {@code @Controller} handler that returns a template/redirect). A mutating view route (form POST)
     * performs a real business action and is NOT treated as a page.
     */
    private static boolean isViewPageRoute(NodeDto node) {
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
        return !UseCaseActorGuesser.isMutating(method);
    }

    /** Path that denotes a pre-authentication business goal (login/registration). */
    private static boolean isAuthBusinessPath(String p) {
        return p.contains("register") || p.contains("signup") || p.contains("sign-up")
                || p.contains("login") || p.contains("signin") || p.contains("sign-in");
    }

    // ---- exclusion -----------------------------------------------------------

    static boolean isExcluded(String path) {
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
    private static boolean isUiScreenRoute(String lowerPath) {
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
}
