# Module: `mcp/` — MCP Server (AI Context Provider)

> **Vai trò:** Đây là **USP cốt lõi** của VibeGraph. Expose 6 MCP tools để AI coding tools (Cursor, Kiro, Claude Code, GitHub Copilot) tự động pull architecture context trước khi sinh code. Không có module này thì VibeGraph chỉ là tool visualization thường, không khác biệt với GitNexus/SonarQube.

> **Dev phụ trách:** Dev 5 (Integration) — phối hợp Dev 1 (parser data) + Dev 2 (graph queries).

> **Sprint:** Sprint 3 (Week 5-6).

> **Phụ thuộc:** `common/` (McpServerConfig), `graph/` (GraphService, ImpactService, AnalyzeService), `diagram/` (ClassDiagramService, UseCaseDiagramService).

---

## Mục tiêu module

1. Expose MCP server qua endpoint `/mcp` (Streamable HTTP transport)
2. 6 MCP tools đáp ứng FR-10:
   - `get_project_architecture` — layers, patterns, naming, rules
   - `get_class_context(className)` — class + dependencies + diagram
   - `get_layer_pattern(layer)` — Controller/Service/Repository pattern hiện tại
   - `get_impact_analysis(target)` — blast radius
   - `get_usecase_context(feature)` — use cases liên quan
   - `get_coding_rules` — DO/DON'T rules từ architecture thật
3. Compatible với Cursor, Kiro, Claude Code (MCP-enabled clients)
4. Response format AI-friendly: JSON với Markdown-embedded content
5. Latency < 1s mỗi tool call

---

## Cấu trúc thư mục

```
mcp/
├── controller/           # /mcp endpoint (nếu cần expose thủ công)
├── tool/                 # @Tool classes (1 class / 1 MCP tool)
├── service/              # Architecture analyzer (logic)
│   └── impl/
└── dto/
    ├── request/
    └── response/
```

---

## Files & Specs

### `controller/McpEndpointController.java`
**Mục tiêu:** Expose endpoint `/mcp` nếu Spring AI MCP Boot Starter chưa tự register.

**Phải làm:**
- Kiểm tra Spring AI MCP Boot Starter đã auto-register `/mcp` chưa
- Nếu **đã có** → file này có thể là **no-op / xóa**, MCP starter handle hết
- Nếu **chưa có** → tạo controller minimal forward request tới `McpServer` bean
- Nên ưu tiên dùng auto-config thay vì viết tay

**Đạt được khi:**
- [ ] `curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"initialize",...}'` trả MCP handshake
- [ ] Cursor/Kiro/Claude Code config thành công với `transport: "streamable-http"`, `url: "http://localhost:8080/mcp"`

**Tham chiếu:** `architecture.md` §9.1, §9.4; `requirements.md` FR-10

---

### MCP Tools (`tool/`)

> Note: Mỗi tool class register với MCP server qua `McpServerConfig` (đã khai báo ở `common/`). Spring AI MCP Boot Starter dùng `@Tool` annotation hoặc `ToolCallbackProvider` bean — chọn 1 nhất quán.

#### `tool/ArchitectureTool.java`
**MCP tool name:** `get_project_architecture`

**Mục tiêu:** Trả về full architecture context — đây là tool quan trọng nhất, AI gọi đầu tiên trước khi sinh code.

**Phải làm:**
- `@Component`, inject `McpToolService`, `ArchitectureAnalyzer`, `ClassDiagramService`
- `@Tool(name = "get_project_architecture", description = "Get the full architecture context of the Java project including layers, patterns, naming conventions. Call this BEFORE writing any new code to ensure correct architecture alignment.")`
- Param: `@ToolParam("projectId") String projectId`
- Logic:
  1. Lấy `ProjectNode` từ projectId (404 nếu không có)
  2. Detect layers: count `@Controller`, `@Service`, `@Repository` classes → trả layer list
  3. Detect patterns: dependency injection style (constructor / field), error handling (`@ControllerAdvice`), validation (`@Valid`)
  4. Detect naming: regex pattern từ existing classes (`*Controller`, `*Service`, `*Dto`)
  5. Generate top-level class diagram (qua `ClassDiagramService`)
  6. Detect warnings (god class, missing tests, anti-pattern)
  7. Return `ArchitectureContextResponse` với markdown-embedded fields

