package com.vibegraph.parser.visitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MethodVisitor - extracts Method nodes from AST.
 *
 * Run: mvn test -Dtest=MethodVisitorTest
 */
@DisplayName("MethodVisitor")
@Disabled("Chờ MethodVisitor implement getExtractedMethods() và method extraction logic")
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
            var methods = visitor.getExtractedMethods();

            assertFalse(methods.isEmpty());
            var method = methods.get(0);
            assertEquals("findByName", method.getName());
            assertEquals("String", method.getReturnType());
            assertEquals(2, method.getParameters().size());
            assertEquals("public", method.getVisibility());
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
            var methods = visitor.getExtractedMethods();

            assertEquals("void", methods.get(0).getReturnType());
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
            var methods = visitor.getExtractedMethods();

            assertTrue(methods.get(0).isStatic());
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
            var methods = visitor.getExtractedMethods();

            assertTrue(methods.get(0).isAbstract());
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
            var methods = visitor.getExtractedMethods();

            var throwsTypes = methods.get(0).getThrowsTypes();
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
            var methods = visitor.getExtractedMethods();

            var method = methods.get(0);
            assertEquals("GET", method.getHttpMethod());
            assertEquals("/users/{id}", method.getRoutePath());
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
            var methods = visitor.getExtractedMethods();

            assertEquals("POST", methods.get(0).getHttpMethod());
            assertEquals("/users", methods.get(0).getRoutePath());
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
            var methods = visitor.getExtractedMethods();

            assertFalse(methods.isEmpty());
            assertNotNull(methods.get(0).getRoutePath());
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
            var methods = visitor.getExtractedMethods();

            assertNull(methods.get(0).getHttpMethod());
            assertNull(methods.get(0).getRoutePath());
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
            var methods = visitor.getExtractedMethods();

            assertTrue(methods.stream().anyMatch(m -> m.getName().equals("<init>") || m.getName().equals("Service")));
        }
    }
}
