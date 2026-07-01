package com.vibegraph.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for Gemini key/model rotation, bound from {@code vibegraph.ai.gemini.*}.
 *
 * <p>{@code apiKeys} binds from a YAML sequence OR a comma-separated env var (relaxed binding), so
 * {@code GEMINI_API_KEYS=k1,k2,k3} works. {@code models} is the ordered failover chain (model1 first).
 *
 * @param apiKeys   ordered API keys; rotation moves to the next key when a key is exhausted (429)
 * @param models    ordered model chain tried on each key before the key is abandoned
 * @param timeoutMs per-attempt timeout (ms) before rotating to the next model
 */
@ConfigurationProperties(prefix = "vibegraph.ai.gemini")
public record GeminiRotationProperties(
        List<String> apiKeys,
        List<String> models,
        Long timeoutMs) {

    /** Distinct, non-blank, trimmed API keys in declared order. */
    public List<String> sanitizedKeys() {
        return sanitizeDistinct(apiKeys);
    }

    /** Non-blank, trimmed model names in declared (priority) order. */
    public List<String> sanitizedModels() {
        List<String> out = new ArrayList<>();
        if (models != null) {
            for (String m : models) {
                if (m != null && !m.isBlank()) {
                    out.add(m.trim());
                }
            }
        }
        return out;
    }

    public long timeoutMsOrDefault() {
        return timeoutMs != null && timeoutMs > 0 ? timeoutMs : 12000L;
    }

    private static List<String> sanitizeDistinct(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) {
            for (String s : in) {
                if (s != null && !s.isBlank()) {
                    String t = s.trim();
                    if (!out.contains(t)) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }
}
