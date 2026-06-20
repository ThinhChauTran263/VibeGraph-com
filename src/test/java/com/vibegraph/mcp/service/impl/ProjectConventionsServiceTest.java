package com.vibegraph.mcp.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.vibegraph.mcp.dto.response.ProjectConventionsResponse;

@DisplayName("ProjectConventionsService")
class ProjectConventionsServiceTest {

    @TempDir
    Path tempDir;

    private final ProjectConventionsServiceImpl service = new ProjectConventionsServiceImpl();

    @Test
    @DisplayName("parses curated sections from the memory file")
    void parsesSections() throws IOException {
        Path memory = tempDir.resolve("ai-memory.md");
        Files.writeString(memory, """
                # VibeGraph AI Memory

                ## Current Limitations

                - STEP_IN_FLOW is inferred, not literal runtime tracing.
                - Deep CPG (READS/WRITES/CATCHES) is opt-in and default off.
                - CREATE/MODIFY/DELETE realtime incremental re-parse is wired.

                ## Testing Commands

                - Full backend: ./mvnw -q -DskipITs test
                """, StandardCharsets.UTF_8);
        ReflectionTestUtils.setField(service, "memoryPath", memory.toString());

        ProjectConventionsResponse result = service.getConventions();

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getSections()).extracting(ProjectConventionsResponse.Section::getTitle)
                .contains("Current Limitations", "Testing Commands");
        ProjectConventionsResponse.Section limitations = result.getSections().stream()
                .filter(s -> s.getTitle().equals("Current Limitations")).findFirst().orElseThrow();
        assertThat(limitations.getItems()).anyMatch(i -> i.contains("STEP_IN_FLOW is inferred"));
        assertThat(limitations.getItems()).anyMatch(i -> i.toLowerCase().contains("deep cpg"));
        assertThat(limitations.getItems()).anyMatch(i -> i.contains("CREATE/MODIFY"));
        assertThat(result.toString()).doesNotContain("password");
    }

    @Test
    @DisplayName("returns a structured warning when the memory file is missing")
    void missingFileWarns() {
        ReflectionTestUtils.setField(service, "memoryPath", tempDir.resolve("nope.md").toString());

        ProjectConventionsResponse result = service.getConventions();

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getSections()).isEmpty();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("not found"));
    }

    @Test
    @DisplayName("the committed repo memory file parses and exposes key facts")
    void committedMemoryFileParses() {
        // Default path resolves against the module working directory (repo root in tests).
        ReflectionTestUtils.setField(service, "memoryPath", "VibeGraph-specs-2month/ai-memory.md");
        ProjectConventionsResponse result = service.getConventions();

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getSections()).extracting(ProjectConventionsResponse.Section::getTitle)
                .contains("Current Limitations", "Testing Commands", "MCP Tool Limitations");
    }
}