**Đạt được khi:**
- [ ] Response < 1s
- [ ] Output bao gồm: layers, packages, namingConventions, patterns, classDiagram (Mermaid), warnings, doNot rules
- [ ] AI tool gọi → nhận đầy đủ context (verify thủ công với Cursor)
- [ ] Response format JSON nhưng `description`, `pattern`, `rules` là Markdown-friendly để AI render đẹp

**Tham chiếu:** `architecture.md` §9.3, `requirements.md` FR-10

---

#### `tool/ClassContextTool.java`
**MCP tool name:** `get_class_context`

**Mục tiêu:** AI hỏi "tôi muốn sửa UserService, hãy cho tôi context" → trả về class + related classes + class diagram.

**Phải làm:**
- `@Tool(name = "get_class_context", description = "Get full context of a specific class including methods, fields, dependencies, callers, and a focused class diagram. Call this when modifying or extending an existing class.")`
- Param: `@ToolParam("projectId") String projectId`, `@ToolParam("className") String className`
- Logic:
  1. Find class by `fullName` (hoặc partial match nếu chỉ có simple name)
  2. Lấy methods (HAS_METHOD), fields (HAS_FIELD), dependencies (INJECTS, IMPORTS)
  3. Tìm callers (impact analysis upstream depth=1)
  4. Generate focused class diagram cho class này + immediate dependencies
  5. Suggest patterns: nếu class là @Service → khuyên follow service pattern hiện có

**Đạt được khi:**
- [ ] Resolve `UserService` đúng dù user pass `com.example.service.UserService` hoặc `UserService`
- [ ] Trả về class diagram chỉ chứa class đó + related (max 10 nodes, không full project)
- [ ] Liệt kê callers giúp AI biết sửa method nào sẽ ảnh hưởng

**Tham chiếu:** `architecture.md` §9.3 example, `requirements.md` FR-10

---

#### `tool/LayerPatternTool.java`
**MCP tool name:** `get_layer_pattern`

**Mục tiêu:** AI hỏi "tôi muốn tạo Controller mới, project này viết Controller như thế nào?" → trả về template + examples.

**Phải làm:**
- `@Tool(name = "get_layer_pattern", description = "Get the existing pattern for a specific Spring layer (CONTROLLER/SERVICE/REPOSITORY/COMPONENT). Returns code patterns, naming, conventions extracted from real classes in this project.")`
- Param: `@ToolParam("projectId") String projectId`, `@ToolParam("layer") String layer`
- Logic:
  1. Validate layer ∈ {CONTROLLER, SERVICE, REPOSITORY, COMPONENT, CONFIG}
  2. Lấy 3-5 example classes của layer đó (chọn class có nhiều methods nhất → đại diện tốt)
  3. Extract pattern: annotation thường dùng, field injection style, method signature
  4. Build pattern description (Markdown):
     - Section "Naming convention": từ regex
     - Section "Dependencies": layer này thường inject gì
     - Section "Common methods": top method signatures
     - Section "Example": 1-2 class snippet (text-only, không full source)

**Đạt được khi:**
- [ ] Trả về layer pattern thực tế (không hardcode boilerplate)
- [ ] AI dùng được để generate code đúng convention
- [ ] Layer không có class nào → response `{found: false, hint: "Layer X has no classes yet"}`

**Tham chiếu:** `architecture.md` §9.3, `requirements.md` FR-10

---

#### `tool/ImpactAnalysisTool.java`
**MCP tool name:** `get_impact_analysis`

**Mục tiêu:** AI hỏi "nếu tôi đổi method `UserService.save()`, sẽ ảnh hưởng gì?" → trả về blast radius.

**Phải làm:**
- `@Tool(name = "get_impact_analysis", description = "Analyze blast radius of changing a target symbol. Returns affected callers, routes, and risk level. Call this BEFORE refactoring or modifying a method to avoid unintended breakage.")`
- Param: `@ToolParam("projectId") String projectId`, `@ToolParam("target") String target`, `@ToolParam("maxDepth") Integer maxDepth` (default 3)
- Logic:
  1. Resolve `target` qua `MethodNodeRepository.findByFullName` hoặc `ClassNodeRepository`
  2. Gọi `ImpactService.analyzeImpact(nodeId, maxDepth)`
  3. Format response với risk level (LOW/MEDIUM/HIGH/CRITICAL)
  4. Group affected items theo depth + theo Spring layer

