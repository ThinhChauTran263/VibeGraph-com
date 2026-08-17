package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.vibegraph.diagram.service.impl.UseCaseActorGuesser.ActorGuess;
import com.vibegraph.diagram.service.impl.UseCaseDomainGuesser.DomainAgg;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;

/**
 * B-M2 split (step 3): the class-layer fallback extracted verbatim from
 * {@link UseCaseInferenceEngine}. Derives business domains from service/controller/entity classes
 * when a project exposes no HTTP endpoints, filling the same {@link DomainAgg} structure the
 * endpoint path uses.
 */
final class UseCaseClassFallback {

    private static final String HAS_METHOD_EDGE = "HAS_METHOD";
    private static final String CLASS_NODE_TYPE = "Class";
    private static final String DBMODEL_NODE_TYPE = "DBModel";
    private static final String INTERFACE_NODE_TYPE = "Interface";
    private static final String METHOD_NODE_TYPE = "Method";

    // Business-service class-name suffixes. Spring @Service (springLayer=SERVICE) is the primary
    // signal; these catch plain Java/CLI/library projects that carry no Spring annotation at all.
    private static final Pattern SERVICE_NAME = Pattern.compile(
            ".*(Service|ServiceImpl|Manager|Facade|UseCase|Usecase|Interactor)$");
    private static final Pattern CONTROLLER_SUFFIX = Pattern.compile(".*(Controller|Resource|Endpoint|Handler)$");
    // Infrastructure/plumbing classes that are never a business goal on their own.
    private static final Pattern INFRA_CLASS = Pattern.compile(
            ".*(Config|Configuration|Properties|Filter|Interceptor|Aspect|Listener|Scheduler|Initializer"
            + "|Util|Utils|Helper|Mapper|Converter|Validator|Exception|Repository|Dao|Factory|Builder"
            + "|Adapter|Provider|Resolver|Serializer|Deserializer|Codec|Client|Test|Tests|Application"
            + "|Bootstrap|Runner|Job|Event|Dto|Bean|Constants)$",
            Pattern.CASE_INSENSITIVE);

    // Method name prefixes that indicate a state mutation (write) rather than a pure read.
    private static final Set<String> MUTATING_PREFIXES = Set.of(
            "create", "add", "insert", "register", "signup", "save", "store", "persist", "update",
            "edit", "modify", "change", "set", "delete", "remove", "drop", "destroy", "cancel",
            "deactivate", "activate", "enable", "disable", "process", "submit", "place", "checkout",
            "pay", "charge", "refund", "send", "publish", "approve", "reject", "assign", "unassign",
            "upload", "import", "export", "generate", "reset", "revoke", "grant", "apply", "confirm",
            "complete", "close", "open", "toggle", "increment", "decrement", "move", "copy", "clone",
            "merge", "split", "schedule", "reschedule", "book", "order", "login", "logout", "authenticate",
            // Additional common business mutation verbs (were previously misread as reads, e.g. shipOrder).
            "ship", "dispatch", "deliver", "fulfill", "restock", "adjust", "transfer", "allocate",
            "escalate", "resolve", "archive", "restore", "verify", "lock", "unlock", "ban", "unban",
            "suspend", "promote", "demote", "issue", "renew", "void", "settle", "capture", "release");
    // Pure accessor / plumbing methods that carry no business goal on their own.
    private static final Set<String> NOISE_METHODS = Set.of(
            "equals", "hashcode", "tostring", "builder", "valueof", "values", "ordinal", "name",
            "compareto", "main", "init", "destroy", "afterpropertiesset", "run", "call", "accept",
            "apply", "get", "set", "is", "has", "of", "clone", "wait", "notify", "notifyall");

    private UseCaseClassFallback() {
    }

    /** Outcome of the class-layer fallback. */
    record ClassFallback(boolean produced, boolean hasRegister, boolean hasLogin,
            boolean roleGuessed) {
    }

