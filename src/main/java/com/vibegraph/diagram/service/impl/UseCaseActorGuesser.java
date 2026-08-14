package com.vibegraph.diagram.service.impl;

import java.util.Locale;
import java.util.Map;

import com.vibegraph.diagram.service.impl.UseCaseEndpointRules.Endpoint;

/**
 * B-M2 split (step 3): actor inference extracted verbatim from {@link UseCaseInferenceEngine}.
 * Decides which business actor owns an endpoint (security role first, then path heuristics), and
 * detects the pre-authentication register/login goals.
 */
final class UseCaseActorGuesser {

    static final String ACTOR_GUEST = "Guest";
    static final String ACTOR_USER = "User";
    static final String ACTOR_ADMIN = "Admin";

    private UseCaseActorGuesser() {
    }

    record ActorGuess(String name, String source, Double confidence, boolean guessed) {
    }

    enum AuthKind { REGISTER, LOGIN }

    static AuthKind authKind(Endpoint ep) {
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

    static ActorGuess inferActor(Endpoint ep) {
        // Strongest signal: an explicit Spring Security role mined from @PreAuthorize/@Secured/
        // @RolesAllowed. This is a real authorization fact, not a guess, so it wins over path/URL
        // heuristics and is not flagged as guessed.
        String role = ep.requiredRole();
        if (role != null && !role.isBlank()) {
            String r = role.toUpperCase(Locale.ROOT);
            if (r.contains("ADMIN") || r.contains("SUPERUSER") || r.contains("ROOT")) {
                return new ActorGuess(ACTOR_ADMIN, "security:@PreAuthorize", 0.95, false);
            }
            String bare = r.startsWith("ROLE_") ? r.substring(5) : r;
            // Generic "any authenticated user" roles collapse to the default User actor.
            if (bare.equals("USER") || bare.equals("USERS") || bare.equals("MEMBER")
                    || bare.equals("AUTHENTICATED") || bare.equals("AUTH") || bare.isBlank()) {
                return new ActorGuess(ACTOR_USER, "security:@PreAuthorize", 0.9, false);
            }
            // A named business role (SELLER, STORE_MANAGER, COURIER, …) is its own actor. This is a
            // real authorization fact, so it is not flagged as guessed. Keeping it distinct (instead
            // of collapsing to "User") preserves who-does-what fidelity from the security model.
            return new ActorGuess(roleToActorName(r), "security:@PreAuthorize:role", 0.85, false);
        }
        String p = ep.path().toLowerCase(Locale.ROOT);
        if (p.contains("/admin")) {
            return new ActorGuess(ACTOR_ADMIN, "path:/admin", 0.9, false);
        }
        // Default: an authenticated end user. We no longer guess Admin from the HTTP method —
        // a write operation does not imply an administrator.
        return new ActorGuess(ACTOR_USER, "default-authenticated", 0.7, true);
    }

    /**
     * Turn a Spring Security role token into a human actor name: strip a leading {@code ROLE_},
     * replace underscores with spaces, and Title Case each word. {@code ROLE_STORE_MANAGER} &rarr;
     * "Store Manager", {@code SELLER} &rarr; "Seller".
     */
    static String roleToActorName(String upperRole) {
        String token = upperRole.startsWith("ROLE_") ? upperRole.substring(5) : upperRole;
        token = token.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (token.isBlank()) {
            return ACTOR_USER;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : token.split("\\s+")) {
            if (w.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(UseCaseNameNormalizer.capitalize(w));
        }
        return sb.length() == 0 ? ACTOR_USER : sb.toString();
    }

    static boolean isMutating(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    static int actorOrder(String actorName) {
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

    /** Weakest actor confidence in a domain bucket; 0.5 when none is recorded. */
    static double minActorConfidence(Map<String, ActorGuess> actorMeta) {
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
}
