package com.vibegraph.parser.visitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClassVisitor - extracts Class/Interface/Enum nodes from AST.
 *
 * Run: mvn test -Dtest=ClassVisitorTest
 */
@DisplayName("ClassVisitor")
@Disabled("Chờ ClassVisitor implement getExtractedNodes() và node extraction logic")
class ClassVisitorTest {

    private JavaParser parser;
    private ClassVisitor visitor;

    @BeforeEach
    void setUp() {
        parser = new JavaParser();
        visitor = new ClassVisitor();
    }

    private CompilationUnit parse(String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        assertTrue(result.isSuccessful(), "Parse should succeed");
        return result.getResult().orElseThrow();
    }

    @Nested
    @DisplayName("Class extraction")
    class ClassExtraction {

        @Test
        @DisplayName("should extract public class with correct properties")
        void shouldExtractPublicClass() {
            // Arrange
            String code = """
                package com.example;

                public class UserService {
                    private String name;
                }
                """;
            CompilationUnit cu = parse(code);

            // Act
            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            // Assert
            assertFalse(nodes.isEmpty(), "Should extract at least 1 node");
            var classNode = nodes.get(0);
            assertEquals("UserService", classNode.getName());
            assertEquals("com.example.UserService", classNode.getFullName());
            assertEquals("public", classNode.getVisibility());
            assertFalse(classNode.isAbstract());
            assertFalse(classNode.isFinal());
        }

        @Test
        @DisplayName("should detect abstract class")
        void shouldDetectAbstractClass() {
            String code = """
                package com.example;
                public abstract class BaseEntity {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals(1, nodes.size());
            assertTrue(nodes.get(0).isAbstract());
        }

        @Test
        @DisplayName("should detect final class")
        void shouldDetectFinalClass() {
            String code = """
                package com.example;
                public final class Constants {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals(1, nodes.size());
            assertTrue(nodes.get(0).isFinal());
        }

        @Test
        @DisplayName("should handle multiple classes in one file")
        void shouldHandleMultipleClasses() {
            String code = """
                package com.example;
                public class Outer {
                    static class Inner {}
                }
                class PackagePrivate {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertTrue(nodes.size() >= 2, "Should extract multiple classes");
        }
    }

    @Nested
    @DisplayName("Interface extraction")
    class InterfaceExtraction {

        @Test
        @DisplayName("should extract interface")
        void shouldExtractInterface() {
            String code = """
                package com.example;
                public interface UserRepository {
                    void save(Object entity);
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty());
            assertEquals("UserRepository", nodes.get(0).getName());
            assertEquals("INTERFACE", nodes.get(0).getType());
        }
    }

    @Nested
    @DisplayName("Enum extraction")
    class EnumExtraction {

        @Test
        @DisplayName("should extract enum")
        void shouldExtractEnum() {
            String code = """
                package com.example;
                public enum Status {
                    ACTIVE, INACTIVE, DELETED
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty());
            assertEquals("Status", nodes.get(0).getName());
            assertEquals("ENUM", nodes.get(0).getType());
        }
    }

    @Nested
    @DisplayName("Spring annotation detection")
    class SpringAnnotations {

        @Test
        @DisplayName("should detect @RestController layer")
        void shouldDetectRestControllerLayer() {
            String code = """
                package com.example;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class UserController {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals("CONTROLLER", nodes.get(0).getSpringLayer());
        }

        @Test
        @DisplayName("should detect @Service layer")
        void shouldDetectServiceLayer() {
            String code = """
                package com.example;
                import org.springframework.stereotype.Service;

                @Service
                public class UserService {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals("SERVICE", nodes.get(0).getSpringLayer());
        }

        @Test
        @DisplayName("should detect @Repository layer")
        void shouldDetectRepositoryLayer() {
            String code = """
                package com.example;
                import org.springframework.stereotype.Repository;

                @Repository
                public class UserRepositoryImpl {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals("REPOSITORY", nodes.get(0).getSpringLayer());
        }

        @Test
        @DisplayName("should return NONE for unannotated class")
        void shouldReturnNoneForUnannotatedClass() {
            String code = """
                package com.example;
                public class PlainClass {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            var nodes = visitor.getExtractedNodes();

            assertEquals("NONE", nodes.get(0).getSpringLayer());
        }
    }
}
