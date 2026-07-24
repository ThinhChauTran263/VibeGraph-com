package com.vibegraph.parser.visitor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Tests for AnnotationVisitor — Annotation nodes + ANNOTATED_BY edges.
 *
 * Canonical direction: (annotated element) -[:ANNOTATED_BY]-> (:Annotation).
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
    @DisplayName("class annotation produces an Annotation node and ANNOTATED_BY edge (element -> annotation)")
    void classAnnotation() {
        CompilationUnit cu = parse("""
            package com.example;
            import org.springframework.stereotype.Service;
            @Service
            public class UserService {}
            """);

        visitor.visit(cu, null);
        List<NodeData> nodes = visitor.getExtractedNodes();
        List<EdgeData> edges = visitor.getExtractedEdges();

        assertThat(nodes)
                .anyMatch(n -> n.type().equals("Annotation")
                        && n.fullName().equals("org.springframework.stereotype.Service"));
        assertThat(edges)
                .anyMatch(e -> e.type().equals("ANNOTATED_BY")
                        && e.sourceFullName().equals("com.example.UserService")
                        && e.targetFullName().equals("org.springframework.stereotype.Service"));
    }

    @Test
    @DisplayName("method and field annotations are linked to the correct element full names")
    void methodAndFieldAnnotations() {
        CompilationUnit cu = parse("""
            package com.example;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.web.bind.annotation.GetMapping;
            public class UserController {
                @Autowired
                private UserService service;

                @GetMapping("/users")
                public String list() { return null; }
            }
            """);

        visitor.visit(cu, null);
        List<EdgeData> edges = visitor.getExtractedEdges();

        // Field-level annotation: element full name is owner + "." + field name.
        assertThat(edges).anyMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.UserController.service")
                && e.targetFullName().equals("org.springframework.beans.factory.annotation.Autowired"));

        // Method-level annotation: element full name is the method signature.
        assertThat(edges).anyMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.UserController.list()")
                && e.targetFullName().equals("org.springframework.web.bind.annotation.GetMapping"));
    }

    @Test
    @DisplayName("JPA @Entity annotation on a class is captured")
    void jpaEntityAnnotation() {
        CompilationUnit cu = parse("""
            package com.example;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Table;
            @Entity
            @Table(name = "users")
            public class User {}
            """);

        visitor.visit(cu, null);
        List<EdgeData> edges = visitor.getExtractedEdges();

        assertThat(edges).anyMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.User")
                && e.targetFullName().equals("jakarta.persistence.Entity"));
        assertThat(edges).anyMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.targetFullName().equals("jakarta.persistence.Table"));
    }

    @Test
    @DisplayName("routine methods and no-op constructors do not emit ANNOTATED_BY edges")
    void skippedRoutineMembersDoNotEmitAnnotationEdges() {
        CompilationUnit cu = parse("""
            package com.example;
            import java.lang.Deprecated;
            public class User {
                @Deprecated
                public User() {}

                @Deprecated
                public String getName() { return name; }

                @Deprecated
                public void run() {}

                private String name;
            }
            """);

        visitor.visit(cu, null);
        List<EdgeData> edges = visitor.getExtractedEdges();

        assertThat(edges).noneMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.User.<init>()"));
        assertThat(edges).noneMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.User.getName()"));
        assertThat(edges).anyMatch(e -> e.type().equals("ANNOTATED_BY")
                && e.sourceFullName().equals("com.example.User.run()"));
    }

    @Test
    @DisplayName("java.lang built-in annotations resolve to java.lang.* and dedupe by type")
    void javaLangAnnotationsResolveAndDedupe() {
        CompilationUnit cu = parse("""
            package com.example;
            public class Base {
                @Deprecated
                public void a() {}
                @Deprecated
                public void b() {}
            }
            """);

        visitor.visit(cu, null);
        List<NodeData> nodes = visitor.getExtractedNodes();

        // One Annotation node per distinct type, even when used twice.
        assertThat(nodes.stream()
                .filter(n -> n.fullName().equals("java.lang.Deprecated"))
                .count()).isEqualTo(1);
    }
}
