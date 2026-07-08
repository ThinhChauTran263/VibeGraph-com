package com.vibegraph.diagram.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.diagram.dto.response.DiagramResponse;
import com.vibegraph.diagram.service.impl.ClassDiagramServiceImpl;
import com.vibegraph.diagram.service.impl.MermaidGeneratorServiceImpl;
import com.vibegraph.graph.dto.response.EdgeDto;
import com.vibegraph.graph.dto.response.GraphDataResponse;
import com.vibegraph.graph.dto.response.NodeDto;
import com.vibegraph.graph.service.GraphService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassDiagramService")
class ClassDiagramServiceTest {

    private static final String PROJECT_ID = "proj-1";

    @Mock
    GraphService graphService;

    private ClassDiagramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassDiagramServiceImpl(graphService, new MermaidGeneratorServiceImpl());
    }

    private NodeDto classNode(String fullName, Map<String, Object> props) {
        return NodeDto.builder()
                .id(fullName).type("Class").name(simple(fullName)).fullName(fullName)
                .properties(props).build();
    }

    private NodeDto typeNode(String type, String fullName, Map<String, Object> props) {
        return NodeDto.builder()
                .id(fullName).type(type).name(simple(fullName)).fullName(fullName)
                .properties(props).build();
    }

    private NodeDto methodNode(String ownerFullName, String name, String visibility,
                               String returnType, List<String> paramTypes) {
        String id = ownerFullName + "#" + name;
        return NodeDto.builder()
                .id(id).type("Method").name(name).fullName(id)
                .properties(Map.of(
                        "visibility", visibility,
                        "returnType", returnType,
                        "paramTypes", paramTypes))
                .build();
    }

    private NodeDto fieldNode(String ownerFullName, String name, String visibility, String declaredType) {
        String id = ownerFullName + "." + name;
        return NodeDto.builder()
                .id(id).type("Field").name(name).fullName(id)
                .properties(Map.of("visibility", visibility, "declaredType", declaredType))
                .build();
    }

    private EdgeDto edge(String type, String source, String target) {
        return EdgeDto.builder().type(type).source(source).target(target).build();
    }

    private String simple(String fullName) {
        int idx = fullName.lastIndexOf('.');
        return idx < 0 ? fullName : fullName.substring(idx + 1);
    }

    private void stubGraph(List<NodeDto> nodes, List<EdgeDto> edges) {
        when(graphService.getFullGraph(PROJECT_ID))
                .thenReturn(GraphDataResponse.builder().nodes(nodes).edges(edges).build());
    }

    @Test
    @DisplayName("renders a class with visibility-marked field and method")
    void rendersClassWithMembers() {
        String cls = "com.app.UserService";
        stubGraph(
                List.of(classNode(cls, Map.of("visibility", "public", "springLayer", "SERVICE")),
                        fieldNode(cls, "repo", "private", "UserRepository"),
                        methodNode(cls, "create", "public", "User", List.of("CreateDto"))),
                List.of(edge("HAS_FIELD", cls, cls + ".repo"),
                        edge("HAS_METHOD", cls, cls + "#create")));

        DiagramResponse response = service.generateClassDiagram(PROJECT_ID, null);

        String mermaid = response.getMermaidSyntax();
        assertThat(response.getDiagramType()).isEqualTo("class");
        assertThat(mermaid).startsWith("classDiagram");
        assertThat(mermaid).contains("class UserService {");
        assertThat(mermaid).contains("<<SERVICE>>");
        assertThat(mermaid).contains("-UserRepository repo");
        assertThat(mermaid).contains("+create(CreateDto) User");
    }

    @Test
    @DisplayName("applies interface and enum stereotypes")
    void interfaceAndEnumStereotypes() {
        stubGraph(
                List.of(typeNode("Interface", "com.app.Repo", Map.of("visibility", "public")),
                        typeNode("Enum", "com.app.Status", Map.of("visibility", "public"))),
                List.of());

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).contains("class Repo {");
        assertThat(mermaid).contains("<<interface>>");
        assertThat(mermaid).contains("class Status {");
        assertThat(mermaid).contains("<<enumeration>>");
    }

    @Test
    @DisplayName("marks abstract classes with <<abstract>>")
    void abstractStereotype() {
        stubGraph(
                List.of(classNode("com.app.Base", Map.of("visibility", "public", "abstract", true))),
                List.of());

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).contains("<<abstract>>");
    }

    @Test
    @DisplayName("renders EXTENDS, IMPLEMENTS and INJECTS relationships between selected classifiers")
    void rendersRelationships() {
        String impl = "com.app.UserServiceImpl";
        String iface = "com.app.UserService";
        String repo = "com.app.UserRepository";
        String base = "com.app.BaseService";
        stubGraph(
                List.of(classNode(impl, Map.of("visibility", "public")),
                        typeNode("Interface", iface, Map.of("visibility", "public")),
                        classNode(repo, Map.of("visibility", "public")),
                        classNode(base, Map.of("visibility", "public"))),
                List.of(edge("IMPLEMENTS", impl, iface),
                        edge("EXTENDS", impl, base),
                        edge("INJECTS", impl, repo),
                        // dangling endpoint outside the project must be skipped
                        edge("EXTENDS", impl, "java.lang.Object")));

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).contains("UserServiceImpl ..|> UserService : implements");
        assertThat(mermaid).contains("UserServiceImpl --|> BaseService : extends");
        assertThat(mermaid).contains("UserServiceImpl --> UserRepository : uses");
        assertThat(mermaid).doesNotContain("Object");
    }

    @Test
    @DisplayName("package filter keeps matching package and sub-packages, drops others")
    void packageFilter() {
        stubGraph(
                List.of(classNode("com.app.web.UserController", Map.of("visibility", "public")),
                        classNode("com.app.web.api.AdminController", Map.of("visibility", "public")),
                        classNode("com.app.data.UserRepository", Map.of("visibility", "public"))),
                List.of());

        String mermaid = service.generateClassDiagram(PROJECT_ID, "com.app.web").getMermaidSyntax();

        assertThat(mermaid).contains("class UserController {");
        assertThat(mermaid).contains("class AdminController {");
        assertThat(mermaid).doesNotContain("UserRepository");
    }

    @Test
    @DisplayName("returns a valid empty-but-syntactic diagram when there are no classes")
    void emptyGraphProducesValidDiagram() {
        stubGraph(List.of(), List.of());

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).startsWith("classDiagram");
        assertThat(mermaid).doesNotContain("-->");
        assertThat(mermaid).doesNotContain("class ");
    }

    @Test
    @DisplayName("handles a null graph payload without throwing")
    void nullGraphIsGraceful() {
        when(graphService.getFullGraph(PROJECT_ID)).thenReturn(null);

        DiagramResponse response = service.generateClassDiagram(PROJECT_ID, null);

        assertThat(response.getMermaidSyntax()).startsWith("classDiagram");
    }

    @Test
    @DisplayName("strips generics from member types to keep Mermaid syntax valid")
    void stripsGenerics() {
        String cls = "com.app.Holder";
        stubGraph(
                List.of(classNode(cls, Map.of("visibility", "public")),
                        fieldNode(cls, "items", "private", "List<String>"),
                        methodNode(cls, "find", "public", "Optional<User>", List.of("Map<String, Object>"))),
                List.of(edge("HAS_FIELD", cls, cls + ".items"),
                        edge("HAS_METHOD", cls, cls + "#find")));

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).doesNotContain("<");
        assertThat(mermaid).doesNotContain(">");
        assertThat(mermaid).contains("-List items");
        assertThat(mermaid).contains("+find(Map) Optional");
    }

    @Test
    @DisplayName("maps all visibility specifiers to Mermaid markers")
    void visibilityMarkers() {
        String cls = "com.app.Vis";
        stubGraph(
                List.of(classNode(cls, Map.of("visibility", "public")),
                        fieldNode(cls, "a", "public", "int"),
                        fieldNode(cls, "b", "private", "int"),
                        fieldNode(cls, "c", "protected", "int"),
                        fieldNode(cls, "d", "", "int")),
                List.of(edge("HAS_FIELD", cls, cls + ".a"),
                        edge("HAS_FIELD", cls, cls + ".b"),
                        edge("HAS_FIELD", cls, cls + ".c"),
                        edge("HAS_FIELD", cls, cls + ".d")));

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).contains("+int a");
        assertThat(mermaid).contains("-int b");
        assertThat(mermaid).contains("#int c");
        assertThat(mermaid).contains("~int d");
    }

    @Test
    @DisplayName("deduplicates Mermaid ids when classes share a simple name across packages")
    void deduplicatesClassIds() {
        stubGraph(
                List.of(classNode("com.app.a.User", Map.of("visibility", "public")),
                        classNode("com.app.b.User", Map.of("visibility", "public"))),
                List.of());

        String mermaid = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(mermaid).contains("class User {");
        assertThat(mermaid).contains("class User_2 {");
    }

    @Test
    @DisplayName("produces deterministic output across repeated calls")
    void deterministicOutput() {
        String cls = "com.app.Svc";
        stubGraph(
                List.of(classNode(cls, Map.of("visibility", "public")),
                        methodNode(cls, "b", "public", "void", List.of()),
                        methodNode(cls, "a", "public", "void", List.of())),
                List.of(edge("HAS_METHOD", cls, cls + "#b"),
                        edge("HAS_METHOD", cls, cls + "#a")));

        String first = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();
        String second = service.generateClassDiagram(PROJECT_ID, null).getMermaidSyntax();

        assertThat(first).isEqualTo(second);
        // +a(...) sorts before +b(...) in the TreeSet member ordering
        assertThat(first.indexOf("+a(")).isLessThan(first.indexOf("+b("));
    }
}
