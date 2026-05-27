package com.vibegraph.parser.visitor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ImportVisitor - extracts IMPORTS edges.
 *
 * Run: mvn test -Dtest=ImportVisitorTest
 */
@DisplayName("ImportVisitor")
@Disabled("Chờ ImportVisitor implement")
class ImportVisitorTest {

    @Test
    @DisplayName("should extract import statement as edge")
    void shouldExtractImportStatement() {
        // String code = "import com.example.UserService;";
        // Parse and visit
        // assertThat(visitor.getImports()).hasSize(1);
        // assertThat(visitor.getImports().get(0)).isEqualTo("com.example.UserService");
    }

    @Test
    @DisplayName("should skip java.lang imports")
    void shouldSkipJavaLangImports() {
        // import java.lang.String should be skipped (implicit)
    }

    @Test
    @DisplayName("should handle wildcard imports")
    void shouldHandleWildcardImports() {
        // import com.example.* → mark as wildcard, resolve later
    }

    @Test
    @DisplayName("should handle static imports")
    void shouldHandleStaticImports() {
        // import static org.junit.jupiter.api.Assertions.*;
    }
}
