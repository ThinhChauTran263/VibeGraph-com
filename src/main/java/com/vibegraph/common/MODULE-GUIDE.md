# Module: common

## Mục đích
Module chứa các thành phần dùng chung cho toàn bộ dự án: configuration, exception handling, DTOs, base models, và utilities.

## Cấu trúc

```
common/
├── config/
│   ├── Neo4jConfig.java          — Spring Data Neo4j connection config
│   ├── WebSocketConfig.java      — STOMP WebSocket setup (/ws/graph-updates)
│   ├── CorsConfig.java           — CORS policy (allow Vue dev server localhost:5173)
│   ├── McpServerConfig.java      — MCP Server bean registration
│   └── AsyncConfig.java          — Virtual threads executor (Java 21)
├── dto/
│   ├── request/
│   │   └── PaginationRequest.java — page, size, sort params
│   └── response/
│       ├── ApiResponse.java       — Wrapper {success, data, error, timestamp}
│       └── ErrorResponse.java     — Error detail {code, message, details}
├── exception/
│   ├── GlobalExceptionHandler.java — @ControllerAdvice, xử lý tất cả exceptions
│   ├── ProjectNotFoundException.java
│   ├── ParseException.java
│   ├── NodeNotFoundException.java
│   └── GithubImportException.java  — GitHub tarball import errors
├── node/
│   └── BaseNode.java             — Abstract @Node parent (id, createdAt, updatedAt)
└── util/
    ├── FileUtils.java            — File I/O helpers (scan directory, filter .java)
    ├── HashUtils.java            — SHA-256 checksum cho incremental cache
    └── JsonUtils.java            — JSON serialization helpers
```

## Yêu cầu chức năng

### Config
- [ ] `Neo4jConfig`: Cấu hình connection tới Neo4j (bolt://localhost:7687), authentication, transaction manager
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
- [ ] `ApiResponse<T>`: generic wrapper `{success: boolean, data: T, error: String, timestamp: Instant}`
- [ ] `ErrorResponse`: `{code: String, message: String, details: Map<String, Object>}`

### Base Node
- [ ] `BaseNode`: Abstract class với `@Id @GeneratedValue Long id`, `Instant createdAt`, `Instant updatedAt`
- [ ] Tất cả Neo4j node classes trong các module khác phải extends `BaseNode`

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
- Spring Data Neo4j
- Spring WebSocket
- Spring AI MCP Server
- Lombok
- Jakarta Validation

## Acceptance Criteria

- [ ] Tất cả config classes có `@Configuration` annotation
- [ ] GlobalExceptionHandler handle được: RuntimeException, ProjectNotFoundException, ParseException, NodeNotFoundException, GithubImportException
- [ ] ApiResponse wrapper được dùng nhất quán ở tất cả REST endpoints
- [ ] BaseNode có auto-generated ID
- [ ] FileUtils scan đúng, bỏ qua ignored paths
- [ ] HashUtils trả về consistent SHA-256 hex string
- [ ] Unit tests pass với coverage > 80% cho util classes
