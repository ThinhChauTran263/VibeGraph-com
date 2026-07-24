package com.vibegraph.parser.visitor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.EdgeData;

/**
 * Tests for ImportVisitor — extracts IMPORTS edges from import statements.
 *
 * Parses real source via JavaParser and asserts the edges ImportVisitor produces.
 * Source file identity is fixed to "com.example.Foo" for the IMPORTS edge source.
 *
 * Run: mvn test -Dtest=ImportVisitorTest
 */
@DisplayName("ImportVisitor")
class ImportVisitorTest {

    private List<EdgeData> importsOf(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        ImportVisitor visitor = new ImportVisitor("com.example.Foo");
        visitor.visit(cu, null);
        return visitor.getExtractedEdges();
    }

    @Test
    @DisplayName("should create an IMPORTS edge for a normal import")
    void shouldExtractImportStatement() {
        List<EdgeData> edges = importsOf("""
                package com.example;
                import com.other.UserService;
                public class Foo {}
                """);

        assertThat(edges).anyMatch(e -> e.type().equals("IMPORTS")
                && e.sourceFullName().equals("com.example.Foo")
                && e.targetFullName().equals("com.other.UserService"));
    }

    @Test
    @DisplayName("should skip JDK and Spring framework imports")
    void shouldSkipJavaLangImports() {
        List<EdgeData> edges = importsOf("""
                package com.example;
                import java.lang.Runnable;
                import java.util.List;
                import org.springframework.stereotype.Service;
                import com.other.UserService;
                public class Foo {}
                """);

        assertThat(edges).noneMatch(e -> e.targetFullName().startsWith("java.")
                || e.targetFullName().startsWith("org.springframework."));
        // The non-java.lang import is still captured.
        assertThat(edges).anyMatch(e -> e.targetFullName().equals("com.other.UserService"));
    }

    @Test
    @DisplayName("should mark wildcard imports (target = package name)")
    void shouldHandleWildcardImports() {
        List<EdgeData> edges = importsOf("""
                package com.example;
                import com.other.*;
                public class Foo {}
                """);

        assertThat(edges).anyMatch(e -> e.type().equals("IMPORTS")
                && e.targetFullName().equals("com.other")
                && Boolean.TRUE.equals(e.properties().get("isWildcard")));
    }

    @Test
    @DisplayName("should mark static imports (target = fully-qualified member)")
    void shouldHandleStaticImports() {
        List<EdgeData> edges = importsOf("""
                package com.example;
                import static com.other.Helpers.help;
                public class Foo {}
                """);

        assertThat(edges).anyMatch(e -> e.type().equals("IMPORTS")
                && e.targetFullName().equals("com.other.Helpers.help")
                && Boolean.TRUE.equals(e.properties().get("isStatic")));
    }
}
