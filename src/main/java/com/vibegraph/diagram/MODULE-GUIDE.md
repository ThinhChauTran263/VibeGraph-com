# Module: diagram

## Mục đích
Module generate sơ đồ kiến trúc từ knowledge graph. UML Use Case được suy luận thành mô hình
nghiệp vụ (actor/use case/relations) và render thành **SVG UML 2.5 chuẩn** ở frontend; API Map và
Class diagram dùng Mermaid.

> **Scope 2-month:** Use Case + Class diagram + API Map. Sequence diagram defer post-2-month (FR-06 deferred).

## Cấu trúc

```
diagram/
├── controller/
│   └── DiagramController.java        — GET /api/projects/{id}/diagrams/usecase?style=api-map|uml, /class
├── service/
│   ├── UseCaseDiagramService.java    — Interface: API Map + UML Use Case
│   ├── ClassDiagramService.java      — Interface: class diagram
│   ├── MermaidGeneratorService.java  — Interface: Mermaid syntax helpers
│   └── impl/
│       ├── UseCaseDiagramServiceImpl.java — orchestrator: API Map + pipeline UML
│       ├── UseCaseInferenceEngine.java    — suy luận model nghiệp vụ (1 goal/domain, không CRUD)
│       ├── BaLabelBeautifier.java         — đặt lại nhãn actor/use case theo văn phong BA
│       ├── SrsUseCaseEnricher.java        — phân rã domain (tracking) + include/extend + carrier actor
│       ├── UmlUseCaseRenderer.java        — render model → PlantUML + Mermaid fallback
│       ├── ClassDiagramServiceImpl.java
│       └── MermaidGeneratorServiceImpl.java
└── dto/
    └── response/
        ├── DiagramResponse.java      — {diagramType, mermaidSyntax, plantUmlSyntax?}
        └── UmlUseCaseResponse.java   — {systemName, actors[], useCases[], relations[], warnings[], mermaidSyntax, plantUmlSyntax}
```

> Lưu ý: render UML Use Case cuối cùng (stick-figure actor, ellipse, boundary, «include»/«extend»,
> generalization tam giác rỗng) do frontend vẽ SVG từ JSON model (`vibegraph-web/src/lib/umlUseCaseSvg.ts`).
> Backend cung cấp model + PlantUML/Mermaid; frontend không còn render UML bằng Mermaid flowchart.

## Yêu cầu chức năng

### DiagramController
- [ ] `GET /api/projects/{id}/diagrams/usecase`: Generate Use Case diagram
- [ ] `GET /api/projects/{id}/diagrams/class?package=...`: Generate Class diagram (filter by package)

### Use Case Diagram (FR-04)
- [ ] Detect **Actors** từ:
  - @RestController endpoints → Actor: "HTTP Client"
  - @Scheduled methods → Actor: "System/Scheduler"
  - @KafkaListener methods → Actor: "Message Queue"
  - @EventListener methods → Actor: "Event Bus"
- [ ] Detect **Use Cases** từ:
  - Public methods trong @RestController (mỗi endpoint = 1 use case)
  - Use case name = method name hoặc @Operation summary
- [ ] Detect **Relationships**:
  - `<<include>>`: Khi use case gọi shared service method
  - `<<extend>>`: Khi có optional flow (validation, notification)
- [ ] Output: model JSON (actors/useCases/relations) + PlantUML; frontend vẽ SVG UML 2.5 chuẩn.

### Class Diagram (FR-05)
- [ ] Show classes với:
  - Fields (visibility indicators: +public, -private, #protected)
  - Methods (visibility indicators)
  - Stereotypes: <<interface>>, <<abstract>>, <<enum>>
- [ ] Show relationships:
  - Inheritance: `--|>` (EXTENDS edge)
  - Implementation: `..|>` (IMPLEMENTS edge)
  - Association: `-->` (field type reference)
  - Dependency: `..>` (INJECTS edge từ @Autowired)
- [ ] Filter by package (chỉ show classes trong package đó)
- [ ] Output: Mermaid classDiagram syntax

### MermaidGeneratorService
- [ ] `generateUseCaseMermaid(UseCaseData)`: Convert to Mermaid flowchart
- [ ] `generateClassMermaid(ClassDiagramData)`: Convert to Mermaid classDiagram
- [ ] Escape special characters trong names
- [ ] Handle long names (truncate hoặc wrap)

## Mermaid Output Examples

### Use Case
```mermaid
flowchart LR
    HTTPClient((HTTP Client))
    Scheduler((Scheduler))

    HTTPClient --> CreateUser[Create User]
    HTTPClient --> GetUser[Get User]
    HTTPClient --> UpdateUser[Update User]

    Scheduler --> CleanupExpiredSessions[Cleanup Sessions]

    CreateUser -.->|<<include>>| ValidateInput[Validate Input]
    CreateUser -.->|<<extend>>| SendWelcomeEmail[Send Welcome Email]
```

### Class Diagram
```mermaid
classDiagram
    class UserController {
        <<RestController>>
        -UserService userService
        +createUser(CreateUserDto) UserResponse
        +getUser(Long) UserResponse
    }

    class UserService {
        <<interface>>
        +createUser(CreateUserDto) User
        +findById(Long) User
    }

    class UserServiceImpl {
        -UserRepository userRepository
        +createUser(CreateUserDto) User
        +findById(Long) User
    }

    UserController --> UserService : uses
    UserServiceImpl ..|> UserService : implements
    UserServiceImpl --> UserRepository : uses
```

## Quy tắc code

1. **Query optimization**: Dùng single Cypher query để fetch diagram data, không N+1
2. **Caching**: Cache generated diagrams (invalidate khi graph thay đổi)
3. **Size limits**: Limit số elements trong diagram (max 50 classes per package)
4. **Readable output**: Format Mermaid syntax với proper indentation

## Performance Targets

| Metric | Target |
|--------|--------|
| Use Case diagram generation | < 1 second |
| Class diagram (1 package) | < 500ms |

## Acceptance Criteria

- [ ] Use Case diagram detect actors từ Spring annotations
- [ ] Use Case diagram show <<include>>/<<extend>> relationships
- [ ] Class diagram show đúng visibility indicators
- [ ] Class diagram show inheritance/implementation relationships
- [ ] Mermaid syntax valid (render được trong Mermaid Live Editor)
- [ ] Auto-update diagrams khi graph thay đổi
- [ ] Unit tests với sample graph data

## Deferred (post-2-month)

- Sequence diagram (FR-06) — trace call chain từ entry point
