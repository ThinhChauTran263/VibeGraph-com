# Module: mcp

## Mục đích
MCP (Model Context Protocol) Server cung cấp tools để AI coding assistants (Cursor, Kiro, Claude Code) đọc project context trước khi generate code, đảm bảo code AI sinh ra đúng architecture.

> **Scope 2-month:** 4 tools cốt lõi. `get_usecase_context` và `get_coding_rules` defer post-2-month.

## Cấu trúc

```
mcp/
├── controller/
│   └── McpEndpointController.java    — /mcp endpoint (Streamable HTTP)
├── tool/                             — @Tool classes (exposed to AI)
│   ├── ArchitectureTool.java         — get_project_architecture
│   ├── ClassContextTool.java         — get_class_context
│   ├── LayerPatternTool.java         — get_layer_pattern
│   └── ImpactAnalysisTool.java       — get_impact_analysis
├── service/
│   ├── McpToolService.java           — Interface: orchestrate tool calls
│   ├── ArchitectureAnalyzer.java     — Interface: detect patterns from graph
│   └── impl/
│       ├── McpToolServiceImpl.java
│       └── ArchitectureAnalyzerImpl.java
└── dto/
    ├── request/
    │   ├── ClassContextRequest.java   — {className, projectId}
    │   └── LayerPatternRequest.java   — {layer: CONTROLLER|SERVICE|REPOSITORY}
    └── response/
        ├── ArchitectureContextResponse.java — {layers[], packages[], patterns[], rules[]}
        ├── ClassContextResponse.java        — {class, related[], methods[], diagram}
        └── LayerPatternResponse.java        — {layer, conventions[], examples[]}
```

## Yêu cầu chức năng

### MCP Tools (FR-10)

#### `get_project_architecture`
- [ ] Input: `projectId`
- [ ] Output: ArchitectureContextResponse
- [ ] Detect layers từ Spring annotations: Controller → Service → Repository
- [ ] List packages và mục đích của từng package
- [ ] Detect patterns: DI style, validation, error handling, pagination
- [ ] Detect naming conventions: {Entity}Controller, {Entity}Service, etc.
- [ ] Generate class diagram (Mermaid) overview
- [ ] List warnings: large classes, missing tests, anti-patterns

#### `get_class_context`
- [ ] Input: `className` (e.g., "UserService"), `projectId`
- [ ] Output: ClassContextResponse
- [ ] Return:
  - Class info (fields, methods, annotations)
  - Related classes (callers, callees, dependencies)
  - Class diagram fragment (Mermaid)
  - Layer assignment

#### `get_layer_pattern`
- [ ] Input: `layer` (CONTROLLER | SERVICE | REPOSITORY | COMPONENT), `projectId`
- [ ] Output: LayerPatternResponse
- [ ] Return:
  - Naming convention cho layer này
  - Annotations bắt buộc (@RestController, @Service, etc.)
  - Conventions: constructor injection, validation, error handling
  - Code examples từ existing classes trong project
  - Anti-patterns to avoid

#### `get_impact_analysis`
- [ ] Input: `projectId`, `target` (class hoặc method fullName)
- [ ] Output: Impact analysis (delegate sang `graph.ImpactService`)
- [ ] Return: affected nodes by depth (1-5 hops), risk level, recommendations

### MCP Endpoint (FR-10)
- [ ] Transport: **Streamable HTTP** (chuẩn MCP mới nhất)
- [ ] Endpoint: `POST /mcp`
- [ ] Compatible với: Cursor, Kiro, Claude Code, GitHub Copilot
- [ ] Server info: name "VibeGraph", version từ pom.xml
- [ ] Configure qua Spring AI MCP Boot Starter

### Architecture Analyzer
- [ ] `detectLayers(projectId)`: Phân tích Spring annotations → layers
- [ ] `detectPatterns(projectId)`:
  - Constructor vs field injection (count tỷ lệ)
  - Validation style (@Valid usage)
  - Error handling pattern (GlobalExceptionHandler presence)
  - Transaction usage
- [ ] `detectNamingConventions(projectId)`: Phân tích tên classes
- [ ] `detectWarnings(projectId)`:
  - Classes > 500 LOC
  - Methods > 50 LOC
  - Missing tests (no corresponding test class)
  - Cyclic dependencies

## Configuration

### MCP Client Config Example
```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "http://localhost:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

### Spring AI MCP Setup
```java
@Bean
public ToolCallbackProvider mcpTools(
    ArchitectureTool architectureTool,
    ClassContextTool classContextTool,
    LayerPatternTool layerPatternTool,
    ImpactAnalysisTool impactAnalysisTool) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(architectureTool, classContextTool, layerPatternTool, impactAnalysisTool)
        .build();
}
```

## Quy tắc code

1. **Stateless tools**: MCP tools không lưu state, mỗi call độc lập
2. **Markdown-friendly**: Response chứa Markdown để AI render dễ đọc
3. **Latency target**: Mỗi tool call < 1 second
4. **Error handling**: Tool errors trả về structured error, không throw exception
5. **Caching**: Cache architecture analysis (invalidate on graph change)

## Performance Targets

| Metric | Target |
|--------|--------|
| MCP tool call latency | < 1 second |
| Architecture analysis | < 2 seconds |
| Class context lookup | < 500ms |

## Acceptance Criteria

- [ ] MCP endpoint accessible tại `http://localhost:8080/mcp`
- [ ] Cursor/Kiro/Claude Code có thể connect và list tools
- [ ] Tất cả **4 tools** hoạt động và return valid response
- [ ] Architecture detection chính xác cho Spring Boot project
- [ ] Response format JSON với Markdown content
- [ ] Integration test với MCP client

## Deferred (post-2-month)

- `get_usecase_context` — context theo use case/feature name
- `get_coding_rules` — generate DO/DON'T rules dynamic
