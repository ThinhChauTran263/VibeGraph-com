# Module: `common/` — Shared Infrastructure

> **Vai trò:** Module nền tảng. Chứa config Spring, exception toàn cục, DTO dùng chung, util helper. Mọi module khác (`parser`, `graph`, `diagram`, `mcp`, `watcher`, `steering`) đều phụ thuộc vào module này.

> **Dev phụ trách:** Dev 2 (Backend) — setup ban đầu, sau đó cả team dùng chung.

> **Sprint:** Sprint 1 (Week 1-2). Phải xong trước khi các module khác bắt đầu.

---

## Mục tiêu module

1. Cấu hình Spring Boot (Neo4j, WebSocket, CORS, MCP, Async/Virtual Threads)
2. Chuẩn hóa response format toàn hệ thống (`ApiResponse<T>`)
3. Chuẩn hóa error handling (GlobalExceptionHandler)
4. Cung cấp helper file I/O, hash, JSON cho các module khác

---

## Cấu trúc thư mục

```
common/
├── config/         # Spring @Configuration classes
├── dto/
│   ├── request/    # Request DTOs dùng chung
│   └── response/   # Response wrappers
├── exception/      # Custom exceptions + global handler
├── node/           # Base class cho Neo4j @Node
└── util/           # Helper static methods
```

---

## Files & Specs

### `config/Neo4jConfig.java`
**Mục tiêu:** Kết nối Spring Data Neo4j với Neo4j 5.x.

**Phải làm:**
- Annotation `@Configuration`, `@EnableNeo4jRepositories(basePackages = "com.vibegraph")`, `@EnableTransactionManagement`
- Đọc URI/username/password từ `application.yaml` (`spring.neo4j.uri`, `spring.neo4j.authentication.*`)
- Tạo bean `Driver` (org.neo4j.driver.Driver) qua `GraphDatabase.driver(...)`
- Tạo bean `Neo4jTransactionManager`
- Cấu hình connection pool: `maxConnectionPoolSize=50`, `connectionAcquisitionTimeout=60s`

**Đạt được khi:**
- [ ] App start không lỗi với Neo4j chạy ở `bolt://localhost:7687`
- [ ] Repository `@Repository extends Neo4jRepository<...>` inject được
- [ ] Connection retry với exponential backoff khi Neo4j chưa sẵn sàng (NFR-03)

**Tham chiếu:** `architecture.md` §1, `requirements.md` FR-02

---

### `config/WebSocketConfig.java`
**Mục tiêu:** Setup STOMP over SockJS cho realtime push.

**Phải làm:**
- Implement `WebSocketMessageBrokerConfigurer`
- `@EnableWebSocketMessageBroker`
- `registerStompEndpoints`: endpoint `/ws/graph-updates`, allow origins từ CORS config, `.withSockJS()`
- `configureMessageBroker`: enable simple broker `/topic`, application destination prefix `/app`
- Topic chuẩn: `/topic/projects/{id}/updates`, `/topic/projects/{id}/status`

**Đạt được khi:**
- [ ] Frontend connect được qua `new SockJS('http://localhost:8080/ws/graph-updates')`
- [ ] Subscribe `/topic/projects/{id}/updates` nhận message
- [ ] Auto-reconnect khi disconnect (frontend xử lý)

**Tham chiếu:** `architecture.md` §5, `requirements.md` FR-07

---

### `config/CorsConfig.java`
**Mục tiêu:** Cho phép Vue dev server (`http://localhost:5173`) gọi API.

**Phải làm:**
- Implement `WebMvcConfigurer`, override `addCorsMappings`
- Allow origins: `http://localhost:5173`, `http://localhost:3000` (configurable qua `application.yaml`)
- Allow methods: `GET, POST, PUT, DELETE, OPTIONS`
- Allow headers: `*`
- `allowCredentials: true`

**Đạt được khi:**
- [ ] Frontend dev mode gọi API không bị CORS block
- [ ] Production build (Nginx serve) cũng OK

**Tham chiếu:** `architecture.md` §8

---

### `config/McpServerConfig.java`
**Mục tiêu:** Đăng ký MCP Server với Spring AI MCP Boot Starter.

**Phải làm:**
- `@Configuration`
- Bean `McpSyncServer` hoặc `McpAsyncServer` (theo Spring AI MCP API)
- ServerInfo: name=`VibeGraph`, version=`1.0.0`
- Register tất cả `@Tool` beans từ package `com.vibegraph.mcp.tool` (auto-detect qua `ToolCallbackProvider`)
- Transport: Streamable HTTP, endpoint `/mcp`