    /**
     * Derive business domains from the class layer when no HTTP endpoint produced one. Tiered by the
     * strongest available signal: service classes first (a service method is a business operation),
     * then controllers, then domain entities (a system with a {@code Product} entity almost certainly
     * lets someone manage products). Mutates the supplied {@code domains} map in place using the same
     * {@link DomainAgg} structure the endpoint path fills, so all downstream goal generation, merging,
     * generalization, and confidence handling apply unchanged.
     */
    static ClassFallback inferDomainsFromClasses(GraphDataResponse graph, Map<String, DomainAgg> domains) {
        List<NodeDto> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<EdgeDto> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();
        if (nodes.isEmpty()) {
            return new ClassFallback(false, false, false, false);
        }

        // Index public business method names per owner class FQCN (via HAS_METHOD edges).
        Map<String, NodeDto> methodByKey = new HashMap<>();
        for (NodeDto n : nodes) {
            if (n != null && METHOD_NODE_TYPE.equals(n.getType())) {
                if (n.getId() != null) {
                    methodByKey.put(n.getId(), n);
                }
                if (n.getFullName() != null) {
                    methodByKey.putIfAbsent(n.getFullName(), n);
                }
            }
        }
        Map<String, List<String>> methodsByOwner = new HashMap<>();
        for (EdgeDto e : edges) {
            if (e == null || !HAS_METHOD_EDGE.equals(e.getType()) || e.getSource() == null) {
                continue;
            }
            NodeDto m = methodByKey.get(e.getTarget());
            if (m == null || !isPublicMethod(m) || m.getName() == null) {
                continue;
            }
            String mn = m.getName();
            if (mn.isBlank() || NOISE_METHODS.contains(mn.toLowerCase(Locale.ROOT))) {
                continue;
            }
            methodsByOwner.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(mn);
        }

        // Candidate business classes, deterministic by FQCN.
        Map<String, NodeDto> classByFqcn = new TreeMap<>();
        for (NodeDto n : nodes) {
            if (n == null || n.getType() == null) {
                continue;
            }
            if (CLASS_NODE_TYPE.equals(n.getType()) || DBMODEL_NODE_TYPE.equals(n.getType())
                    || INTERFACE_NODE_TYPE.equals(n.getType())) {
                String key = n.getFullName() != null ? n.getFullName() : n.getId();
                if (key != null) {
                    classByFqcn.putIfAbsent(key, n);
                }
            }
        }

        // Tier the candidates: service > controller > entity > generic.
        List<NodeDto> services = new ArrayList<>();
        List<NodeDto> controllers = new ArrayList<>();
        List<NodeDto> entities = new ArrayList<>();
        List<NodeDto> generic = new ArrayList<>();
        for (NodeDto n : classByFqcn.values()) {
            String layer = layerOf(n);
            String simple = n.getName() != null ? n.getName() : "";
            boolean serviceLike = "SERVICE".equals(layer) || SERVICE_NAME.matcher(simple).matches();
            boolean controllerLike = "CONTROLLER".equals(layer) || CONTROLLER_SUFFIX.matcher(simple).matches();
            boolean entityLike = "ENTITY".equals(layer) || DBMODEL_NODE_TYPE.equals(n.getType());
            if (serviceLike && !isInfraName(simple)) {
                services.add(n);
            } else if (controllerLike && !isInfraName(simple)) {
                controllers.add(n);
            } else if (entityLike) {
                entities.add(n);
            } else if (!isInfraName(simple)) {
                // Last resort: a plain business class (CLI/util-free app, hand-rolled domain object).
                generic.add(n);
            }
        }
        String tierKind;
        List<NodeDto> tier;
        if (!services.isEmpty()) {
            tier = services;
            tierKind = "service";
        } else if (!controllers.isEmpty()) {
            tier = controllers;
            tierKind = "controller";
        } else if (!entities.isEmpty()) {
            tier = entities;
            tierKind = "entity";
        } else {
            tier = generic;
            tierKind = "generic";
        }
        if (tier.isEmpty()) {
            return new ClassFallback(false, false, false, false);
        }

        double confidence = switch (tierKind) {
            case "service" -> 0.75;
            case "controller" -> 0.7;
            case "entity" -> 0.5;
            default -> 0.4;
        };

        boolean hasRegister = false;
        boolean hasLogin = false;
        boolean produced = false;

        for (NodeDto cls : tier) {
            String simple = cls.getName() != null ? cls.getName() : "";
            String fqcn = cls.getFullName() != null ? cls.getFullName() : cls.getId();
            List<String> methods = methodsByOwner.getOrDefault(fqcn, List.of());

            // Service/controller goals legitimately include read operations (getX is a "View" signal).
            // For entities and plain classes, accessor getters/setters are noise, not operations, so
            // strip them: an entity with only getId/getName must not read as "View" — it is a managed
            // business object. A generic class with NO real business method left is skipped entirely.
            List<String> business = ("service".equals(tierKind) || "controller".equals(tierKind))
                    ? methods
                    : methods.stream().filter(m -> !isAccessorName(m)).toList();
            if ("generic".equals(tierKind) && business.isEmpty()) {
                continue;
            }

            // Auth detection from method names (e.g. AuthService.register / UserService.login).
            boolean clsRegister = business.stream().anyMatch(UseCaseClassFallback::isRegisterMethod);
            boolean clsLogin = business.stream().anyMatch(UseCaseClassFallback::isLoginMethod);
            hasRegister |= clsRegister;
            hasLogin |= clsLogin;

            String domain = classDomainName(simple, tierKind);
            // A class whose entire identity is authentication contributes only the Guest goals.
            if (isAuthLikeDomain(domain) && (clsRegister || clsLogin || business.isEmpty())) {
                continue;
            }
            if (domain.isBlank() || UseCaseDomainGuesser.isTechDomain(domain)
                    || UseCaseDomainGuesser.isRoleLikeDomain(domain)) {
                continue;
            }

            boolean adminScoped = simple.toLowerCase(Locale.ROOT).startsWith("admin")
                    || domain.toLowerCase(Locale.ROOT).startsWith("admin ");
            String cleanDomain = UseCaseDomainGuesser.stripLeadingRoleWords(domain);
            if (cleanDomain.isBlank() || UseCaseDomainGuesser.isRoleLikeDomain(cleanDomain)) {
                continue;
            }
            String actorName = adminScoped ? UseCaseActorGuesser.ACTOR_ADMIN : UseCaseActorGuesser.ACTOR_USER;

            // Mutating evidence from non-accessor method names. With no business method captured
            // (e.g. a bare entity), assume the system manages the thing (manage=true) at low
            // confidence — an entity in the domain model is something the system manages.
            int methodCount = business.size();
            int mutating = (int) business.stream().filter(UseCaseClassFallback::isMutatingMethodName).count();
            if (methodCount == 0) {
                methodCount = 1;
                mutating = 1;
            }

            String domainKey = (adminScoped ? "admin:" : "") + cleanDomain;
            DomainAgg agg = domains.computeIfAbsent(domainKey, k -> new DomainAgg());
            agg.domainLabel = cleanDomain;
            agg.adminScoped = adminScoped;
            agg.actors.add(actorName);
            agg.actorMeta.putIfAbsent(actorName,
                    new ActorGuess(actorName, "fallback:" + tierKind + "-layer", confidence, true));
            agg.endpointCount += methodCount;
            agg.mutatingCount += mutating;
            agg.domainConfidence = Math.max(agg.domainConfidence, confidence);
            if (fqcn != null && !fqcn.isBlank()) {
                agg.controllerFqcns.add(fqcn);
            }
            produced = true;
        }

        produced |= hasRegister || hasLogin;
        return new ClassFallback(produced, hasRegister, hasLogin, produced);
    }

