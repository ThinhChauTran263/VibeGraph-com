package com.vibegraph.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a benchmark result as machine-readable JSON plus a structured console summary.
 *
 * <p>The environment block is part of the result on purpose: a capacity number without the hardware
 * and JVM that produced it cannot be compared against anything.
 */
final class BenchmarkReport {

    private static final Path OUTPUT_DIRECTORY = Paths.get("target", "benchmarks");

    private BenchmarkReport() {
    }

    static Path write(String name, Map<String, Object> measurements) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("benchmark", name);
        report.put("recordedAt", Instant.now().toString());
        report.put("environment", environment());
        report.put("measurements", measurements);

        Files.createDirectories(OUTPUT_DIRECTORY);
        Path target = OUTPUT_DIRECTORY.resolve(name + ".json");
        Files.writeString(target, toJson(report), StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("=== benchmark: " + name + " ===");
        print("environment", environment());
        print("measurements", measurements);
        System.out.println("json: " + target.toAbsolutePath());
        return target;
    }

    private static Map<String, Object> environment() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        environment.put("arch", System.getProperty("os.arch"));
        environment.put("javaVersion", System.getProperty("java.version"));
        environment.put("availableProcessors", runtime.availableProcessors());
        environment.put("maxHeapBytes", runtime.maxMemory());
        environment.put("database", "Testcontainers PostgreSQL (container-local, no network hop)");
        return environment;
    }

    private static void print(String section, Map<String, Object> values) {
        System.out.println("[" + section + "]");
        values.forEach((key, value) -> System.out.printf("  %-28s %s%n", key, value));
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder();
        appendValue(json, values, 0);
        return json.append(System.lineSeparator()).toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder json, Object value, int depth) {
        String indent = "  ".repeat(depth + 1);
        String closingIndent = "  ".repeat(depth);
        if (value instanceof Map<?, ?> map) {
            json.append("{").append(System.lineSeparator());
            int remaining = map.size();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                json.append(indent).append(quote(entry.getKey())).append(": ");
                appendValue(json, entry.getValue(), depth + 1);
                if (--remaining > 0) {
                    json.append(",");
                }
                json.append(System.lineSeparator());
            }
            json.append(closingIndent).append("}");
            return;
        }
        if (value == null) {
            json.append("null");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
            return;
        }
        json.append(quote(value.toString()));
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + '"';
    }
}