**Đạt được khi:**
- [ ] `curl http://localhost:8080/mcp` trả về MCP handshake
- [ ] Cursor/Kiro/Claude Code config `mcp.json` connect thành công
- [ ] List tools API trả về 6 tools (architecture, classContext, layerPattern, impact, useCase, codingRules)

**Tham chiếu:** `architecture.md` §9.1-9.2, `requirements.md` FR-10

---

### `config/AsyncConfig.java`
**Mục tiêu:** Bật Virtual Threads (Java 21) cho async tasks.

**Phải làm:**
- `@Configuration`, `@EnableAsync`
- Bean `Executor` (name=`taskExecutor`) dùng `Executors.newVirtualThreadPerTaskExecutor()`
- Bean cho file watcher executor (single thread, daemon)
- Bean cho parser parallel executor (virtual threads, dùng cho Sprint 3 task 1.17)

**Đạt được khi:**
- [ ] `@Async` method chạy trên virtual thread
- [ ] Parse 500 files parallel < 30s (NFR-01)

**Tham chiếu:** `requirements.md` NFR-01, `task-breakdown.md` 1.17

---

### `exception/GlobalExceptionHandler.java`
**Mục tiêu:** Bắt mọi exception, trả về `ErrorResponse` chuẩn.

**Phải làm:**
- `@RestControllerAdvice`
- Handler cho `ProjectNotFoundException` → 404
- Handler cho `NodeNotFoundException` → 404
- Handler cho `ParseException` → 422 (Unprocessable Entity)
- Handler cho `MethodArgumentNotValidException` → 400 (validation lỗi)
- Handler cho `Exception` (catch-all) → 500
- Log với SLF4J: ERROR cho 5xx, WARN cho 4xx
- KHÔNG leak stack trace ra response (security)

**Đạt được khi:**
- [ ] Mọi response lỗi có format `{success: false, error: {code, message, timestamp}}`
- [ ] 5xx errors có log chi tiết server-side
- [ ] Validation errors trả message dễ hiểu cho từng field

**Tham chiếu:** `requirements.md` NFR-03, common/security.md

---

### `exception/ProjectNotFoundException.java`
**Mục tiêu:** Throw khi lookup project không tồn tại.

**Phải làm:**
- Extends `RuntimeException`
- Constructor `(String projectId)` → message `"Project not found: {projectId}"`
- Constructor `(String projectId, Throwable cause)`

**Đạt được khi:**
- [ ] `ProjectService.findById` throw exception này khi không tìm thấy
- [ ] GlobalExceptionHandler bắt được, trả 404

---

### `exception/ParseException.java`
**Mục tiêu:** Wrap lỗi từ JavaParser.

**Phải làm:**
- Extends `RuntimeException`
- Field: `String filePath`, `int lineNumber` (nullable)
- Constructor `(String filePath, String message, Throwable cause)`
- Override `getMessage` → format `"Parse failed at {filePath}:{line} — {message}"`

**Đạt được khi:**
- [ ] ParserService throw khi gặp file không parse được
- [ ] GlobalExceptionHandler trả 422 + thông tin file

---

### `exception/NodeNotFoundException.java`
**Mục tiêu:** Throw khi query Neo4j không có node.

**Phải làm:**
- Extends `RuntimeException`
- Constructor `(String nodeType, String nodeId)` → `"{nodeType} not found: {nodeId}"`

**Đạt được khi:**
- [ ] GraphService trả 404 khi `nodeId` không tồn tại

---

### `dto/request/PaginationRequest.java`
**Mục tiêu:** Chuẩn hóa pagination params cho list APIs.

**Phải làm:**
- Record (Java 21) `PaginationRequest(int page, int size, String sortBy, String direction)`
- Default: `page=0, size=50, sortBy="name", direction="ASC"`
- Validation: `@Min(0) page`, `@Min(1) @Max(500) size`
- Method `toPageable()` → Spring Data `PageRequest`

**Đạt được khi:**
- [ ] Controller dùng `@Valid PaginationRequest` nhận query params
- [ ] Trang ngoài giới hạn → trả page rỗng, không crash

---

### `dto/response/ApiResponse.java`
**Mục tiêu:** Wrapper chuẩn cho mọi response thành công.

**Phải làm:**
- Generic record `ApiResponse<T>(boolean success, T data, ErrorResponse error, Instant timestamp)`
- Static `success(T data)` → `new ApiResponse<>(true, data, null, Instant.now())`
- Static `failure(ErrorResponse error)` → `new ApiResponse<>(false, null, error, Instant.now())`
- `@JsonInclude(NON_NULL)` để ẩn field null

