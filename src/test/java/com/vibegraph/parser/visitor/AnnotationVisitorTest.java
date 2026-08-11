package com.vibegraph.parser.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Compatibility tests for the deprecated AnnotationVisitor.
 *
 * Annotation usages are stored on owning node properties instead of emitted as
 * Annotation nodes or ANNOTATED_BY edges. Project @interface declarations are
 * emitted by ClassVisitor.
 */
@DisplayName("AnnotationVisitor")
class AnnotationVisitorTest {

    private JavaParser parser;
    private AnnotationVisitor visitor;

    @BeforeEach
    void setUp() {
        parser = new JavaParser();
        visitor = new AnnotationVisitor();
    }

    private CompilationUnit parse(String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        assertTrue(result.isSuccessful());
        return result.getResult().orElseThrow();
    }

    @Test
    @DisplayName("does not emit usage annotation nodes or ANNOTATED_BY edges")
    void doesNotEmitAnnotationUsageGraph() {
        CompilationUnit cu = parse("""
            package com.example;
            import jakarta.persistence.Entity;
            import org.springframework.stereotype.Service;

            @Service
            @Entity
            public class UserService {
                @Deprecated
                public void run() {}
            }
            """);

        visitor.visit(cu, null);

        assertThat(visitor.getExtractedNodes()).isEmpty();
        assertThat(visitor.getExtractedEdges()).isEmpty();
    }

    @Test
    @DisplayName("ClassVisitor owns project annotation declaration extraction")
    void classVisitorOwnsAnnotationDeclarations() {
        CompilationUnit cu = parse("""
            package com.example;
            public @interface Auditable {}
            """);
        ClassVisitor classVisitor = new ClassVisitor();

        classVisitor.visit(cu, null);

        assertThat(classVisitor.getExtractedNodes())
                .anyMatch(node -> node.type().equals("Annotation")
                        && node.fullName().equals("com.example.Auditable"));
    }
}
