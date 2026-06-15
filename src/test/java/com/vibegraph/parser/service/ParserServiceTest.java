package com.vibegraph.parser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.impl.ParserServiceImpl;

/**
 * Tests for ParserService - main parsing orchestrator.
 *
 * Run: mvn test -Dtest=ParserServiceTest
 */
@DisplayName("ParserService")
class ParserServiceTest {

    @TempDir
    Path tempDir;

    private ParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ParserServiceImpl();
    }

    @Nested
    @DisplayName("parseFile")
    class ParseFile {

        @Test
        @DisplayName("should parse single Java file and extract class + method nodes")
        void shouldParseSingleFile() throws IOException {
            Path javaFile = tempDir.resolve("UserService.java");
            Files.writeString(javaFile, """
                package com.example;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    private final UserRepository repository;

                    public UserService(UserRepository repository) {
                        this.repository = repository;
                    }

                    public User findById(Long id) {
                        return repository.findById(id).orElse(null);
                    }
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result).isNotNull();
            assertThat(result.getNodes()).isNotEmpty();
            assertThat(result.getNodes()).anyMatch(n -> n.name().equals("UserService"));
            assertThat(result.getNodes()).anyMatch(n -> n.name().equals("findById"));
        }

        @Test
        @DisplayName("controller file should yield an APIEndpoint node inside the ParseResult")
        void shouldAggregateRouteNode() throws IOException {
            Path javaFile = tempDir.resolve("UserController.java");
            Files.writeString(javaFile, """
                package com.example;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    @GetMapping("/{id}")
                    public String findById(Long id) { return null; }
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            // Regression guard for the dropped-route bug: the APIEndpoint node must be
            // present in the aggregated nodes, not only referenced by an edge.
            assertThat(result.getNodes())
                    .as("APIEndpoint node must be aggregated into ParseResult")
                    .anyMatch(n -> n.type().equals("APIEndpoint") && n.fullName().equals("GET /api/users/{id}"));

            assertThat(result.getEdges())
                    .as("HANDLES_ROUTE edge must target the aggregated APIEndpoint node")
                    .anyMatch(e -> e.type().equals("HANDLES_ROUTE")
                            && e.targetFullName().equals("GET /api/users/{id}"));
        }

        @Test
        @DisplayName("should handle parse errors gracefully")
        void shouldHandleParseErrorsGracefully() throws IOException {
            Path invalidFile = tempDir.resolve("Invalid.java");
            Files.writeString(invalidFile, """
                package com.example;
                public class Invalid {
                    // Missing closing brace - syntax error
                """);

            ParseResult result = parserService.parseFile(invalidFile);

            assertThat(result).isNotNull();
            assertThat(result.getWarnings()).isNotEmpty();
        }

        @Test
        @DisplayName("non-.java file should return a warning and no nodes")
        void shouldRejectNonJavaFile() throws IOException {
            Path txtFile = tempDir.resolve("notes.txt");
            Files.writeString(txtFile, "not java");

            ParseResult result = parserService.parseFile(txtFile);

            assertThat(result.getNodes()).isEmpty();
            assertThat(result.getWarnings()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseProject")
    class ParseProject {

        @Test
        @DisplayName("should parse all Java files in project directory")
        void shouldParseAllJavaFiles() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("User.java"), """
                package com.example;
                public class User {
                    private Long id;
                    private String name;
                }
                """);

            Files.writeString(srcDir.resolve("UserService.java"), """
                package com.example;
                public class UserService {
                    public User findById(Long id) { return null; }
                }
                """);

            Files.writeString(srcDir.resolve("UserController.java"), """
                package com.example;
                public class UserController {
                    private UserService service;
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);

            assertThat(results).hasSize(3);
            assertThat(results).flatExtracting(ParseResult::getNodes)
                    .anyMatch(n -> n.name().equals("User"))
                    .anyMatch(n -> n.name().equals("UserService"))
                    .anyMatch(n -> n.name().equals("UserController"));
        }

        @Test
        @DisplayName("should skip ignored directories (target/)")
        void shouldSkipIgnoredDirectories() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java");
            Path targetDir = tempDir.resolve("target/classes");
            Files.createDirectories(srcDir);
            Files.createDirectories(targetDir);

            Files.writeString(srcDir.resolve("App.java"), "public class App {}");
            Files.writeString(targetDir.resolve("App.java"), "// compiled");

            List<ParseResult> results = parserService.parseProject(tempDir);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("parseProject on the sample-project fixture yields non-empty nodes and edges")
        void shouldParseSampleProjectFixture() {
            Path sampleProject = Path.of("src/test/resources/sample-project");
            assertThat(Files.isDirectory(sampleProject))
                    .as("sample-project fixture must exist at " + sampleProject.toAbsolutePath())
                    .isTrue();

            List<ParseResult> results = parserService.parseProject(sampleProject);

            assertThat(results).as("fixture .java files").isNotEmpty();
            assertThat(results).flatExtracting(ParseResult::getNodes)
                    .as("nodes must not be empty").isNotEmpty()
                    .anyMatch(n -> n.name().equals("SampleUserService"));
            assertThat(results).flatExtracting(ParseResult::getEdges)
                    .as("edges must not be empty").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseFileWithCache")
    class ParseIncremental {

        @Test
        @DisplayName("should throw UnsupportedOperationException - deferred to Sprint 2")
        void incrementalDeferred() throws IOException {
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, "public class Service {}");

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> parserService.parseFileWithCache(javaFile, "p1"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