**Đạt được khi:**
- [ ] Mọi controller return `ApiResponse<T>` (trừ MCP/WebSocket)
- [ ] Frontend parse được format `{success, data, error}` (xem `common/patterns.md`)

**Tham chiếu:** `~/.claude/rules/ecc/common/patterns.md` §API Response Format

---

### `dto/response/ErrorResponse.java`
**Mục tiêu:** Cấu trúc chi tiết khi lỗi.

**Phải làm:**
- Record `ErrorResponse(String code, String message, Map<String, String> details, Instant timestamp)`
- `code` enum-like: `PROJECT_NOT_FOUND`, `PARSE_FAILED`, `VALIDATION_ERROR`, `INTERNAL_ERROR`...
- `details`: ví dụ field-level errors `{"name": "must not be blank"}`

**Đạt được khi:**
- [ ] GlobalExceptionHandler tạo ErrorResponse đầy đủ
- [ ] Không bao giờ chứa stack trace hoặc credentials

---

### `node/BaseNode.java`
**Mục tiêu:** Abstract parent cho mọi Neo4j `@Node` model.

**Phải làm:**
- Abstract class với `@Id @GeneratedValue String id`
- Field chung: `String name`, `String fullName`, `String filePath`, `Integer lineNumber`, `Instant createdAt`, `Instant updatedAt`
- `@Version Long version` cho optimistic locking
- Lombok `@Getter @Setter @SuperBuilder` (hoặc plain getter/setter nếu không dùng Lombok)

**Đạt được khi:**
- [ ] `ClassNode`, `MethodNode`... extends BaseNode
- [ ] Repository query theo `fullName` index hoạt động (architecture.md §3)

**Tham chiếu:** `architecture.md` §3 Neo4j Schema

---

### `util/FileUtils.java`
**Mục tiêu:** Helper file I/O dùng nhiều trong parser + watcher.

**Phải làm:**
- Static method `List<Path> findJavaFiles(Path root)` → recursive, skip `target/`, `build/`, `.git/`, `node_modules/`
- Static method `String readFile(Path path)` → UTF-8 string
- Static method `boolean isJavaFile(Path path)` → ends with `.java`
- Static method `Path relativize(Path root, Path file)` → relative path string
- Configurable ignore patterns (đọc từ `WatcherProperties`)

**Đạt được khi:**
- [ ] Parser scan project 500 files trong < 1 giây
- [ ] Skip đúng các thư mục bị ignore (FR-08)

**Tham chiếu:** `requirements.md` FR-08

---

### `util/HashUtils.java`
**Mục tiêu:** SHA-256 cho incremental cache.

**Phải làm:**
- Static method `String sha256(byte[] content)` → hex string
- Static method `String sha256OfFile(Path path)` → đọc file + hash
- Dùng `MessageDigest.getInstance("SHA-256")`, KHÔNG dùng MD5

**Đạt được khi:**
- [ ] FileNode lưu được checksum
- [ ] AnalyzeService skip file có checksum trùng (task 5.10)

**Tham chiếu:** `architecture.md` §4.1, `task-breakdown.md` 5.10

---

### `util/JsonUtils.java`
**Mục tiêu:** Helper Jackson cho serialize/deserialize ad-hoc.

**Phải làm:**
- Static field `ObjectMapper MAPPER` (singleton, register `JavaTimeModule`)
- Static method `String toJson(Object obj)`
- Static method `<T> T fromJson(String json, Class<T> type)`
- Throw `RuntimeException` wrap `JsonProcessingException`

**Đạt được khi:**
- [ ] MCP tool serialize response JSON đúng format
- [ ] WebSocket payload serialize được Java records

---

## Definition of Done cho module common/

- [ ] Tất cả config beans load không lỗi (`mvn spring-boot:run`)
- [ ] Neo4j connection test pass (integration test)
- [ ] WebSocket endpoint accessible
- [ ] MCP `/mcp` endpoint trả về handshake
- [ ] CORS không block frontend dev server
- [ ] Coverage > 70% cho util classes (testing.md)
- [ ] Không có hardcoded secret (security.md)
- [ ] `package-info.java` có Javadoc cho mỗi sub-package

---

## Lưu ý cross-module

- Mọi module gọi `Neo4jRepository` phải qua bean được khai báo ở `Neo4jConfig`
- Mọi controller throw exception phải dùng custom exception ở `exception/`, KHÔNG throw `RuntimeException` trần
- Mọi controller return type phải là `ApiResponse<T>`, không return raw entity
- File I/O luôn dùng `FileUtils`, không dùng `Files.walk` trực tiếp (để áp dụng ignore patterns thống nhất)
