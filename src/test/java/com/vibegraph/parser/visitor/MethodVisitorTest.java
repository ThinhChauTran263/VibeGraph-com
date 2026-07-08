package com.vibegraph.parser.visitor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.NodeData;

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

            assertTrue(methods.stream().anyMatch(method -> method.name().equals("<init>") && method.type().equals("Constructor")));
        }
    }

    @Nested
    @DisplayName("Deep CPG: READS / WRITES / CATCHES + LocalVariable (Phase 3)")
    class DeepCpg {

        private MethodVisitor deepVisitor;

        @BeforeEach
        void setUpDeep() {
            deepVisitor = new MethodVisitor(true);
        }

        private List<com.vibegraph.parser.node.EdgeData> edges(String code) {
            deepVisitor.visit(parse(code), null);
            return deepVisitor.getExtractedEdges();
        }

        private boolean hasEdge(List<com.vibegraph.parser.node.EdgeData> edges, String type,
                                String source, java.util.function.Predicate<String> targetMatch) {
            return edges.stream().anyMatch(e -> e.type().equals(type)
                    && e.sourceFullName().equals(source) && targetMatch.test(e.targetFullName()));
        }

        @Test
        @DisplayName("initializer `int b = a + 1` READS the parameter a and WRITES local b")
        void initializerReadsAndWrites() {
            var es = edges("""
                package com.example;
                public class C {
                    public void m(int a) {
                        int b = a + 1;
                    }
                }
                """);
            String m = "com.example.C.m(int)";
            assertTrue(hasEdge(es, "READS", m, t -> t.startsWith(m + "#a@")), "READS a (parameter)");
            assertTrue(hasEdge(es, "WRITES", m, t -> t.startsWith(m + "#b@")), "WRITES b (local)");
            // Local b is materialised as a LocalVariable node.
            assertTrue(deepVisitor.getExtractedVariables().stream()
                    .anyMatch(n -> n.type().equals("LocalVariable") && n.fullName().startsWith(m + "#b@")));
        }

        @Test
        @DisplayName("local variable read links to the declared local node")
        void localVariableRead() {
            var es = edges("""
                package com.example;
                public class C {
                    public void m() {
                        int b = 1;
                        int c = b;
                    }
                }
                """);
            String m = "com.example.C.m()";
            assertTrue(hasEdge(es, "WRITES", m, t -> t.startsWith(m + "#b@")));
            assertTrue(hasEdge(es, "READS", m, t -> t.startsWith(m + "#b@")), "c = b READS b");
            assertTrue(hasEdge(es, "WRITES", m, t -> t.startsWith(m + "#c@")));
        }

        @Test
        @DisplayName("this.field write and bare-field read resolve to the same Field node")
        void fieldReadAndWrite() {
            var es = edges("""
                package com.example;
                public class C {
                    private int count;
                    public void inc() {
                        this.count = count + 1;
                    }
                }
                """);
            String m = "com.example.C.inc()";
            assertTrue(hasEdge(es, "WRITES", m, t -> t.equals("com.example.C.count")), "this.count = ... WRITES field");
            assertTrue(hasEdge(es, "READS", m, t -> t.equals("com.example.C.count")), "bare count READS field");
        }

        @Test
        @DisplayName("compound assignment writes AND reads the target")
        void compoundAssignment() {
            var es = edges("""
                package com.example;
                public class C {
                    private int total;
                    public void add(int x) {
                        total += x;
                    }
                }
                """);
            String m = "com.example.C.add(int)";
            assertTrue(hasEdge(es, "WRITES", m, t -> t.equals("com.example.C.total")));
            assertTrue(hasEdge(es, "READS", m, t -> t.equals("com.example.C.total")), "+= reads target");
            assertTrue(hasEdge(es, "READS", m, t -> t.startsWith(m + "#x@")), "RHS reads parameter x");
        }

        @Test
        @DisplayName("increment / decrement write and read the operand")
        void incrementDecrement() {
            var es = edges("""
                package com.example;
                public class C {
                    public void loop() {
                        int i = 0;
                        i++;
                        --i;
                    }
                }
                """);
            String m = "com.example.C.loop()";
            assertTrue(hasEdge(es, "WRITES", m, t -> t.startsWith(m + "#i@")));
            assertTrue(hasEdge(es, "READS", m, t -> t.startsWith(m + "#i@")));
        }

        @Test
        @DisplayName("catch and multi-catch produce one CATCHES edge per exception type")
        void catchesIncludingMultiCatch() {
            var es = edges("""
                package com.example;
                public class C {
                    public void risky() {
                        try {
                            work();
                        } catch (IllegalStateException e) {
                            try {
                                work();
                            } catch (IllegalArgumentException | NullPointerException ex) {
                            }
                        }
                    }
                    void work() {}
                }
                """);
            String m = "com.example.C.risky()";
            long catches = es.stream().filter(e -> e.type().equals("CATCHES")
                    && e.sourceFullName().equals(m)).count();
            assertEquals(3, catches, "one CATCHES per caught type (incl. nested + multi-catch)");
            assertTrue(es.stream().anyMatch(e -> e.type().equals("CATCHES")
                    && e.targetFullName().endsWith("IllegalArgumentException")));
            assertTrue(es.stream().anyMatch(e -> e.type().equals("CATCHES")
                    && e.targetFullName().endsWith("NullPointerException")));
        }

        @Test
        @DisplayName("negative: type references and unresolved names do not become READS")
        void negativeNoSpuriousReads() {
            var es = edges("""
                package com.example;
                public class C {
                    public void m(int x) {
                        String s = String.valueOf(x);
                        System.out.println(s);
                    }
                }
                """);
            String m = "com.example.C.m(int)";
            // x (parameter) and s (local) are real reads; String / System are type refs.
            assertTrue(hasEdge(es, "READS", m, t -> t.startsWith(m + "#x@")));
            assertTrue(es.stream().noneMatch(e -> e.type().equals("READS")
                    && (e.targetFullName().endsWith(".String") || e.targetFullName().endsWith(".System"))),
                    "type references must not be READS");
        }

        @Test
        @DisplayName("deep CPG OFF (default) emits no LocalVariable nodes and no READS/WRITES/CATCHES")
        void defaultOffEmitsNothingExtra() {
            MethodVisitor plain = new MethodVisitor(); // deepCpg = false
            plain.visit(parse("""
                package com.example;
                public class C {
                    private int count;
                    public void m(int a) {
                        int b = a + 1;
                        this.count = b;
                        try { work(); } catch (RuntimeException e) {}
                    }
                    void work() {}
                }
                """), null);

            assertTrue(plain.getExtractedVariables().isEmpty(), "no LocalVariable nodes when off");
            assertTrue(plain.getExtractedEdges().stream().noneMatch(e ->
                    e.type().equals("READS") || e.type().equals("WRITES") || e.type().equals("CATCHES")),
                    "no deep-CPG edges when off");
        }
    }

    @Nested
    @DisplayName("INSTANTIATES and OVERRIDES (CPG-lite Phase 2)")
    class InstantiationAndOverride {

        private CompilationUnit parseWithSolver(String code) {
            JavaParser solverParser = new JavaParser(new com.github.javaparser.ParserConfiguration()
                    .setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(
                            new com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver())));
            ParseResult<CompilationUnit> result = solverParser.parse(code);
            assertTrue(result.isSuccessful());
            return result.getResult().orElseThrow();
        }

        @Test
        @DisplayName("new SomeClass(...) inside a method produces an INSTANTIATES edge (Method -> type)")
        void methodInstantiatesType() {
            CompilationUnit cu = parseWithSolver("""
                package com.example;
                public class Factory {
                    public Helper build() {
                        return new Helper();
                    }
                }
                class Helper {}
                """);

            visitor.visit(cu, null);

            assertTrue(visitor.getExtractedEdges().stream()
                    .anyMatch(e -> e.type().equals("INSTANTIATES")
                            && e.sourceFullName().equals("com.example.Factory.build()")
                            && e.targetFullName().equals("com.example.Helper")),
                    "expected INSTANTIATES Factory.build() -> com.example.Helper");
        }

        @Test
        @DisplayName("new inside a constructor produces an INSTANTIATES edge (Constructor -> type)")
        void constructorInstantiatesType() {
            CompilationUnit cu = parseWithSolver("""
                package com.example;
                public class Service {
                    private final Helper helper;
                    public Service() {
                        this.helper = new Helper();
                    }
                }
                class Helper {}
                """);

            visitor.visit(cu, null);

            assertTrue(visitor.getExtractedEdges().stream()
                    .anyMatch(e -> e.type().equals("INSTANTIATES")
                            && e.sourceFullName().equals("com.example.Service.<init>()")
                            && e.targetFullName().equals("com.example.Helper")),
                    "expected INSTANTIATES Service.<init>() -> com.example.Helper");
        }

        @Test
        @DisplayName("overriding an in-project superclass method produces an OVERRIDES edge")
        void overridesResolvedSuperclassMethod() {
            CompilationUnit cu = parseWithSolver("""
                package com.example;
                class Base {
                    public void run() {}
                }
                class Sub extends Base {
                    @Override
                    public void run() {}
                }
                """);

            visitor.visit(cu, null);

            assertTrue(visitor.getExtractedEdges().stream()
                    .anyMatch(e -> e.type().equals("OVERRIDES")
                            && e.sourceFullName().equals("com.example.Sub.run()")
                            && e.targetFullName().equals("com.example.Base.run()")),
                    "expected OVERRIDES Sub.run() -> Base.run()");
        }

        @Test
        @DisplayName("does NOT emit OVERRIDES when the supertype cannot be resolved (conservative)")
        void noOverridesForUnresolvedSupertype() {
            // ExternalBase is not in the project and not on the reflection classpath,
            // so the hierarchy is unresolvable and no OVERRIDES edge may be inferred.
            CompilationUnit cu = parseWithSolver("""
                package com.example;
                public class Sub extends com.unknown.ExternalBase {
                    @Override
                    public void run() {}
                }
                """);

            visitor.visit(cu, null);

            assertTrue(visitor.getExtractedEdges().stream()
                    .noneMatch(e -> e.type().equals("OVERRIDES")),
                    "must not infer OVERRIDES from @Override alone when the target is unresolved");
        }
    }
}
