package com.vibegraph.infrastructure.service.collector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.ContainerMetrics;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Reads bounded container resource statistics from a pinned cAdvisor sidecar.
 * The backend never receives a Docker socket; if cAdvisor is unavailable, the caller gets an
 * empty result and the UI can truthfully render an unavailable state.
 */
@Component
public final class CAdvisorContainerCollector {

    // cAdvisor keeps a bounded history per container; Docker 29 stacks can exceed 2 MB.
    private static final long MAX_RESPONSE_BYTES = 16_000_000L;

    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final long maxResponseBytes;
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final Map<String, PreviousSample> previousSamples = new ConcurrentHashMap<>();
    private final int hostProcessors;

    @Autowired
    public CAdvisorContainerCollector(InfrastructureMonitorProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getCAdvisorConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    CAdvisorContainerCollector(InfrastructureMonitorProperties properties, ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.endpoint = URI.create(normalizeUrl(properties.getCAdvisorUrl())
                + "/api/v1.3/subcontainers?recursive=true");
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.maxResponseBytes = Math.min(MAX_RESPONSE_BYTES,
                Math.max(16_384L, properties.getCAdvisorMaxResponseBytes()));
        this.hostProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Collects the latest cAdvisor sample without overlapping scheduler requests. */
    public List<ContainerMetrics> collect() {
        if (!requestInFlight.compareAndSet(false, true)) return List.of();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(1_500))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > maxResponseBytes) return List.of();
            return parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            return List.of();
        } finally {
            requestInFlight.set(false);
        }
    }

    List<ContainerMetrics> parse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.isArray() ? root : root.path("containers");
            if (!entries.isArray()) return List.of();
            List<ContainerMetrics> result = new ArrayList<>();
            for (JsonNode entry : entries) {
                ContainerMetrics metric = parseEntry(entry);
                if (metric != null) result.add(metric);
            }
            return List.copyOf(result);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ContainerMetrics parseEntry(JsonNode entry) {
        String rawName = text(entry, "name");
        String name = serviceName(entry, rawName);
        if (name == null || "/".equals(name)) return null;
        JsonNode stats = entry.path("stats");
        JsonNode latest = stats.isArray() && stats.size() > 0 ? stats.get(stats.size() - 1) : stats;
        if (!latest.isObject()) return null;
        long memory = firstNonNegative(latest.path("memory").path("working_set"),
                latest.path("memory").path("usage"));
        long cpuNanos = nonNegative(latest.path("cpu").path("usage").path("total"));
        Instant timestamp = parseInstant(text(latest, "timestamp"));
        Long createdAt = parseEpochSeconds(text(entry.path("spec"), "creation_time"));
        double cpuPercent = cpuPercent(name, cpuNanos, timestamp);
        return new ContainerMetrics(name, "RUNNING (health unavailable)", false, false, "UNAVAILABLE", memory, null, cpuPercent,
                null, createdAt == null || timestamp == null ? null : Math.max(0, timestamp.getEpochSecond() - createdAt),
                "cAdvisor");
    }

    private double cpuPercent(String name, long cpuNanos, Instant timestamp) {
        if (timestamp == null) return 0d;
        PreviousSample previous = previousSamples.put(name, new PreviousSample(cpuNanos, timestamp));
        if (previous == null || cpuNanos < previous.cpuNanos() || !timestamp.isAfter(previous.timestamp())) return 0d;
        double elapsedNanos = Duration.between(previous.timestamp(), timestamp).toNanos();
        if (elapsedNanos <= 0d) return 0d;
        double percent = (cpuNanos - previous.cpuNanos()) / elapsedNanos * 100d / hostProcessors;
        return Double.isFinite(percent) ? Math.min(100d, Math.max(0d, percent)) : 0d;
    }

    private record PreviousSample(long cpuNanos, Instant timestamp) {
    }

    private String serviceName(JsonNode entry, String rawName) {
        JsonNode aliases = entry.path("aliases");
        if (aliases.isArray()) {
            for (JsonNode alias : aliases) {
                String value = alias.asText("").trim();
                if (!value.isBlank() && !value.startsWith("/docker/")) {
                    return value.replaceFirst("^/", "");
                }
            }
        }
        if (rawName == null || rawName.isBlank()) return null;
        int slash = rawName.lastIndexOf('/');
        return slash >= 0 && slash + 1 < rawName.length() ? rawName.substring(slash + 1) : rawName;
    }

    private static String normalizeUrl(String value) {
        String normalized = value == null || value.isBlank() ? "http://cadvisor:8080" : value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String text(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static long firstNonNegative(JsonNode first, JsonNode second) {
        long value = nonNegative(first);
        return value > 0 ? value : nonNegative(second);
    }

    private static long nonNegative(JsonNode value) {
        if (value == null || !value.isNumber()) return 0;
        return Math.max(0, value.asLong());
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Long parseEpochSeconds(String value) {
        try {
            return value == null ? null : Instant.parse(value).getEpochSecond();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
