package com.vibegraph.parser.visitor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;

/**
 * Tests for SpringAnnotationVisitor — detects Spring annotations, produces Route nodes,
 * HANDLES_ROUTE edges, and INJECTS edges.
 *
 * Run: mvn test -Dtest=SpringAnnotationVisitorTest
 */
@DisplayName("SpringAnnotationVisitor")
class SpringAnnotationVisitorTest {

    private SpringAnnotationVisitor visit(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        SpringAnnotationVisitor visitor = new SpringAnnotationVisitor();
        visitor.visit(cu, null);
        return visitor;
    }

    @Nested
    @DisplayName("Route extraction")
    class RouteExtraction {

        @Test
        @DisplayName("@GetMapping should produce a Route node AND a HANDLES_ROUTE edge to it")
        void getMappingProducesRouteNodeAndEdge() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    @GetMapping("/{id}")
                    public String findById(Long id) { return null; }
                }
                """);

            List<NodeData> nodes = visitor.getExtractedNodes();
            List<EdgeData> edges = visitor.getExtractedEdges();

            // The route node must exist...
            NodeData route = nodes.stream()
                    .filter(n -> n.type().equals("Route"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No Route node extracted"));
            assertThat(route.fullName()).isEqualTo("GET /api/users/{id}");
            assertThat(route.properties()).containsEntry("httpMethod", "GET");
            assertThat(route.properties()).containsEntry("routePath", "/api/users/{id}");

            // ...and a HANDLES_ROUTE edge must target that exact node id.
            // This pairing is the regression guard: if the node is dropped from the
            // aggregate, MATCH-based upsert silently drops this edge.
            EdgeData handles = edges.stream()
                    .filter(e -> e.type().equals("HANDLES_ROUTE"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No HANDLES_ROUTE edge extracted"));
            assertThat(handles.targetFullName()).isEqualTo(route.fullName());
            assertThat(handles.sourceFullName()).contains("UserController.findById");
        }

        @Test
        @DisplayName("class-level @RequestMapping prefix is combined with method path")
        void combinesClassPrefixWithMethodPath() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;

                @RequestMapping("/api")
                public class OrderController {
                    @PostMapping("/orders")
                    public void create() { }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("Route"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.fullName()).isEqualTo("POST /api/orders");
        }

        @Test
        @DisplayName("non-mapping methods produce no Route node")
        void noRouteForPlainMethod() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                public class PlainService {
                    public void doWork() { }
                }
                """);

            assertThat(visitor.getExtractedNodes())
                    .noneMatch(n -> n.type().equals("Route"));
            assertThat(visitor.getExtractedEdges())
                    .noneMatch(e -> e.type().equals("HANDLES_ROUTE"));
        }
    }

    @Nested
    @DisplayName("Injection detection")
    class InjectionDetection {

        @Test
        @DisplayName("@Autowired field produces an INJECTS edge")
        void autowiredFieldProducesInjectsEdge() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;

                public class UserController {
                    @Autowired
                    private UserService userService;
                }
                """);

            EdgeData injects = visitor.getExtractedEdges().stream()
                    .filter(e -> e.type().equals("INJECTS"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No INJECTS edge extracted"));
            assertThat(injects.sourceFullName()).contains("UserController");
            assertThat(injects.targetFullName()).isEqualTo("com.example.UserService");
        }

        @Test
        @DisplayName("@RequiredArgsConstructor + final field produces an INJECTS edge")
        void lombokConstructorInjectionProducesInjectsEdge() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import lombok.RequiredArgsConstructor;

                @RequiredArgsConstructor
                public class OrderService {
                    private final OrderRepository repository;
                }
                """);

            assertThat(visitor.getExtractedEdges())
                    .anyMatch(e -> e.type().equals("INJECTS")
                            && e.targetFullName().equals("com.example.OrderRepository"));
        }
    }
}
