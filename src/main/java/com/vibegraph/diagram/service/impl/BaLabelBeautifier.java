package com.vibegraph.diagram.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Actor;
import com.vibegraph.diagram.dto.response.UmlUseCaseResponse.UseCaseElement;

/**
 * Display-layer "Business Analyst" beautifier.
 *
 * <p>Runs after semantic inference ({@link UseCaseInferenceEngine}) and before rendering
 * ({@link UmlUseCaseRenderer}). It rewrites only the human-readable labels so the diagram reads
 * like an SRS document authored by a BA, while leaving every stable id ({@code A_User},
 * {@code UC_ManageProducts}) and every {@link com.vibegraph.diagram.dto.response.UmlUseCaseResponse.Relation}
 * untouched. Because relations reference ids — never names — association/generalization/dedup all
 * keep working unchanged.
 *
 * <p>Three concerns:
 * <ul>
 *   <li><b>System boundary</b> — {@code "ThinhChauTran263/SPX_Tracking"} &rarr;
 *       {@code "SPX Tracking System"}; a plain {@code "Shop"} &rarr; {@code "Shop System"}.</li>
 *   <li><b>Actor display names</b> — internal role ids stay {@code Guest/User/Admin}; only the
 *       shown name is elevated ({@code User} &rarr; {@code Registered User},
 *       {@code Admin} &rarr; {@code Administrator}).</li>
 *   <li><b>Use case lexicon</b> — an extensible dictionary maps inferred goal phrases to the
 *       wording a BA would use, falling back to the original label when no mapping exists.</li>
 * </ul>
 */
@Component
public class BaLabelBeautifier {

    private static final String SYSTEM_SUFFIX = "System";

    /** Internal role name &rarr; display name. Absent keys render unchanged. */
    private static final Map<String, String> ACTOR_DISPLAY = Map.of(
            "Guest", "Guest",
            "User", "Registered User",
            "Admin", "Administrator");

    /**
     * Inferred goal phrase &rarr; BA wording. Extend here as new domains appear; any goal without an
     * entry keeps its original label. Keys are matched case-insensitively against the trimmed name.
     */
    private static final Map<String, String> USE_CASE_LEXICON = Map.ofEntries(
            Map.entry("register account", "Register Account"),
            Map.entry("log in", "Log In"),
            Map.entry("view dashboards", "View Dashboard"),
            Map.entry("manage profiles", "Manage Profile"),
            Map.entry("manage trackings", "Manage Tracking Orders"),
            Map.entry("view resources", "Manage System Resources"),
            Map.entry("view stats", "Analyze Statistics"),
            Map.entry("manage stats", "Analyze Statistics"),
            Map.entry("view statistics", "Analyze Statistics"),
            Map.entry("view analytics", "View Analytics"),
            Map.entry("view audit logs", "View Audit Logs"),
            Map.entry("manage users", "Manage User Accounts"),
            Map.entry("manage user accounts", "Manage User Accounts"),
            Map.entry("manage categories", "Manage Categories"),
            Map.entry("view categories", "View Categories"),
            Map.entry("manage orders", "Manage Orders"),
            Map.entry("manage carts", "Manage Cart"),
            Map.entry("manage payments", "Manage Payments"),
            Map.entry("manage reviews", "Manage Reviews"),
            Map.entry("manage wishlists", "Manage Wishlist"));

    /**
     * Format a raw project identifier into a presentable system boundary title.
     *
     * <p>Steps: drop any owner prefix before the last {@code /}; replace {@code _}/{@code -} with
     * spaces; collapse whitespace; proper-case each word while preserving all-caps acronyms
     * ({@code SPX}); append {@code "System"} unless the name already ends with it.
     */
    public String formatSystemName(String raw) {
        if (raw == null || raw.isBlank()) {
            return SYSTEM_SUFFIX;
        }
        String name = raw.trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
        if (name.isBlank()) {
            return SYSTEM_SUFFIX;
        }

        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(properCaseWord(word));
        }
        String formatted = sb.toString();
        if (formatted.toLowerCase(Locale.ROOT).endsWith(" " + SYSTEM_SUFFIX.toLowerCase(Locale.ROOT))
                || formatted.equalsIgnoreCase(SYSTEM_SUFFIX)) {
            return formatted;
        }
        return formatted + " " + SYSTEM_SUFFIX;
    }

    /**
     * Rebuild actors with elevated display names, preserving id/source/confidence.
     */
    public List<Actor> beautifyActors(List<Actor> actors) {
        if (actors == null) {
            return List.of();
        }
        List<Actor> out = new ArrayList<>(actors.size());
        for (Actor actor : actors) {
            if (actor == null) {
                continue;
            }
            String display = ACTOR_DISPLAY.getOrDefault(actor.getName(), actor.getName());
            out.add(Actor.builder()
                    .id(actor.getId())
                    .name(display)
                    .source(actor.getSource())
                    .confidence(actor.getConfidence())
                    .build());
        }
        return out;
    }

    /**
     * Rebuild use cases with BA-wording names, preserving id/domain/level/source/sourceEndpoint/confidence.
     */
    public List<UseCaseElement> beautifyUseCases(List<UseCaseElement> useCases) {
        if (useCases == null) {
            return List.of();
        }
        List<UseCaseElement> out = new ArrayList<>(useCases.size());
        for (UseCaseElement uc : useCases) {
            if (uc == null) {
                continue;
            }
            out.add(UseCaseElement.builder()
                    .id(uc.getId())
                    .name(beautifyUseCaseName(uc.getName()))
                    .domain(uc.getDomain())
                    .level(uc.getLevel())
                    .source(uc.getSource())
                    .sourceEndpoint(uc.getSourceEndpoint())
                    .confidence(uc.getConfidence())
                    .build());
        }
        return out;
    }

    private String beautifyUseCaseName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return USE_CASE_LEXICON.getOrDefault(key, name);
    }

    /** Proper-case a single word, leaving all-caps acronyms (e.g. {@code SPX}) intact. */
    private String properCaseWord(String word) {
        if (word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT))) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