**Đạt được khi:**
- [ ] Response chứa: risk, summary text, byDepth (d=1, d=2, d=3), affectedRoutes
- [ ] Risk CRITICAL → response có `warning: "Stop! High-risk change. Review d=1 callers first."`
- [ ] Latency < 1s cho graph 5000 nodes

**Tham chiếu:** `architecture.md` §9.3, `requirements.md` FR-10, CLAUDE.md (impact risk levels)

---

#### `tool/UseCaseContextTool.java`
**MCP tool name:** `get_usecase_context`

**Mục tiêu:** AI hỏi "tôi đang làm feature payment, có use case gì liên quan?" → trả về use cases + related controllers + diagram.

**Phải làm:**
- `@Tool(name = "get_usecase_context", description = "Get use cases related to a feature/keyword. Returns matching routes, controllers, and a focused use case diagram.")`
- Param: `@ToolParam("projectId") String projectId`, `@ToolParam("feature") String feature`
- Logic:
  1. Search routes có path/handler chứa keyword `feature` (case-insensitive)
  2. Lấy related controllers, services, methods (1-hop)
  3. Generate focused use case diagram chỉ với routes + actors liên quan
  4. Liệt kê suggested next actions: "If adding new endpoint to this feature, follow X pattern"

**Đạt được khi:**
- [ ] Search "user" → trả về `/api/users/*` routes + UserController
- [ ] Trả về sub-Mermaid use case (filtered)
- [ ] Empty result → suggest broader keywords

**Tham chiếu:** `requirements.md` FR-10, FR-04

---

#### `tool/CodingRulesTool.java`
**MCP tool name:** `get_coding_rules`

**Mục tiêu:** AI hỏi "DO/DON'T của project này là gì?" → trả về rules từ architecture thật, không phải generic best practices.

**Phải làm:**
- `@Tool(name = "get_coding_rules", description = "Get DO/DON'T rules derived from the actual architecture of this project. Use these rules to write code that fits the existing codebase.")`
- Param: `@ToolParam("projectId") String projectId`
- Logic:
  1. Detect rules từ patterns:
     - Nếu mọi Controller dùng constructor injection → "DO: Use constructor injection"
     - Nếu có @ControllerAdvice → "DO: Throw custom exceptions, GlobalExceptionHandler will handle"
     - Nếu có @Valid trên @RequestBody → "DO: Validate all request DTOs with @Valid"
     - Nếu Repository chỉ extend JpaRepository → "DON'T: Write raw SQL in Repository"
  2. Detect anti-patterns hiện có (warnings):
     - "X classes have field injection — migrate to constructor injection"
     - "Y controllers don't use @Valid"
  3. Output: list of rules với category (DO/DONT/WARNING) + reasoning

**Đạt được khi:**
- [ ] Rules dựa trên thực tế project, không hardcode generic
- [ ] Output Markdown table dễ đọc cho AI
- [ ] Khi project mới (chưa có pattern rõ ràng) → trả default rules + note "patterns chưa rõ"

**Tham chiếu:** `architecture.md` §9.5 (steering file content), `requirements.md` FR-10, FR-12

---

### Services (`service/`)

#### `service/McpToolService.java` (interface)
**Mục tiêu:** Shared facade cho tool classes — tránh tool nào cũng inject 5 service.

**Phải có:**
- `Optional<ClassNode> resolveClassByName(String projectId, String name)` — match fullName hoặc simpleName
- `List<ClassNode> findClassesByLayer(String projectId, String layer)`
- `Map<String, Long> countByLayer(String projectId)`
- `String formatAsMarkdownTable(List<?> data, ...)`

#### `service/impl/McpToolServiceImpl.java`
**Phải làm:**
- `@Service`, inject GraphService, ProjectService
- Method resolution: thử exact `fullName` match trước, fallback `endsWith(.simpleName)`, throw nếu ambiguous (>1 match)

**Đạt được khi:**
- [ ] Resolve `UserService` cho project có 1 UserService → OK
- [ ] Có 2 UserService trong 2 package → trả error message yêu cầu fullName
- [ ] Coverage > 70%

---

#### `service/ArchitectureAnalyzer.java` (interface)
**Phải có:**
- `List<LayerInfo> detectLayers(String projectId)` — return layer + count + example classes
- `List<PatternInfo> detectPatterns(String projectId)` — DI style, error handling, validation, pagination
- `NamingConventions detectNaming(String projectId)` — regex từ existing class names
- `List<Warning> detectWarnings(String projectId)` — god class, missing tests, anti-pattern
- `List<Rule> generateRules(String projectId)` — DO/DON'T

