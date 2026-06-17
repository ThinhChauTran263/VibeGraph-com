package com.vibegraph.mcp.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.vibegraph.graph.dto.response.ProjectResponse;
import com.vibegraph.graph.service.ProjectService;
import com.vibegraph.mcp.source.SourceFileService.SearchOutcome;
import com.vibegraph.mcp.source.SourceFileService.SourceContent;
import com.vibegraph.mcp.source.impl.SourceFileServiceImpl;

@DisplayName("SourceFileService")
class SourceFileServiceTest {

    private static final String PROJECT_ID = "proj-1";

    @TempDir
    Path tempDir;

    private final ProjectService projectService = Mockito.mock(ProjectService.class);
    private SourceFileServiceImpl service;
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = tempDir.toRealPath();
        service = new SourceFileServiceImpl(projectService);
        when(projectService.getProject(PROJECT_ID)).thenReturn(
                ProjectResponse.builder().id(PROJECT_ID).rootPath(root.toString()).build());

        write("src/main/java/demo/Hello.java", """
                package demo;

                public class Hello {
                    private final CategoryService categoryService = null;

                    @GetMapping("/api/hello")
                    public String greet() {
                        String password=topSecretValue;
                        return "hi";
                    }
                }
                """);
        write("application.properties", "app.name=demo\napi.token=abcdef123456\n");
        write(".env", "SECRET_KEY=should-never-be-served\n");
        write("target/Generated.java", "public class Generated { String marker_in_target; }\n");
    }

    private void write(String relative, String content) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent() == null ? root : target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("reads a relative source file and returns a relative path only")
    void readsRelativeFile() {
        SourceContent content = service.readRange(PROJECT_ID, "src/main/java/demo/Hello.java", null, null);

        assertThat(content.found()).isTrue();
        assertThat(content.relativePath()).isEqualTo("src/main/java/demo/Hello.java");
        assertThat(content.language()).isEqualTo("java");
        assertThat(content.content()).contains("public class Hello");
        assertThat(content.content()).doesNotContain(tempDir.toString());
    }

    @Test
    @DisplayName("reads an absolute path inside the root (as graph nodes provide)")
    void readsAbsolutePathInsideRoot() {
        String absolute = root.resolve("src/main/java/demo/Hello.java").toString();

        SourceContent content = service.readRange(PROJECT_ID, absolute, null, null);

        assertThat(content.found()).isTrue();
        assertThat(content.relativePath()).isEqualTo("src/main/java/demo/Hello.java");
        assertThat(content.content()).doesNotContain(root.toString());
    }

    @Test
    @DisplayName("returns the exact requested line range")
    void returnsExactRange() {
        SourceContent content = service.readRange(PROJECT_ID, "src/main/java/demo/Hello.java", 3, 3);

        assertThat(content.startLine()).isEqualTo(3);
        assertThat(content.endLine()).isEqualTo(3);
        assertThat(content.content().strip()).isEqualTo("public class Hello {");
    }

    @Test
    @DisplayName("truncates oversized ranges to the max line cap")
    void truncatesOversizedRange() throws IOException {
        String big = IntStream.rangeClosed(1, 400)
                .mapToObj(i -> "// line " + i)
                .collect(Collectors.joining("\n"));
        write("Big.java", big);

        SourceContent content = service.readRange(PROJECT_ID, "Big.java", 1, 400);

        assertThat(content.truncated()).isTrue();
        assertThat(content.truncationReason()).isNotBlank();
        assertThat(content.endLine() - content.startLine() + 1).isLessThanOrEqualTo(300);
    }

    @Test
    @DisplayName("rejects path traversal that escapes the root")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> service.readRange(PROJECT_ID, "../../../../.env", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    @DisplayName("refuses to serve a blocked .env file")
    void refusesBlockedEnvFile() {
        SourceContent content = service.readRange(PROJECT_ID, ".env", null, null);

        assertThat(content.found()).isFalse();
        assertThat(content.content()).isNullOrEmpty();
        assertThat(content.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("refuses to serve files under build output (target)")
    void refusesTargetDir() {
        SourceContent content = service.readRange(PROJECT_ID, "target/Generated.java", null, null);

        assertThat(content.found()).isFalse();
        assertThat(content.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("returns a safe warning for a missing file (no stacktrace)")
    void missingFileSafeWarning() {
        SourceContent content = service.readRange(PROJECT_ID, "src/main/java/demo/Missing.java", null, null);

        assertThat(content.found()).isFalse();
        assertThat(content.warnings()).anyMatch(w -> w.contains("not found"));
    }

    @Test
    @DisplayName("redacts secret-looking lines in served content")
    void redactsSecrets() {
        SourceContent content = service.readRange(PROJECT_ID, "src/main/java/demo/Hello.java", null, null);

        assertThat(content.content()).contains("[REDACTED]");
        assertThat(content.content()).doesNotContain("topSecretValue");
    }

    @Test
    @DisplayName("search finds literal text and a Spring annotation")
    void searchFindsLiteralAndAnnotation() {
        SearchOutcome annotation = service.search(PROJECT_ID, "@GetMapping", null, 50);
        assertThat(annotation.hits()).isNotEmpty();
        assertThat(annotation.hits().get(0).relativePath()).isEqualTo("src/main/java/demo/Hello.java");

        SearchOutcome service2 = service.search(PROJECT_ID, "categoryService", null, 50);
        assertThat(service2.hits()).isNotEmpty();
    }

    @Test
    @DisplayName("search ignores build output directories")
    void searchIgnoresTarget() {
        SearchOutcome outcome = service.search(PROJECT_ID, "marker_in_target", null, 50);

        assertThat(outcome.hits()).isEmpty();
        assertThat(outcome.totalMatches()).isZero();
    }

    @Test
    @DisplayName("search caps the number of returned matches")
    void searchCapsResults() throws IOException {
        String many = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "int needleToken" + i + " = needle;")
                .collect(Collectors.joining("\n"));
        write("Many.java", many);

        SearchOutcome outcome = service.search(PROJECT_ID, "needle", null, 5);

        assertThat(outcome.hits()).hasSizeLessThanOrEqualTo(5);
        assertThat(outcome.totalMatches()).isGreaterThan(outcome.hits().size());
        assertThat(outcome.truncated()).isTrue();
    }

    @Test
    @DisplayName("search redacts secrets in snippets")
    void searchRedactsSnippets() {
        SearchOutcome outcome = service.search(PROJECT_ID, "api.token", null, 50);

        assertThat(outcome.hits()).isNotEmpty();
        assertThat(outcome.hits().get(0).snippet()).contains("[REDACTED]");
        assertThat(outcome.hits().get(0).snippet()).doesNotContain("abcdef123456");
    }

    @Test
    @DisplayName("search rejects a blank query")
    void searchRejectsBlankQuery() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, "   ", null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    @DisplayName("search rejects an over-long query")
    void searchRejectsLongQuery() {
        String longQuery = "x".repeat(201);

        assertThatThrownBy(() -> service.search(PROJECT_ID, longQuery, null, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("search rejects a traversal glob")
    void searchRejectsTraversalGlob() {
        assertThatThrownBy(() -> service.search(PROJECT_ID, "Hello", "../**", 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("rejects a blank file path")
    void rejectsBlankPath() {
        assertThatThrownBy(() -> service.readRange(PROJECT_ID, "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
