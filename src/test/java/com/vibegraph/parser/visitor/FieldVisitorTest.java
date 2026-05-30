package com.vibegraph.parser.visitor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.NodeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FieldVisitor - extracts Field nodes from AST.
 *
 * Run: mvn test -Dtest=FieldVisitorTest
 */
@DisplayName("FieldVisitor")
class FieldVisitorTest {

    private JavaParser parser;
    private FieldVisitor visitor;

    @BeforeEach
    void setUp() {
        parser = new JavaParser();
        visitor = new FieldVisitor();
    }

    private CompilationUnit parse(String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        assertTrue(result.isSuccessful());
        return result.getResult().orElseThrow();
    }

    private Object property(NodeData node, String key) {
        return node.properties().get(key);
    }

    @Test
    @DisplayName("should extract field with type and visibility")
    void shouldExtractFieldWithTypeAndVisibility() {
        String code = """
            package com.example;
            public class User {
                private String name;
                protected int age;
                public boolean active;
            }
            """;
        CompilationUnit cu = parse(code);

        visitor.visit(cu, null);
        List<NodeData> fields = visitor.getExtractedFields();

        assertEquals(3, fields.size());

        NodeData nameField = fields.stream().filter(field -> field.name().equals("name")).findFirst().orElseThrow();
        assertEquals("String", property(nameField, "declaredType"));
        assertEquals("private", property(nameField, "visibility"));

        NodeData ageField = fields.stream().filter(field -> field.name().equals("age")).findFirst().orElseThrow();
        assertEquals("int", property(ageField, "declaredType"));
        assertEquals("protected", property(ageField, "visibility"));
    }

    @Test
    @DisplayName("should detect static and final modifiers")
    void shouldDetectStaticAndFinalModifiers() {
        String code = """
            package com.example;
            public class Constants {
                public static final String APP_NAME = "VibeGraph";
                private static int counter;
                private final Logger logger;
            }
            """;
        CompilationUnit cu = parse(code);

        visitor.visit(cu, null);
        List<NodeData> fields = visitor.getExtractedFields();

        NodeData appName = fields.stream().filter(field -> field.name().equals("APP_NAME")).findFirst().orElseThrow();
        assertTrue((boolean) property(appName, "static"));
        assertTrue((boolean) property(appName, "final"));

        NodeData counter = fields.stream().filter(field -> field.name().equals("counter")).findFirst().orElseThrow();
        assertTrue((boolean) property(counter, "static"));
        assertFalse((boolean) property(counter, "final"));

        NodeData logger = fields.stream().filter(field -> field.name().equals("logger")).findFirst().orElseThrow();
        assertFalse((boolean) property(logger, "static"));
        assertTrue((boolean) property(logger, "final"));
    }

    @Test
    @DisplayName("should detect @Autowired injection")
    void shouldDetectAutowiredInjection() {
        String code = """
            package com.example;
            import org.springframework.beans.factory.annotation.Autowired;

            public class UserService {
                @Autowired
                private UserRepository userRepository;

                private String plainField;
            }
            """;
        CompilationUnit cu = parse(code);

        visitor.visit(cu, null);
        List<NodeData> fields = visitor.getExtractedFields();

        NodeData repo = fields.stream().filter(field -> field.name().equals("userRepository")).findFirst().orElseThrow();
        assertTrue((boolean) property(repo, "injected"));

        NodeData plain = fields.stream().filter(field -> field.name().equals("plainField")).findFirst().orElseThrow();
        assertFalse((boolean) property(plain, "injected"));
    }

    @Test
    @DisplayName("should detect @Inject annotation")
    void shouldDetectInjectAnnotation() {
        String code = """
            package com.example;
            import jakarta.inject.Inject;

            public class Service {
                @Inject
                private Dependency dep;
            }
            """;
        CompilationUnit cu = parse(code);

        visitor.visit(cu, null);
        List<NodeData> fields = visitor.getExtractedFields();

        assertTrue((boolean) property(fields.get(0), "injected"));
    }

    @Test
    @DisplayName("should handle generic types")
    void shouldHandleGenericTypes() {
        String code = """
            package com.example;
            import java.util.List;
            import java.util.Map;

            public class Container {
                private List<String> items;
                private Map<String, Object> properties;
            }
            """;
        CompilationUnit cu = parse(code);

        visitor.visit(cu, null);
        List<NodeData> fields = visitor.getExtractedFields();

        NodeData items = fields.stream().filter(field -> field.name().equals("items")).findFirst().orElseThrow();
        assertTrue(((String) property(items, "declaredType")).contains("List"));

        NodeData props = fields.stream().filter(field -> field.name().equals("properties")).findFirst().orElseThrow();
        assertTrue(((String) property(props, "declaredType")).contains("Map"));
    }
}