#### `service/impl/ArchitectureAnalyzerImpl.java`
**Mục tiêu:** Logic phân tích architecture — đây là **brain** của MCP module.

**Phải làm:**
- `@Service`, inject Neo4jClient hoặc GraphService
- Detect layers: query `MATCH (c:Class) WHERE c.springLayer IS NOT NULL RETURN c.springLayer, count(*)`
- Detect DI style: scan `INJECTS` edges → check `via` property (constructor vs field) → tỉ lệ %
- Detect naming: regex từ class names — pattern phổ biến nhất (ví dụ `(.+)Controller$`, `(.+)Service$`)
- Detect god class: class có > 20 methods → warning
- Detect missing tests: file có class không có file test tương ứng → warning
- Detect anti-pattern: Controller gọi Repository trực tiếp (skip Service) → warning

**Đạt được khi:**
- [ ] Demo Spring Boot project: detect đúng 3 layer (Controller, Service, Repository)
- [ ] Detect đúng DI style chính (constructor vs field)
- [ ] Naming pattern khớp 80%+ existing classes
- [ ] Coverage > 70%

**Tham chiếu:** `architecture.md` §9.5, `requirements.md` FR-10, FR-12

---

### DTOs

#### `dto/request/ClassContextRequest.java`
- Record `{String projectId, String className}` — used internally, không expose REST (MCP dùng Tool params)

#### `dto/request/LayerPatternRequest.java`
- Record `{String projectId, String layer}`

#### `dto/response/ArchitectureContextResponse.java`
- Record với fields:
  - `String projectName`
  - `List<LayerInfo> layers`
  - `List<String> packages`
  - `NamingConventions namingConventions`
  - `List<PatternInfo> patterns`
  - `String classDiagramMermaid`
  - `List<Warning> warnings`
  - `List<String> doRules`
  - `List<String> dontRules`
- `LayerInfo{String layer, Long count, List<String> exampleClasses}`
- `PatternInfo{String name, String description, String example}`
- `NamingConventions{String controllers, String services, String repositories, String dtos, String entities}`

**Đạt được khi:**
- [ ] AI parse được JSON, render Markdown đẹp
- [ ] `classDiagramMermaid` là valid Mermaid string

#### `dto/response/ClassContextResponse.java`
- Record `{NodeDto classNode, List<NodeDto> methods, List<NodeDto> fields, List<NodeDto> dependencies, List<NodeDto> callers, String focusedClassDiagram}`

#### `dto/response/LayerPatternResponse.java`
- Record `{String layer, List<NodeDto> exampleClasses, String namingPattern, String diSummary, List<String> commonAnnotations, String exampleSnippet}`

#### `dto/response/CodingRulesResponse.java`
- Record `{List<Rule> rules, List<Warning> warnings}`
- `Rule{String category, String text, String reasoning}` — category ∈ {DO, DONT}

---

## Definition of Done cho module mcp/

- [ ] 6 MCP tools đều callable từ Cursor / Kiro / Claude Code (verify với mỗi client tối thiểu 1 lần)
- [ ] Response format đúng MCP protocol (JSON-RPC 2.0)
- [ ] Latency < 1s cho mọi tool
- [ ] `mcp.json` config template được tạo cho cả 3 clients (xem task 5.16)
- [ ] Coverage > 70% (tập trung vào ArchitectureAnalyzerImpl)
- [ ] Integration test: spin up Spring Boot + sample project + call tool → verify response shape
- [ ] Documentation: hướng dẫn config MCP cho từng client

---

## Lưu ý cross-module

- KHÔNG duplicate logic — luôn delegate xuống `graph/` (GraphService, ImpactService) và `diagram/` (ClassDiagramService)
- MCP tools phải **stateless** — không cache state per-call (Spring AI MCP có thể reuse instance)
- Response phải JSON-serializable (test với Jackson)
- AI tools đọc `description` của `@Tool` annotation để quyết định khi nào gọi — viết description rõ ràng, kèm "WHEN to call this"
- KHÔNG expose Neo4j entity — luôn convert sang DTO trong response
- Cùng `ImpactService` được dùng bởi cả `ImpactController` (REST) và `ImpactAnalysisTool` (MCP) — đảm bảo logic identical
- Steering module sẽ reuse `ArchitectureAnalyzer` để generate rules cho `.kiro/steering/*.md`, `.cursorrules`, `CLAUDE.md` — không tách logic
