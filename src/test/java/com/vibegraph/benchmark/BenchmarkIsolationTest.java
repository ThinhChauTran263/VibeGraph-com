package com.vibegraph.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the benchmark opt-in. Capacity benchmarks are slow and machine-dependent, so a regular
 * {@code mvn test} must never pick them up.
 */
class BenchmarkIsolationTest {

    private static final Path POM = Paths.get("pom.xml");

    @Test
    @DisplayName("benchmarks are wired to a dedicated Maven profile")
    void pom_declaresBenchmarkProfile() throws Exception {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);

        assertThat(pom).contains("<id>benchmark</id>");
        int profileStart = pom.indexOf("<id>benchmark</id>");
        int profileEnd = pom.indexOf("</profile>", profileStart);
        assertThat(profileStart).isPositive();
        assertThat(profileEnd).isGreaterThan(profileStart);

        String benchmarkProfile = pom.substring(profileStart, profileEnd);
        assertThat(benchmarkProfile).contains("**/*Benchmark.java");
    }

    @Test
    @DisplayName("the default build never includes benchmark classes")
    void pom_defaultBuildExcludesBenchmarks() throws Exception {
        String pom = Files.readString(POM, StandardCharsets.UTF_8);
        int profilesStart = pom.indexOf("<profiles>");
        assertThat(profilesStart).isPositive();

        String defaultBuild = pom.substring(0, profilesStart);
        assertThat(defaultBuild)
                .as("the default build must not reference benchmark classes")
                .doesNotContain("Benchmark");
    }

    @Test
    @DisplayName("benchmark classes are named so Surefire's default includes skip them")
    void benchmarkClasses_doNotMatchDefaultSurefireIncludes() throws Exception {
        try (var files = Files.walk(Paths.get("src", "test", "java", "com", "vibegraph", "benchmark"))) {
            var benchmarkNames = files
                    .filter(path -> path.toString().endsWith("Benchmark.java"))
                    .map(path -> path.getFileName().toString())
                    .toList();

            assertThat(benchmarkNames).isNotEmpty();
            for (String name : benchmarkNames) {
                // Surefire's defaults are Test*.java, *Test.java, *Tests.java and *TestCase.java.
                assertThat(name).doesNotStartWith("Test");
                assertThat(name).doesNotEndWith("Test.java");
                assertThat(name).doesNotEndWith("Tests.java");
                assertThat(name).doesNotEndWith("TestCase.java");
            }
        }
    }
}
