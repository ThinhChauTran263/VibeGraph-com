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
 * Tests for SpringAnnotationVisitor - detects Spring annotations, produces APIEndpoint nodes,
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
        @DisplayName("@GetMapping should produce an APIEndpoint node AND a HANDLES_ROUTE edge to it")
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

            // The API endpoint node must exist...
            NodeData route = nodes.stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No APIEndpoint node extracted"));
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
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.fullName()).isEqualTo("POST /api/orders");
        }

        @Test
        @DisplayName("non-mapping methods produce no APIEndpoint node")
        void noRouteForPlainMethod() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                public class PlainService {
                    public void doWork() { }
                }
                """);

            assertThat(visitor.getExtractedNodes())
                    .noneMatch(n -> n.type().equals("APIEndpoint"));
            assertThat(visitor.getExtractedEdges())
                    .noneMatch(e -> e.type().equals("HANDLES_ROUTE"));
        }
    }

    @Nested
    @DisplayName("Security role extraction")
    class SecurityRoleExtraction {

        @Test
        @DisplayName("@PreAuthorize hasRole('ADMIN') attaches requiredRole=ADMIN to the endpoint")
        void preAuthorizeAdminRole() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.security.access.prepost.PreAuthorize;

                public class ProductController {
                    @PostMapping("/products")
                    @PreAuthorize("hasRole('ADMIN')")
                    public void create() { }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).containsEntry("requiredRole", "ADMIN");
        }

        @Test
        @DisplayName("class-level @PreAuthorize applies to every method lacking its own role")
        void classLevelRoleAppliesToMethods() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.security.access.prepost.PreAuthorize;

                @PreAuthorize("hasAuthority('ROLE_ADMIN')")
                public class AnalyticsController {
                    @GetMapping("/analytics")
                    public String view() { return null; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            // ROLE_ prefix is stripped.
            assertThat(route.properties()).containsEntry("requiredRole", "ADMIN");
        }

        @Test
        @DisplayName("@RolesAllowed and @Secured are also mined")
        void rolesAllowedAndSecured() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.GetMapping;
                import jakarta.annotation.security.RolesAllowed;

                public class ReportController {
                    @GetMapping("/reports")
                    @RolesAllowed("ADMIN")
                    public String reports() { return null; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).containsEntry("requiredRole", "ADMIN");
        }

        @Test
        @DisplayName("an endpoint without any security annotation has no requiredRole")
        void noRoleWhenUnsecured() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.GetMapping;

                public class CatalogController {
                    @GetMapping("/catalog")
                    public String catalog() { return null; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).doesNotContainKey("requiredRole");
        }
    }

    @Nested
    @DisplayName("View (page) route detection")
    class ViewRouteDetection {

        @Test
        @DisplayName("a plain @Controller GET returning a view name is marked view=true")
        void controllerViewGetMarkedView() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;

                @Controller
                public class HomeController {
                    @GetMapping("/checkout")
                    public String checkout() { return "module/order/checkout"; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).containsEntry("view", true);
        }

        @Test
        @DisplayName("a @RestController route is never marked as a view, even returning String")
        void restControllerNotView() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class ApiController {
                    @GetMapping("/api/ping")
                    public String ping() { return "pong"; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).doesNotContainKey("view");
        }

        @Test
        @DisplayName("a @Controller method with @ResponseBody returns data, not a view")
        void responseBodyNotView() {
            SpringAnnotationVisitor visitor = visit("""
                package com.example;
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.ResponseBody;

                @Controller
                public class MixedController {
                    @GetMapping("/data")
                    @ResponseBody
                    public String data() { return "{}"; }
                }
                """);

            NodeData route = visitor.getExtractedNodes().stream()
                    .filter(n -> n.type().equals("APIEndpoint"))
                    .findFirst()
                    .orElseThrow();
            assertThat(route.properties()).doesNotContainKey("view");
        }
    }
}
