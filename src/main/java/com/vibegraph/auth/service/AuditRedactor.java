package com.vibegraph.auth.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Redacts secrets before audit data crosses the persistence boundary. */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class AuditRedactor {

    private static final String REDACTED = "[REDACTED]";
    private static final java.util.regex.Pattern BEARER =
            java.util.regex.Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~-]+");
    private static final java.util.regex.Pattern API_KEY =
            java.util.regex.Pattern.compile("vbg_[A-Za-z0-9]{12,}");

    private final ObjectMapper objectMapper;

    public String redact(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(redactValue(details, null));
        } catch (JsonProcessingException ex) {
            return "{\"details\":\"[REDACTED]\"}";
        }
    }

    private Object redactValue(Object value, String key) {
        if (key != null && isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> safe.put(
                    String.valueOf(entryKey), redactValue(entryValue, String.valueOf(entryKey))));
            return safe;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> redactValue(item, key)).toList();
        }
        if (value instanceof String string) {
            return API_KEY.matcher(BEARER.matcher(string).replaceAll("Bearer " + REDACTED))
                    .replaceAll(REDACTED);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("jwt")
                || normalized.contains("token")
                || normalized.contains("sourcecontent")
                || normalized.contains("privatecontent")
                || normalized.contains("apikeyvalue");
    }
}
