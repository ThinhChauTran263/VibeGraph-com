package com.vibegraph.parser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vibegraph.parser.node.EdgeData;
import com.vibegraph.parser.node.NodeData;
import com.vibegraph.parser.node.ParseResult;
import com.vibegraph.parser.service.impl.ParserServiceImpl;

/**
 * Tests for ParserService - main parsing orchestrator.
 *
 * Run: mvn test -Dtest=ParserServiceTest
 */
@DisplayName("ParserService")
class ParserServiceTest {

    @TempDir
    Path tempDir;

    private ParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new ParserServiceImpl();
    }

    @Nested
    @DisplayName("parseFile")
    class ParseFile {

        @Test
        @DisplayName("should parse single Java file and extract class + method nodes")
        void shouldParseSingleFile() throws IOException {
            Path javaFile = tempDir.resolve("UserService.java");
            Files.writeString(javaFile, """
                package com.example;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    private final UserRepository repository;

                    public UserService(UserRepository repository) {
                        this.repository = repository;
                    }

                    public User findById(Long id) {
                        return repository.findById(id).orElse(null);
                    }
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result).isNotNull();
            assertThat(result.getNodes()).isNotEmpty();
            assertThat(result.getNodes()).anyMatch(n -> n.name().equals("UserService"));
            assertThat(result.getNodes()).anyMatch(n -> n.name().equals("findById"));
        }

        @Test
        @DisplayName("should emit a Package node and CONTAINS edge (Package -> File) from the package declaration")
        void shouldEmitPackageNodeAndContainsEdge() throws IOException {
            Path javaFile = tempDir.resolve("UserService.java");
            Files.writeString(javaFile, """
                package com.example.service;

                public class UserService {
                    public void run() {}
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getNodes())
                    .as("Package node from the package declaration")
                    .anyMatch(n -> n.type().equals("Package")
                            && n.fullName().equals("com.example.service")
                            && n.properties().get("packageName").equals("com.example.service"));

            assertThat(result.getNodes())
                    .as("File/Class/Method nodes carry normalized packageName from AST package declaration")
                    .filteredOn(n -> n.fullName().equals(javaFile.toString())
                            || n.fullName().equals("com.example.service.UserService")
                            || n.fullName().equals("com.example.service.UserService.run()"))
                    .allSatisfy(n -> assertThat(n.properties()).containsEntry("packageName", "com.example.service"));

            assertThat(result.getEdges())
                    .as("CONTAINS edge points Package -> File")
                    .anyMatch(e -> e.type().equals("CONTAINS")
                            && e.sourceFullName().equals("com.example.service")
                            && e.targetFullName().equals(javaFile.toString()));

            // The Package node must NOT collect a File -[:DEFINES]-> Package edge.
            assertThat(result.getEdges())
                    .noneMatch(e -> e.type().equals("DEFINES")
                            && e.targetFullName().equals("com.example.service"));
        }

        @Test
        @DisplayName("should attach annotation usages to node metadata without usage nodes/edges")
        void shouldAttachAnnotationMetadataWithoutUsageGraph() throws IOException {
            Path javaFile = tempDir.resolve("UserService.java");
            Files.writeString(javaFile, """
                package com.example;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {}
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getNodes())
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserService");
                        assertThat(n.properties()).containsEntry("annotations", List.of("Service"));
                    });
            assertThat(result.getNodes())
                    .noneMatch(n -> n.type().equals("Annotation")
                            && n.fullName().equals("org.springframework.stereotype.Service"));
            assertThat(result.getEdges())
                    .noneMatch(e -> e.type().equals("ANNOTATED_BY"));
        }

        @Test
        @DisplayName("controller file should yield an APIEndpoint node inside the ParseResult")
        void shouldAggregateRouteNode() throws IOException {
            Path javaFile = tempDir.resolve("UserController.java");
            Files.writeString(javaFile, """
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

            ParseResult result = parserService.parseFile(javaFile);

            // Regression guard for the dropped-route bug: the APIEndpoint node must be
            // present in the aggregated nodes, not only referenced by an edge.
            assertThat(result.getNodes())
                    .as("APIEndpoint node must be aggregated into ParseResult")
                    .anyMatch(n -> n.type().equals("APIEndpoint") && n.fullName().equals("GET /api/users/{id}"));

            assertThat(result.getEdges())
                    .as("HANDLES_ROUTE edge must target the aggregated APIEndpoint node")
                    .anyMatch(e -> e.type().equals("HANDLES_ROUTE")
                            && e.targetFullName().equals("GET /api/users/{id}"));
        }

        @Test
        @DisplayName("should restrict File-DEFINES to top-level architectural types only")
        void shouldRestrictFileDefinesToTopLevelTypesOnly() throws IOException {
            Path javaFile = tempDir.resolve("Mixed.java");
            Files.writeString(javaFile, """
                package com.example;

                import org.springframework.stereotype.Service;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @Service
                public class MixedController {
                    private String name;

                    @GetMapping("/ping")
                    public String ping() { return "ok"; }

                    public static class Nested {}
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getEdges())
                    .filteredOn(e -> e.type().equals("DEFINES"))
                    .extracting(e -> e.targetFullName())
                    .contains("com.example.MixedController")
                    .doesNotContain(
                            "com.example.MixedController.ping()",
                            "com.example.MixedController.name",
                            "GET /ping",
                            "org.springframework.stereotype.Service");
        }

        @Test
        @DisplayName("should assign coarse architectural layers to parsed nodes")
        void shouldAssignNodeLayers() throws IOException {
            Path javaFile = tempDir.resolve("Layers.java");
            Files.writeString(javaFile, """
                package com.example;
                import org.springframework.web.bind.annotation.RestController;
                import jakarta.persistence.Entity;

                @RestController
                public class UserController {
                    public void handle() {}
                }
                class UserServiceImpl {}
                interface UserRepository {}
                @Entity
                class UserEntity {}
                record UserRecord(String id) {}
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getNodes())
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserController");
                        assertThat(n.properties()).containsEntry("layer", "PRESENTATION");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserController.handle()");
                        assertThat(n.properties()).containsEntry("layer", "PRESENTATION");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserServiceImpl");
                        assertThat(n.properties()).containsEntry("layer", "SERVICE");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserRepository");
                        assertThat(n.properties()).containsEntry("layer", "DATA_ACCESS");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserEntity");
                        assertThat(n.type()).isEqualTo("DBModel");
                        assertThat(n.properties()).containsEntry("layer", "DOMAIN");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo("com.example.UserRecord");
                        assertThat(n.properties()).containsEntry("layer", "DOMAIN");
                    })
                    .anySatisfy(n -> {
                        assertThat(n.fullName()).isEqualTo(javaFile.toString());
                        assertThat(n.type()).isEqualTo("File");
                        assertThat(n.properties()).containsEntry("layer", "PRESENTATION");
                    });
        }

        @Test
        @DisplayName("should aggregate duplicate edges with weight and bounded occurrences")
        void shouldAggregateDuplicateEdges() throws IOException {
            Path javaFile = tempDir.resolve("Caller.java");
            Files.writeString(javaFile, """
                package com.example;
                public class Caller {
                    public void run() {
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                        helper();
                    }
                    void helper() {}
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            var calls = result.getEdges().stream()
                    .filter(edge -> edge.type().equals("CALLS"))
                    .filter(edge -> edge.sourceFullName().equals("com.example.Caller.run()"))
                    .filter(edge -> edge.targetFullName().equals("com.example.Caller.helper()"))
                    .toList();

            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).properties()).containsEntry("weight", 12);
            assertThat((List<?>) calls.get(0).properties().get("occurrences")).hasSize(10);
        }

        @Test
        @DisplayName("should not emit guessed edges for unverified project types")
        void shouldSkipGuessedTypeReferencesWhenNotInProjectRegistry() throws IOException {
            Path javaFile = tempDir.resolve("UnknownRef.java");
            Files.writeString(javaFile, """
                package com.example;
                public class UnknownRef {
                    private MissingType value;
                    public MissingType find() { return null; }
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getEdges())
                    .noneMatch(edge -> edge.targetFullName().equals("com.example.MissingType")
                            || edge.targetFullName().equals("MissingType"));
        }

        @Test
        @DisplayName("deep CPG disabled (plain construction, no Spring @Value): no LocalVariable nodes or READS/WRITES/CATCHES edges")
        void deepCpgOffByDefault() throws IOException {
            Path javaFile = tempDir.resolve("Calc.java");
            Files.writeString(javaFile, """
                package com.example;
                public class Calc {
                    private int total;
                    public void add(int x) {
                        int y = x + 1;
                        this.total = y;
                        try { compute(); } catch (RuntimeException e) {}
                    }
                    void compute() {}
                }
                """);

            ParseResult result = parserService.parseFile(javaFile);

            assertThat(result.getNodes())
                    .as("no LocalVariable nodes when deep CPG disabled")
                    .noneMatch(n -> n.type().equals("LocalVariable"));
            assertThat(result.getEdges())
                    .as("no deep-CPG edges when disabled")
                    .noneMatch(e -> e.type().equals("READS")
                            || e.type().equals("WRITES")
                            || e.type().equals("CATCHES"));
        }

        @Test
        @DisplayName("should handle parse errors gracefully")
        void shouldHandleParseErrorsGracefully() throws IOException {
            Path invalidFile = tempDir.resolve("Invalid.java");
            Files.writeString(invalidFile, """
                package com.example;
                public class Invalid {
                    // Missing closing brace - syntax error
                """);

            ParseResult result = parserService.parseFile(invalidFile);

            assertThat(result).isNotNull();
            assertThat(result.getWarnings()).isNotEmpty();
        }

        @Test
        @DisplayName("non-.java file should return a warning and no nodes")
        void shouldRejectNonJavaFile() throws IOException {
            Path txtFile = tempDir.resolve("notes.txt");
            Files.writeString(txtFile, "not java");

            ParseResult result = parserService.parseFile(txtFile);

            assertThat(result.getNodes()).isEmpty();
            assertThat(result.getWarnings()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("parseProject")
    class ParseProject {

        @Test
        @DisplayName("should parse all Java files in project directory")
        void shouldParseAllJavaFiles() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("User.java"), """
                package com.example;
                public class User {
                    private Long id;
                    private String name;
                }
                """);

            Files.writeString(srcDir.resolve("UserService.java"), """
                package com.example;
                public class UserService {
                    public User findById(Long id) { return null; }
                }
                """);

            Files.writeString(srcDir.resolve("UserController.java"), """
                package com.example;
                public class UserController {
                    private UserService service;
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);

            assertThat(results).hasSize(3);
            assertThat(results).flatExtracting(ParseResult::getNodes)
                    .anyMatch(n -> n.name().equals("User"))
                    .anyMatch(n -> n.name().equals("UserService"))
                    .anyMatch(n -> n.name().equals("UserController"));
        }

        @Test
        @DisplayName("should keep type-reference edges to known project symbols")
        void shouldKeepProjectScopedTypeReferences() throws IOException {
            Path domainDir = tempDir.resolve("src/main/java/com/example/domain");
            Path serviceDir = tempDir.resolve("src/main/java/com/example/service");
            Files.createDirectories(domainDir);
            Files.createDirectories(serviceDir);

            Files.writeString(domainDir.resolve("User.java"), """
                package com.example.domain;
                public class User {}
                """);

            Files.writeString(serviceDir.resolve("UserService.java"), """
                package com.example.service;
                import com.example.domain.User;

                public class UserService {
                    private User user;
                    public User find() { return user; }
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);

            assertThat(results).flatExtracting(ParseResult::getEdges)
                    .anyMatch(e -> e.type().equals("IMPORTS")
                            && e.sourceFullName().equals("com.example.service.UserService")
                            && e.targetFullName().equals("com.example.domain.User"))
                    .anyMatch(e -> e.type().equals("TYPE_OF")
                            && e.targetFullName().equals("com.example.domain.User"))
                    .anyMatch(e -> e.type().equals("RETURNS")
                            && e.targetFullName().equals("com.example.domain.User"));
        }

        @Test
        @DisplayName("should emit verified aggregated HAS_RELATION edges for JPA domain fields")
        void shouldEmitVerifiedAggregatedJpaRelations() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java/com/example/domain");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("Order.java"), """
                package com.example.domain;
                import jakarta.persistence.Entity;

                @Entity
                public class Order {}
                """);

            Files.writeString(srcDir.resolve("User.java"), """
                package com.example.domain;
                import jakarta.persistence.Entity;
                import jakarta.persistence.OneToMany;
                import jakarta.persistence.ManyToOne;
                import java.util.List;

                @Entity
                public class User {
                    @OneToMany
                    private List<Order> orders;
                    @ManyToOne
                    private Order primaryOrder;
                    @ManyToOne
                    private MissingExternal missing;
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);
            List<EdgeData> edges = results.stream().flatMap(result -> result.getEdges().stream()).toList();

            List<EdgeData> relations = edges.stream()
                    .filter(edge -> edge.type().equals("HAS_RELATION"))
                    .filter(edge -> edge.sourceFullName().equals("com.example.domain.User"))
                    .filter(edge -> edge.targetFullName().equals("com.example.domain.Order"))
                    .toList();

            assertThat(relations).hasSize(1);
            assertThat(relations.get(0).properties()).containsEntry("weight", 2);
            assertThat(((List<?>) relations.get(0).properties().get("fields")).stream()
                    .map(Object::toString)
                    .toList()).containsExactlyInAnyOrder("orders", "primaryOrder");
            assertThat(((List<?>) relations.get(0).properties().get("cardinalities")).stream()
                    .map(Object::toString)
                    .toList()).containsExactlyInAnyOrder("ONE_TO_MANY", "MANY_TO_ONE");
            assertThat(edges)
                    .noneMatch(edge -> edge.type().equals("HAS_RELATION")
                            && edge.targetFullName().contains("MissingExternal"));
        }

        @Test
        @DisplayName("should emit pure Spring event facts and async/scheduled method properties")
        void shouldEmitSpringImplicitFlowFacts() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("UserCreatedEvent.java"), """
                package com.example;
                public class UserCreatedEvent {}
                """);

            Files.writeString(srcDir.resolve("UserService.java"), """
                package com.example;
                import org.springframework.context.ApplicationEventPublisher;
                import org.springframework.scheduling.annotation.Async;
                import org.springframework.scheduling.annotation.Scheduled;

                public class UserService {
                    private final ApplicationEventPublisher publisher;
                    public UserService(ApplicationEventPublisher publisher) {
                        this.publisher = publisher;
                    }
                    @Async
                    public void create() {
                        publisher.publishEvent(new UserCreatedEvent());
                    }
                    @Scheduled(fixedRate = 1000)
                    public void sweep() {}
                }
                """);

            Files.writeString(srcDir.resolve("UserListener.java"), """
                package com.example;
                import org.springframework.context.event.EventListener;
                import org.springframework.transaction.event.TransactionalEventListener;

                public class UserListener {
                    @EventListener(UserCreatedEvent.class)
                    public void onUserCreated() {}
                    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, condition = "#event != null")
                    public void afterCommit(UserCreatedEvent event) {}
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);
            List<NodeData> nodes = results.stream().flatMap(result -> result.getNodes().stream()).toList();
            List<EdgeData> edges = results.stream().flatMap(result -> result.getEdges().stream()).toList();

            assertThat(nodes)
                    .anySatisfy(node -> {
                        assertThat(node.fullName()).isEqualTo("com.example.UserService.create()");
                        assertThat(node.properties()).containsEntry("isAsync", true);
                    })
                    .anySatisfy(node -> {
                        assertThat(node.fullName()).isEqualTo("com.example.UserService.sweep()");
                        assertThat(node.properties())
                                .containsEntry("isScheduled", true)
                                .containsEntry("entrypoint", true)
                                .containsEntry("entrypointKind", "SCHEDULED");
                    });

            assertThat(edges)
                    .anySatisfy(edge -> {
                        assertThat(edge.type()).isEqualTo("PUBLISHES_EVENT");
                        assertThat(edge.sourceFullName()).isEqualTo("com.example.UserService.create()");
                        assertThat(edge.targetFullName()).isEqualTo("com.example.UserCreatedEvent");
                    })
                    .anySatisfy(edge -> {
                        assertThat(edge.type()).isEqualTo("LISTENS_EVENT");
                        assertThat(edge.sourceFullName()).isEqualTo("com.example.UserListener.onUserCreated()");
                        assertThat(edge.targetFullName()).isEqualTo("com.example.UserCreatedEvent");
                    })
                    .anySatisfy(edge -> {
                        assertThat(edge.type()).isEqualTo("LISTENS_EVENT");
                        assertThat(edge.sourceFullName()).isEqualTo("com.example.UserListener.afterCommit(UserCreatedEvent)");
                        assertThat(edge.targetFullName()).isEqualTo("com.example.UserCreatedEvent");
                        assertThat(edge.properties()).containsEntry("phase", "TransactionPhase.AFTER_COMMIT");
                    });
        }

        @Test
        @DisplayName("strict payload leakage test excludes legacy parser artifacts")
        void strictPayloadLeakageTest() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java/com/example");
            Files.createDirectories(srcDir);

            Files.writeString(srcDir.resolve("CleanPayload.java"), """
                package com.example;

                import com.nonexistent.library.PhantomDependency;
                import org.springframework.beans.factory.annotation.Autowired;
                import jakarta.validation.constraints.NotNull;

                public class CleanPayload {
                    private String name;
                    private PhantomDependency dependency;
                    private MissingExternal missing;

                    @Autowired
                    public CleanPayload() {}

                    @NotNull
                    public String getName() { return name; }

                    public void setName(String name) { this.name = name; }

                    @Override
                    public String toString() { return name; }

                    @Override
                    public int hashCode() { return 31; }

                    public MissingExternal loadMissing(PhantomDependency input)
                            throws MissingExternalException { return missing; }

                    public void process() {
                        this.getName();
                        this.setName("updated");
                        this.toString();
                        this.hashCode();
                        helper();
                    }

                    void helper() {}
                }
                """);

            List<ParseResult> results = parserService.parseProject(tempDir);
            List<NodeData> nodes = results.stream().flatMap(result -> result.getNodes().stream()).toList();
            List<EdgeData> edges = results.stream().flatMap(result -> result.getEdges().stream()).toList();

            assertThat(nodes)
                    .as("routine methods and no-op constructors must not be materialized")
                    .noneMatch(n -> isRoutineMethod(n));

            assertThat(edges)
                    .as("no edge may point to or originate from a skipped routine member")
                    .noneMatch(e -> pointsToSkippedMethod(e));

            assertThat(edges)
                    .as("annotations on skipped members must not leak into the payload")
                    .noneMatch(e -> isAnnotatedByOnSkippedMember(e));

            assertThat(nodes)
                    .as("annotations used only on skipped members must not be materialized")
                    .noneMatch(node -> node.type().equals("Annotation")
                            && (node.fullName().equals("org.springframework.beans.factory.annotation.Autowired")
                            || node.fullName().equals("jakarta.validation.constraints.NotNull")));

            assertThat(edges)
                    .as("unknown external types and unresolved fallback stubs must not leak")
                    .noneMatch(e -> isGuessedExternalTypeEdge(e)
                            || "unresolved".equals(e.properties().get("targetType")));

            Set<String> nodeNames = nodes.stream().map(NodeData::fullName).collect(java.util.stream.Collectors.toSet());
            assertThat(edges)
                    .as("every payload edge must have materialized endpoints")
                    .allSatisfy(edge -> {
                        assertThat(nodeNames).contains(edge.sourceFullName());
                        assertThat(nodeNames).contains(edge.targetFullName());
                    });

            assertThat(edges)
                    .as("non-routine domain calls should survive the cleanup")
                    .anyMatch(edge -> edge.type().equals("CALLS")
                            && edge.sourceFullName().equals("com.example.CleanPayload.process()")
                            && edge.targetFullName().equals("com.example.CleanPayload.helper()"));

            System.out.println("VERIFICATION COMPLETE: NO DUST/LEGACY CODE LEAKS IN PARSER PAYLOAD.");
        }

        private boolean isRoutineMethod(NodeData node) {
            return node.type().equals("Method") || node.type().equals("Constructor")
                    ? isSkippedMember(node.fullName())
                    : false;
        }

        private boolean pointsToSkippedMethod(EdgeData edge) {
            return isSkippedMember(edge.sourceFullName()) || isSkippedMember(edge.targetFullName());
        }

        private boolean isAnnotatedByOnSkippedMember(EdgeData edge) {
            return edge.type().equals("ANNOTATED_BY") && isSkippedMember(edge.sourceFullName());
        }

        private boolean isGuessedExternalTypeEdge(EdgeData edge) {
            Set<String> typeEdges = Set.of(
                    "IMPORTS", "TYPE_OF", "RETURNS", "PARAMETER_TYPE", "THROWS",
                    "INJECTS", "INSTANTIATES", "CATCHES", "EXTENDS", "IMPLEMENTS");
            String target = edge.targetFullName();
            return typeEdges.contains(edge.type())
                    && (target.equals("MissingExternal")
                    || target.equals("MissingExternalException")
                    || target.startsWith("com.example.MissingExternal")
                    || target.startsWith("com.nonexistent.library."));
        }

        private boolean isSkippedMember(String fullName) {
            return fullName != null
                    && (fullName.endsWith(".getName()")
                    || fullName.endsWith(".setName(String)")
                    || fullName.endsWith(".toString()")
                    || fullName.endsWith(".hashCode()")
                    || fullName.endsWith(".<init>()"));
        }

        @Test
        @DisplayName("should skip ignored directories (target/)")
        void shouldSkipIgnoredDirectories() throws IOException {
            Path srcDir = tempDir.resolve("src/main/java");
            Path targetDir = tempDir.resolve("target/classes");
            Files.createDirectories(srcDir);
            Files.createDirectories(targetDir);

            Files.writeString(srcDir.resolve("App.java"), "public class App {}");
            Files.writeString(targetDir.resolve("App.java"), "// compiled");

            List<ParseResult> results = parserService.parseProject(tempDir);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("parseProject on the sample-project fixture yields non-empty nodes and edges")
        void shouldParseSampleProjectFixture() {
            Path sampleProject = Path.of("src/test/resources/sample-project");
            assertThat(Files.isDirectory(sampleProject))
                    .as("sample-project fixture must exist at " + sampleProject.toAbsolutePath())
                    .isTrue();

            List<ParseResult> results = parserService.parseProject(sampleProject);

            assertThat(results).as("fixture .java files").isNotEmpty();
            assertThat(results).flatExtracting(ParseResult::getNodes)
                    .as("nodes must not be empty").isNotEmpty()
                    .anyMatch(n -> n.name().equals("SampleUserService"));
            assertThat(results).flatExtracting(ParseResult::getEdges)
                    .as("edges must not be empty").isNotEmpty();
        }

        @Test
        @DisplayName("bounded project parsing stops before returning an oversized aggregate")
        void boundedParseRejectsOversizedAggregate() throws IOException {
            Files.writeString(tempDir.resolve("Alpha.java"), "public class Alpha { void run() {} }");
            Files.writeString(tempDir.resolve("Beta.java"), "public class Beta { void run() {} }");

            assertThatThrownBy(() -> parserService.parseProject(
                    tempDir, ParseProgressListener.NOOP, 1, 100))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("too large to analyze")
                    .hasMessageContaining("1 / 100");
        }
    }
}
