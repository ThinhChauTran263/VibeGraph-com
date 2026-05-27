package com.vibegraph.common.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HashUtils - SHA-256 checksum utilities.
 *
 * Run: mvn test -Dtest=HashUtilsTest
 */
@DisplayName("HashUtils")
@Disabled("Chờ HashUtils implement sha256(Path) và sha256(String)")
class HashUtilsTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("sha256 from file")
    class Sha256FromFile {

        @Test
        @DisplayName("should compute consistent hash for same content")
        void shouldComputeConsistentHash() throws IOException {
            // Arrange
            Path file = tempDir.resolve("test.java");
            Files.writeString(file, "public class Test {}");

            // Act
            String hash1 = HashUtils.sha256(file);
            String hash2 = HashUtils.sha256(file);

            // Assert
            assertEquals(hash1, hash2, "Same file should produce same hash");
            assertEquals(64, hash1.length(), "SHA-256 hex should be 64 chars");
        }

        @Test
        @DisplayName("should produce different hash for different content")
        void shouldProduceDifferentHashForDifferentContent() throws IOException {
            // Arrange
            Path file1 = tempDir.resolve("file1.java");
            Path file2 = tempDir.resolve("file2.java");
            Files.writeString(file1, "public class A {}");
            Files.writeString(file2, "public class B {}");

            // Act
            String hash1 = HashUtils.sha256(file1);
            String hash2 = HashUtils.sha256(file2);

            // Assert
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("should return lowercase hex string")
        void shouldReturnLowercaseHexString() throws IOException {
            // Arrange
            Path file = tempDir.resolve("test.java");
            Files.writeString(file, "content");

            // Act
            String hash = HashUtils.sha256(file);

            // Assert
            assertEquals(hash.toLowerCase(), hash, "Hash should be lowercase");
            assertTrue(hash.matches("[0-9a-f]+"), "Hash should be hex");
        }

        @Test
        @DisplayName("should handle empty file")
        void shouldHandleEmptyFile() throws IOException {
            // Arrange
            Path file = tempDir.resolve("empty.java");
            Files.writeString(file, "");

            // Act
            String hash = HashUtils.sha256(file);

            // Assert
            assertNotNull(hash);
            assertEquals(64, hash.length());
            // SHA-256 of empty string is known
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
        }
    }

    @Nested
    @DisplayName("sha256 from string")
    class Sha256FromString {

        @Test
        @DisplayName("should compute hash from string")
        void shouldComputeHashFromString() {
            // Act
            String hash = HashUtils.sha256("public class Test {}");

            // Assert
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("should match file hash for same content")
        void shouldMatchFileHashForSameContent() throws IOException {
            // Arrange
            String content = "public class Test { void method() {} }";
            Path file = tempDir.resolve("test.java");
            Files.writeString(file, content);

            // Act
            String stringHash = HashUtils.sha256(content);
            String fileHash = HashUtils.sha256(file);

            // Assert
            assertEquals(stringHash, fileHash, "String and file hash should match for same content");
        }

        @Test
        @DisplayName("should handle null input gracefully")
        void shouldHandleNullInput() {
            // Act & Assert
            assertThrows(NullPointerException.class, () -> HashUtils.sha256((String) null));
        }
    }
}
