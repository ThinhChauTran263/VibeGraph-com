package com.vibegraph.parser.visitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FieldVisitor - extracts Field nodes from AST.
 *
 * Run: mvn test -Dtest=FieldVisitorTest
 */
@DisplayName("FieldVisitor")
@Disabled("Chờ FieldVisitor implement getExtractedFields() và field extraction logic")
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
        var fields = visitor.getExtractedFields();

        assertEquals(3, fields.size());

        var nameField = fields.stream().filter(f -> f.getName().equals("name")).findFirst().orElseThrow();
        assertEquals("String", nameField.getDeclaredType());
        assertEquals("private", nameField.getVisibility());

        var ageField = fields.stream().filter(f -> f.getName().equals("age")).findFirst().orElseThrow();
        assertEquals("int", ageField.getDeclaredType());
        assertEquals("protected", ageField.getVisibility());
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
        var fields = visitor.getExtractedFields();

        var appName = fields.stream().filter(f -> f.getName().equals("APP_NAME")).findFirst().orElseThrow();
        assertTrue(appName.isStatic());
        assertTrue(appName.isFinal());

        var counter = fields.stream().filter(f -> f.getName().equals("counter")).findFirst().orElseThrow();
        assertTrue(counter.isStatic());
        assertFalse(counter.isFinal());

        var logger = fields.stream().filter(f -> f.getName().equals("logger")).findFirst().orElseThrow();
        assertFalse(logger.isStatic());
        assertTrue(logger.isFinal());
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
        var fields = visitor.getExtractedFields();

        var repo = fields.stream().filter(f -> f.getName().equals("userRepository")).findFirst().orElseThrow();
        assertTrue(repo.isInjected());

        var plain = fields.stream().filter(f -> f.getName().equals("plainField")).findFirst().orElseThrow();
        assertFalse(plain.isInjected());
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
        var fields = visitor.getExtractedFields();

        assertTrue(fields.get(0).isInjected());
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
        var fields = visitor.getExtractedFields();

        var items = fields.stream().filter(f -> f.getName().equals("items")).findFirst().orElseThrow();
        assertTrue(items.getDeclaredType().contains("List"));

        var props = fields.stream().filter(f -> f.getName().equals("properties")).findFirst().orElseThrow();
        assertTrue(props.getDeclaredType().contains("Map"));
    }
}
