# Module: common

## Mục đích
Module chứa các thành phần dùng chung cho toàn bộ dự án: configuration, exception handling, DTOs, và utilities.

## Cấu trúc

```
common/
├── config/
│   ├── Neo4jMigrationRunner.java     — Áp dụng V1__init_schema.cypher lúc startup (raw Driver)
│   ├── WebSocketConfig.java          — STOMP WebSocket setup (/ws/graph-updates), origins từ CorsProperties
│   ├── CorsConfig.java               — CORS policy (origins từ CorsProperties), enable ApiKeyProperties
│   ├── CorsProperties.java           — vibegraph.cors.allowed-origins
│   ├── McpServerConfig.java          — MCP Server bean registration
│   ├── AsyncConfig.java              — Bounded analysisExecutor (AbortPolicy) cho import async
│   ├── AnalysisExecutorProperties.java — core/max pool size, queue capacity của analysisExecutor
│   ├── ApiKeyProperties.java         — vibegraph.api-key (shared secret; blank = tắt)
│   └── ApiKeyFilter.java             — Yêu cầu X-API-Key trên endpoint filesystem khi key được set
├── dto/
│   ├── request/
│   │   └── PaginationRequest.java    — page, size, sort params
│   └── response/
│       ├── ApiResponse.java          — Wrapper {success, data, error}
│       └── ErrorResponse.java        — Error detail {code, message, details}
├── exception/
│   ├── GlobalExceptionHandler.java   — @RestControllerAdvice, map exceptions → ApiResponse
│   ├── ProjectNotFoundException.java     (→ 404)
│   ├── NodeNotFoundException.java         (→ 404)
│   ├── ProjectNotAnalyzedException.java   (→ 409)
│   ├── GithubImportException.java         (→ 422)
│   ├── ArchiveImportException.java        (→ 400)
│   ├── FeatureNotImplementedException.java(→ 501)
│   ├── ServiceBusyException.java          (→ 503, executor bão hòa)
│   └── ParseException.java
└── util/
    ├── FileUtils.java            — File I/O helpers (scan directory, filter .java)
    ├── HashUtils.java            — SHA-256 checksum cho incremental cache
    └── JsonUtils.java            — JSON serialization helpers
```

## Yêu cầu chức năng

### Config
- [x] `Neo4jMigrationRunner`: Áp dụng `V1__init_schema.cypher` lúc khởi động qua raw Neo4j Java Driver (không dùng Spring Data Neo4j OGM)
- [ ] `WebSocketConfig`: Enable STOMP over SockJS, endpoint `/ws/graph-updates`, allowed origins
- [ ] `CorsConfig`: Allow origins `http://localhost:5173` (Vue dev), configurable qua application.yaml
- [ ] `McpServerConfig`: Register MCP Server beans, transport = Streamable HTTP tại `/mcp`
- [ ] `AsyncConfig`: Sử dụng Java 21 virtual threads cho async operations

### Exception Handling
- [ ] `GlobalExceptionHandler`: Catch tất cả exceptions, trả về `ApiResponse` format thống nhất
- [ ] HTTP 404 cho `ProjectNotFoundException`, `NodeNotFoundException`
- [ ] HTTP 400 cho `GithubImportException` (invalid URL, private repo, size exceeded)
- [ ] HTTP 500 cho `ParseException` với error details
- [ ] Log error context (không leak sensitive data ra response)

### DTOs
- [ ] `PaginationRequest`: fields `page` (default 0), `size` (default 20), `sort` (optional)
- [x] `ApiResponse<T>`: generic wrapper `{success: boolean, data: T, error: ErrorResponse}`
- [x] `ErrorResponse`: `{code: String, message: String, details: String}`

### Persistence (lưu ý kiến trúc)
- Module này KHÔNG chứa entity Neo4j — không dùng `@Node` / Spring Data Neo4j OGM và không có `BaseNode`.
- `common/config` chỉ giữ config dùng chung; riêng `Neo4jMigrationRunner` áp dụng `V1__init_schema.cypher` qua raw Neo4j Java Driver lúc khởi động.
- Toàn bộ persistence nằm ở `graph/repository/impl/neo4j` (raw Driver + Cypher).

### Utilities
- [ ] `FileUtils.scanJavaFiles(Path dir)`: Recursive scan, trả về `List<Path>`, bỏ qua build/, target/, .git/, node_modules/
- [ ] `FileUtils.isJavaFile(Path)`: Check extension .java
- [ ] `HashUtils.sha256(Path file)`: Tính SHA-256 checksum của file content
- [ ] `HashUtils.sha256(String content)`: Tính SHA-256 từ string
- [ ] `JsonUtils.toJson(Object)`: Serialize object to JSON string
- [ ] `JsonUtils.fromJson(String, Class<T>)`: Deserialize JSON to object

## Quy tắc code

1. **Immutability**: DTOs dùng Java record hoặc `@Value` (Lombok)
2. **Validation**: Dùng Jakarta Validation annotations (`@NotNull`, `@Min`, `@Max`)
3. **No business logic**: Module này KHÔNG chứa business logic, chỉ infrastructure
4. **Config externalized**: Tất cả config values đọc từ `application.yaml`, không hardcode

## Dependencies

- Spring Boot Starter Web
- Neo4j Java Driver (raw — không dùng Spring Data Neo4j OGM)
- Spring WebSocket
- Spring AI MCP Server
- Lombok
- Jakarta Validation

## Acceptance Criteria

- [ ] Tất cả config classes có `@Configuration` annotation
- [ ] GlobalExceptionHandler handle được: RuntimeException, ProjectNotFoundException, ParseException, NodeNotFoundException, GithubImportException
- [ ] ApiResponse wrapper được dùng nhất quán ở tất cả REST endpoints
- [ ] FileUtils scan đúng, bỏ qua ignored paths
- [ ] HashUtils trả về consistent SHA-256 hex string
- [ ] Unit tests pass với coverage > 80% cho util classes
