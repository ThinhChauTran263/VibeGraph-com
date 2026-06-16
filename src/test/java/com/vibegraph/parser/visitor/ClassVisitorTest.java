package com.vibegraph.parser.visitor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.NodeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ClassVisitor - extracts Class/Interface/Enum nodes from AST.
 *
 * Run: mvn test -Dtest=ClassVisitorTest
 */
@DisplayName("ClassVisitor")
class ClassVisitorTest {

    private JavaParser parser;
    private ClassVisitor visitor;

    @BeforeEach
    void setUp() {
        parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        visitor = new ClassVisitor();
    }

    private CompilationUnit parse(String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        assertTrue(result.isSuccessful(), "Parse should succeed");
        return result.getResult().orElseThrow();
    }

    private Object property(NodeData node, String key) {
        return node.properties().get(key);
    }

    @Nested
    @DisplayName("Class extraction")
    class ClassExtraction {

        @Test
        @DisplayName("should extract public class with correct properties")
        void shouldExtractPublicClass() {
            String code = """
                package com.example;

                public class UserService {
                    private String name;
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty(), "Should extract at least 1 node");
            NodeData classNode = nodes.get(0);
            assertEquals("Class", classNode.type());
            assertEquals("UserService", classNode.name());
            assertEquals("com.example.UserService", classNode.fullName());
            assertEquals("public", property(classNode, "visibility"));
            assertFalse((boolean) property(classNode, "abstract"));
            assertFalse((boolean) property(classNode, "final"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals(1, nodes.size());
            assertTrue((boolean) property(nodes.get(0), "abstract"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals(1, nodes.size());
            assertTrue((boolean) property(nodes.get(0), "final"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty());
            assertEquals("UserRepository", nodes.get(0).name());
            assertEquals("Interface", nodes.get(0).type());
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty());
            assertEquals("Status", nodes.get(0).name());
            assertEquals("Enum", nodes.get(0).type());
        }
    }

    @Nested
    @DisplayName("Record extraction")
    class RecordExtraction {

        @Test
        @DisplayName("should extract Java record")
        void shouldExtractRecord() {
            String code = """
                package com.example;
                public record UserRecord(String id, String name) {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertFalse(nodes.isEmpty());
            NodeData recordNode = nodes.get(0);
            assertEquals("Record", recordNode.type());
            assertEquals("UserRecord", recordNode.name());
            assertEquals("com.example.UserRecord", recordNode.fullName());
            assertEquals(List.of("id", "name"), property(recordNode, "components"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals("CONTROLLER", property(nodes.get(0), "springLayer"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals("SERVICE", property(nodes.get(0), "springLayer"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals("REPOSITORY", property(nodes.get(0), "springLayer"));
        }


        @Test
        @DisplayName("should type persistence model annotations as DBModel")
        void shouldTypePersistenceModelAsDbModel() {
            String code = """
                package com.example;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Table;

                @Entity
                @Table(name = "users")
                public class UserEntity {}
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals("DBModel", nodes.get(0).type());
            assertEquals("ENTITY", property(nodes.get(0), "springLayer"));
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
            List<NodeData> nodes = visitor.getExtractedNodes();

            assertEquals("NONE", property(nodes.get(0), "springLayer"));
        }
    }
}
