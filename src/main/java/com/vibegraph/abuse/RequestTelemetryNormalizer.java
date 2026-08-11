package com.vibegraph.abuse;

import java.util.regex.Pattern;

import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Normalizes request telemetry fields before they are queued.
 *
 * <p>Two problems are handled here. First, cardinality and privacy: a raw request URI carries
 * identifiers, e-mail addresses, tokens and filesystem-like data that must never reach the
 * telemetry tables, and unbounded distinct routes make the aggregates useless. Second, column
 * width: every value is truncated to the width of its column so one malformed request cannot
 * fail an entire batch insert.
 *
 * <p>Column widths mirror {@code request_events} / {@code security_events} in both the primary
 * schema and the Supabase schema.
 */
public final class RequestTelemetryNormalizer {

    /** {@code request_events.route}. */
    public static final int MAX_ROUTE_LENGTH = 240;
    /** {@code request_events.http_method}. */
    public static final int MAX_METHOD_LENGTH = 10;
    /** {@code request_events.ip_address}. */
    public static final int MAX_IP_LENGTH = 120;
    /** {@code request_events.event_type}. */
    public static final int MAX_EVENT_TYPE_LENGTH = 40;
    /** {@code request_events.api_key_ref}. */
    public static final int MAX_API_KEY_REF_LENGTH = 120;

    private static final int MAX_SEGMENTS = 12;
    private static final int MAX_SEGMENT_LENGTH = 40;
    private static final String TRUNCATION_MARKER = "/...";

    private static final Pattern UUID_SEGMENT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("\\d+");
    private static final Pattern EMAIL_SEGMENT = Pattern.compile("[^@/\\s]+@[^@/\\s]+\\.[^@/\\s]+");
    /** Long opaque values: hex digests, or 16+ character mixed alphanumeric secrets. */
    private static final Pattern TOKEN_SEGMENT =
            Pattern.compile("[0-9a-fA-F]{16,}|(?=[A-Za-z0-9_.~+-]*\\d)[A-Za-z0-9_.~+-]{16,}");
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]");

    private RequestTelemetryNormalizer() {
    }

    /**
     * Resolves the route to record for a request.
     *
     * <p>Prefers the handler mapping's best matching pattern, which is already a template such as
     * {@code /api/projects/{id}}. Requests without a matched handler — rejected, unmapped or
     * static ones — fall back to a sanitized request URI.
     */
    public static String resolveRoute(HttpServletRequest request) {
        if (request != null) {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern instanceof String text && !text.isBlank()) {
                return normalizeRoute(text);
            }
        }
        return normalizeRoute(request == null ? null : request.getRequestURI());
    }

    /**
     * Sanitizes an arbitrary path into a bounded, low-cardinality route with identifiers masked.
     * A query string is never part of the result.
     */
    public static String normalizeRoute(String rawRoute) {
        if (rawRoute == null || rawRoute.isBlank()) {
            return "/";
        }
        String path = stripQuery(rawRoute);
        String[] segments = path.split("/");
        StringBuilder normalized = new StringBuilder();
        int kept = 0;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (kept == MAX_SEGMENTS) {
                normalized.append(TRUNCATION_MARKER);
                break;
            }
            normalized.append('/').append(maskSegment(segment));
            kept++;
        }
        String route = normalized.isEmpty() ? "/" : normalized.toString();
        return truncate(route, MAX_ROUTE_LENGTH);
    }

    public static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "UNKNOWN";
        }
        return truncate(sanitize(method).toUpperCase(java.util.Locale.ROOT), MAX_METHOD_LENGTH);
    }

    public static String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "unknown";
        }
        return truncate(sanitize(ipAddress), MAX_IP_LENGTH);
    }

    public static String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "REQUEST";
        }
        return truncate(sanitize(eventType), MAX_EVENT_TYPE_LENGTH);
    }

    public static String normalizeApiKeyRef(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return null;
        }
        return truncate(sanitize(apiKeyRef), MAX_API_KEY_REF_LENGTH);
    }

    private static String stripQuery(String rawRoute) {
        int queryStart = rawRoute.indexOf('?');
        String path = queryStart < 0 ? rawRoute : rawRoute.substring(0, queryStart);
        int fragmentStart = path.indexOf('#');
        return fragmentStart < 0 ? path : path.substring(0, fragmentStart);
    }

    private static String maskSegment(String rawSegment) {
        String segment = sanitize(rawSegment);
        if (segment.startsWith("{") && segment.endsWith("}")) {
            return truncate(segment, MAX_SEGMENT_LENGTH);
        }
        if (UUID_SEGMENT.matcher(segment).matches() || NUMERIC_SEGMENT.matcher(segment).matches()) {
            return "{id}";
        }
        if (EMAIL_SEGMENT.matcher(segment).matches()) {
            return "{email}";
        }
        if (looksLikeFilesystemPath(segment)) {
            return "{path}";
        }
        if (TOKEN_SEGMENT.matcher(segment).matches()) {
            return "{token}";
        }
        return truncate(segment, MAX_SEGMENT_LENGTH);
    }

    private static boolean looksLikeFilesystemPath(String segment) {
        String lower = segment.toLowerCase(java.util.Locale.ROOT);
        return segment.indexOf('\\') >= 0
                || lower.contains("%5c")
                || segment.startsWith("~")
                || DRIVE_PREFIX.matcher(segment).matches();
    }

    private static String sanitize(String value) {
        return CONTROL_CHARACTERS.matcher(value).replaceAll("").trim();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
