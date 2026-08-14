package com.vibegraph.diagram.service.impl;

import java.util.Locale;
import java.util.Set;

/**
 * B-M2 split (step 3): pure string/identifier helpers extracted verbatim from
 * {@link UseCaseInferenceEngine}. Stateless — every method is a pure function of its arguments,
 * so behaviour is identical before and after the split (guarded by
 * {@code UseCaseInferenceEngineHelperTest} and the accuracy eval harness).
 */
final class UseCaseNameNormalizer {

    private UseCaseNameNormalizer() {
    }

    static String splitCamel(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
    }

    static String singularizeWords(String words) {
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

    static String singularize(String word) {
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
    static String pluralName(String domain) {
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

    static String pluralizeWord(String word) {
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

    static boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String pascal(String raw) {
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

    static String uniqueId(String base, Set<String> used) {
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
