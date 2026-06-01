package com.vibegraph.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for FileUtils - file scanning and filtering utilities.
 *
 * Run: mvn test -Dtest=FileUtilsTest
 */
@DisplayName("FileUtils")
class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("scanJavaFiles")
    class ScanJavaFiles {

        @Test
        @DisplayName("should find all .java files recursively")
        void shouldFindAllJavaFilesRecursively() throws IOException {
            // Arrange
            Path srcDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("UserService.java"), "public class UserService {}");
            Files.writeString(srcDir.resolve("UserController.java"), "public class UserController {}");
            Files.writeString(srcDir.getParent().resolve("BaseEntity.java"), "public class BaseEntity {}");

            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(p -> p.toString().endsWith(".java"));
        }

        @Test
        @DisplayName("should ignore build directory")
        void shouldIgnoreBuildDirectory() throws IOException {
            // Arrange
            Path srcDir = tempDir.resolve("src/main/java");
            Path buildDir = tempDir.resolve("build/classes");
            Files.createDirectories(srcDir);
            Files.createDirectories(buildDir);
            Files.writeString(srcDir.resolve("App.java"), "public class App {}");
            Files.writeString(buildDir.resolve("App.java"), "// compiled");

            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).toString()).contains("src");
        }

        @Test
        @DisplayName("should ignore target directory")
        void shouldIgnoreTargetDirectory() throws IOException {
            // Arrange
            Path srcDir = tempDir.resolve("src");
            Path targetDir = tempDir.resolve("target/classes");
            Files.createDirectories(srcDir);
            Files.createDirectories(targetDir);
            Files.writeString(srcDir.resolve("Main.java"), "public class Main {}");
            Files.writeString(targetDir.resolve("Main.java"), "// compiled");

            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).toString()).doesNotContain("target");
        }

        @Test
        @DisplayName("should ignore .git directory")
        void shouldIgnoreGitDirectory() throws IOException {
            // Arrange
            Path srcDir = tempDir.resolve("src");
            Path gitDir = tempDir.resolve(".git/objects");
            Files.createDirectories(srcDir);
            Files.createDirectories(gitDir);
            Files.writeString(srcDir.resolve("App.java"), "public class App {}");
            Files.writeString(gitDir.resolve("SomeFile.java"), "// git internal");

            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list for empty directory")
        void shouldReturnEmptyListForEmptyDirectory() throws IOException {
            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should not include non-java files")
        void shouldNotIncludeNonJavaFiles() throws IOException {
            // Arrange
            Files.writeString(tempDir.resolve("readme.md"), "# Readme");
            Files.writeString(tempDir.resolve("config.xml"), "<config/>");
            Files.writeString(tempDir.resolve("App.java"), "public class App {}");

            // Act
            List<Path> result = FileUtils.scanJavaFiles(tempDir);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).toString()).endsWith(".java");
        }
    }

    @Nested
    @DisplayName("isJavaFile")
    class IsJavaFile {

        @Test
        @DisplayName("should return true for .java files")
        void shouldReturnTrueForJavaFiles() {
            assertThat(FileUtils.isJavaFile(Path.of("UserService.java"))).isTrue();
            assertThat(FileUtils.isJavaFile(Path.of("/path/to/App.java"))).isTrue();
        }

        @Test
        @DisplayName("should return false for non-java files")
        void shouldReturnFalseForNonJavaFiles() {
            assertThat(FileUtils.isJavaFile(Path.of("readme.md"))).isFalse();
            assertThat(FileUtils.isJavaFile(Path.of("config.xml"))).isFalse();
            assertThat(FileUtils.isJavaFile(Path.of("script.js"))).isFalse();
            assertThat(FileUtils.isJavaFile(Path.of("App.class"))).isFalse();
        }

        @Test
        @DisplayName("should return false for files with java in name but wrong extension")
        void shouldReturnFalseForFilesWithJavaInName() {
            assertThat(FileUtils.isJavaFile(Path.of("javascript.js"))).isFalse();
            assertThat(FileUtils.isJavaFile(Path.of("java-config.yml"))).isFalse();
        }
    }
}
