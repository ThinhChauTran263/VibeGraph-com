package com.vibegraph.parser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

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
        // TODO: Initialize with real implementation or mock
        // parserService = new ParserServiceImpl(...);
    }

    @Nested
    @DisplayName("parseFile")
    class ParseFile {

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should parse single Java file and extract nodes")
        void shouldParseSingleFile() throws IOException {
            // Arrange
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

            // Act
            // var result = parserService.parseFile(javaFile);

            // Assert
            // assertNotNull(result);
            // assertTrue(result.getNodes().size() > 0);
            // assertTrue(result.getNodes().stream().anyMatch(n -> n.getName().equals("UserService")));
            // assertTrue(result.getNodes().stream().anyMatch(n -> n.getName().equals("findById")));

        }

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should handle parse errors gracefully")
        void shouldHandleParseErrorsGracefully() throws IOException {
            // Arrange
            Path invalidFile = tempDir.resolve("Invalid.java");
            Files.writeString(invalidFile, """
                package com.example;
                public class Invalid {
                    // Missing closing brace - syntax error
                """);

            // Act & Assert
            // Should not throw, should return result with warnings
            // var result = parserService.parseFile(invalidFile);
            // assertThat(result).isNotNull();
            // assertThat(result.getWarnings()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseProject")
    class ParseProject {

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should parse all Java files in project directory")
        void shouldParseAllJavaFiles() throws IOException {
            // Arrange
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

            // Act
            // var result = parserService.parseProject(tempDir);

            // Assert
            // assertEquals(3, result.getFileCount());
            // assertTrue(result.getNodes().stream().anyMatch(n -> n.getName().equals("User")));
            // assertTrue(result.getNodes().stream().anyMatch(n -> n.getName().equals("UserService")));
            // assertTrue(result.getNodes().stream().anyMatch(n -> n.getName().equals("UserController")));
        }

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should skip ignored directories")
        void shouldSkipIgnoredDirectories() throws IOException {
            // Arrange
            Path srcDir = tempDir.resolve("src/main/java");
            Path targetDir = tempDir.resolve("target/classes");
            Files.createDirectories(srcDir);
            Files.createDirectories(targetDir);

            Files.writeString(srcDir.resolve("App.java"), "public class App {}");
            Files.writeString(targetDir.resolve("App.java"), "// compiled");

            // Act
            // var result = parserService.parseProject(tempDir);

            // Assert
            // assertEquals(1, result.getFileCount());
        }
    }

    @Nested
    @DisplayName("parseIncremental")
    class ParseIncremental {

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should skip file if checksum unchanged")
        void shouldSkipUnchangedFile() throws IOException {
            // Arrange
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, "public class Service {}");

            // First parse
            // var result1 = parserService.parseFile(javaFile);
            // String checksum = result1.getChecksum();

            // Second parse with same checksum
            // var result2 = parserService.parseIncremental(javaFile, checksum);

            // Assert
            // assertTrue(result2.isSkipped());
        }

        @Test
        @Disabled("Chờ ParserServiceImpl implement")
        @DisplayName("should re-parse if checksum changed")
        void shouldReparseIfChecksumChanged() throws IOException {
            // Arrange
            Path javaFile = tempDir.resolve("Service.java");
            Files.writeString(javaFile, "public class Service {}");

            // Parse with old checksum
            // var result = parserService.parseIncremental(javaFile, "old-checksum");

            // Assert
            // assertFalse(result.isSkipped());
            // assertNotNull(result.getNodes());
        }
    }
}
