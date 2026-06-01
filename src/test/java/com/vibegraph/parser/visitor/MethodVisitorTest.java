package com.vibegraph.parser.visitor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.NodeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MethodVisitor - extracts Method nodes from AST.
 *
 * Run: mvn test -Dtest=MethodVisitorTest
 */
@DisplayName("MethodVisitor")
class MethodVisitorTest {

    private JavaParser parser;
    private MethodVisitor visitor;

    @BeforeEach
    void setUp() {
        parser = new JavaParser();
        visitor = new MethodVisitor();
    }

    private CompilationUnit parse(String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        assertTrue(result.isSuccessful());
        return result.getResult().orElseThrow();
    }

    private Object property(NodeData node, String key) {
        return node.properties().get(key);
    }

    @Nested
    @DisplayName("Basic method extraction")
    class BasicExtraction {

        @Test
        @DisplayName("should extract method with return type and parameters")
        void shouldExtractMethodWithReturnTypeAndParams() {
            String code = """
                package com.example;
                public class UserService {
                    public String findByName(String name, int limit) {
                        return null;
                    }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertFalse(methods.isEmpty());
            NodeData method = methods.get(0);
            assertEquals("Method", method.type());
            assertEquals("findByName", method.name());
            assertEquals("String", property(method, "returnType"));
            assertEquals(2, ((List<?>) property(method, "paramTypes")).size());
            assertEquals("public", property(method, "visibility"));
        }

        @Test
        @DisplayName("should extract void method")
        void shouldExtractVoidMethod() {
            String code = """
                package com.example;
                public class Service {
                    public void doSomething() {}
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertEquals("void", property(methods.get(0), "returnType"));
        }

        @Test
        @DisplayName("should detect static method")
        void shouldDetectStaticMethod() {
            String code = """
                package com.example;
                public class Utils {
                    public static int calculate(int a, int b) { return a + b; }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertTrue((boolean) property(methods.get(0), "static"));
        }

        @Test
        @DisplayName("should detect abstract method")
        void shouldDetectAbstractMethod() {
            String code = """
                package com.example;
                public abstract class Base {
                    public abstract void process();
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertTrue((boolean) property(methods.get(0), "abstract"));
        }

        @Test
        @DisplayName("should extract throws types")
        void shouldExtractThrowsTypes() {
            String code = """
                package com.example;
                public class Service {
                    public void riskyMethod() throws IOException, ParseException {}
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            List<?> throwsTypes = (List<?>) property(methods.get(0), "throwsTypes");
            assertEquals(2, throwsTypes.size());
            assertTrue(throwsTypes.contains("IOException"));
            assertTrue(throwsTypes.contains("ParseException"));
        }
    }

    @Nested
    @DisplayName("Spring endpoint detection")
    class SpringEndpoints {

        @Test
        @DisplayName("should detect @GetMapping with route path")
        void shouldDetectGetMapping() {
            String code = """
                package com.example;
                import org.springframework.web.bind.annotation.*;

                @RestController
                public class UserController {
                    @GetMapping("/users/{id}")
                    public Object getUser(@PathVariable Long id) { return null; }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            NodeData method = methods.get(0);
            assertEquals("GET", property(method, "httpMethod"));
            assertEquals("/users/{id}", property(method, "routePath"));
        }

        @Test
        @DisplayName("should detect @PostMapping")
        void shouldDetectPostMapping() {
            String code = """
                package com.example;
                import org.springframework.web.bind.annotation.*;

                @RestController
                public class UserController {
                    @PostMapping("/users")
                    public Object createUser(@RequestBody Object dto) { return null; }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertEquals("POST", property(methods.get(0), "httpMethod"));
            assertEquals("/users", property(methods.get(0), "routePath"));
        }

        @Test
        @DisplayName("should detect @RequestMapping with method attribute")
        void shouldDetectRequestMapping() {
            String code = """
                package com.example;
                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api")
                public class ApiController {
                    @RequestMapping(value = "/health", method = RequestMethod.GET)
                    public Object health() { return null; }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertFalse(methods.isEmpty());
            assertEquals("GET", property(methods.get(0), "httpMethod"));
            assertEquals("/api/health", property(methods.get(0), "routePath"));
        }

        @Test
        @DisplayName("should return null httpMethod for non-endpoint methods")
        void shouldReturnNullForNonEndpoint() {
            String code = """
                package com.example;
                public class Service {
                    public void internalMethod() {}
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertNull(property(methods.get(0), "httpMethod"));
            assertNull(property(methods.get(0), "routePath"));
        }
    }

    @Nested
    @DisplayName("Constructor extraction")
    class Constructors {

        @Test
        @DisplayName("should extract constructors")
        void shouldExtractConstructors() {
            String code = """
                package com.example;
                public class Service {
                    private final Repository repo;

                    public Service(Repository repo) {
                        this.repo = repo;
                    }
                }
                """;
            CompilationUnit cu = parse(code);

            visitor.visit(cu, null);
            List<NodeData> methods = visitor.getExtractedMethods();

            assertTrue(methods.stream().anyMatch(method -> method.name().equals("<init>")));
        }
    }
}