    /** A method node is treated as public when its visibility is public or unspecified. */
    private static boolean isPublicMethod(NodeDto method) {
        if (method.getProperties() == null) {
            return true;
        }
        Object v = method.getProperties().get("visibility");
        if (v == null) {
            return true;
        }
        String vis = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return vis.isEmpty() || vis.equals("public");
    }

    private static String layerOf(NodeDto node) {
        if (node.getProperties() == null) {
            return "NONE";
        }
        Object l = node.getProperties().get("springLayer");
        return l != null ? String.valueOf(l) : "NONE";
    }

    private static boolean isInfraName(String simpleName) {
        return simpleName != null && INFRA_CLASS.matcher(simpleName).matches();
    }

    private static boolean isMutatingMethodName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String prefix : MUTATING_PREFIXES) {
            if (n.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegisterMethod(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("register") || n.startsWith("signup");
    }

    private static boolean isLoginMethod(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("login") || n.startsWith("signin") || n.startsWith("authenticate");
    }

    /** Accessor getter/setter (getId, setName, isActive) — a data accessor, not a business operation. */
    private static boolean isAccessorName(String name) {
        return name != null && name.matches("^(get|set)[A-Z].*|^is[A-Z].*");
    }

    private static boolean isAuthLikeDomain(String domain) {
        String d = domain.toLowerCase(Locale.ROOT).trim();
        return d.equals("auth") || d.equals("authentication") || d.equals("security")
                || d.equals("credential") || d.equals("session") || d.equals("token");
    }

    /**
     * Turn a class simple name into a business domain. Strips an {@code Impl} suffix, the tier's role
     * suffix (Service/Manager/Controller/Entity...), then splits camel case and singularizes — so
     * {@code OrderServiceImpl} &rarr; "Order", {@code ProductController} &rarr; "Product",
     * {@code ShoppingCart} (entity) &rarr; "Shopping Cart".
     */
    private static String classDomainName(String simpleName, String tierKind) {
        if (simpleName == null || simpleName.isBlank()) {
            return "";
        }
        String base = simpleName;
        if (base.endsWith("Impl")) {
            base = base.substring(0, base.length() - 4);
        }
        String[] suffixes = switch (tierKind) {
            case "service" -> new String[] {"Service", "Manager", "Facade", "UseCase", "Usecase", "Interactor"};
            case "controller" -> new String[] {"Controller", "Resource", "Endpoint", "Handler"};
            case "entity" -> new String[] {"Entity", "Model"};
            default -> new String[] {};
        };
        for (String suffix : suffixes) {
            if (base.length() > suffix.length() && base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }
        if (base.isBlank()) {
            return "";
        }
        return UseCaseDomainGuesser.stripLeadingRoleWords(
                UseCaseNameNormalizer.singularizeWords(UseCaseNameNormalizer.splitCamel(base)));
    }
}
