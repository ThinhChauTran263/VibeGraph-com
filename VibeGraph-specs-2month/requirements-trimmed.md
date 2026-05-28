# VibeGraph — Requirements (2-Month Scope)

**Version:** 2.0 (trimmed)
**Timeline:** 8 weeks
**Target:** Java projects, single-tenant local + simple SaaS demo

---

## Functional Requirements

### FR-01: Java Source Code Parsing — Critical
Parse `.java` files trong project directory, extract nodes/edges với JavaParser 3.26+ và Symbol Solver.

**Acceptance:**
- Parse 500 files < 30s
- Call graph accuracy > 85% (relax từ 90% của spec gốc)
- Detect Spring annotations: @Controller/@Service/@Repository/@Component/@RestController/@RequestMapping/@Autowired/@Scheduled/@KafkaListener
- Skip files lỗi syntax, log warning

### FR-02: Neo4j Storage — Critical
Lưu graph trong Neo4j 5.x Community. Schema theo `hungry-liskov/neo4j-schema.md`.

**Acceptance:**
- Mọi node có `projectId` property
- Service KHÔNG import Neo4j class — đi qua `GraphRepository` interface
- Incremental update (chỉ re-parse file đổi)
- Query 3-hop < 500ms

### FR-03: Force Graph Visualization — Critical
Sigma.js WebGL render giống ảnh GitNexus user gửi.

**Acceptance:**
- Node colors: Method (blue), File (red/orange), APIEndpoint (green), Class (yellow), DBModel (dark yellow), Interface (green), Constructor (cyan), Enum (purple), Record (orange)
- Edge colors: DEFINES (green), CALLS (red), IMPORTS (blue), EXTENDS (orange), IMPLEMENTS (pink), HAS_METHOD (cyan), HANDLES_ROUTE (dark green), STEP_IN_FLOW (purple)
- Filter panel: NODE TYPES + EDGE TYPES toggle với count
- Focus Mode: click node → highlight neighbors + dim unrelated (opacity 0.1-0.2)
- Focus Depth: All / 1 / 2 / 3 / 5 hops
- Explorer panel: file tree → click → focus node
- Node Detail panel: INCOMING + OUTGOING connections
- Legend panel bottom-left
- Controls panel top-right (Left/Right click, Scroll wheel)
- ForceAtlas2 layout in Web Worker
- 5000+ nodes 60fps

### FR-04: Use Case Diagram — High
Mermaid flowchart LR từ @RestController + @Scheduled + @KafkaListener.

### FR-05: Class Diagram — High
Mermaid classDiagram với fields/methods, EXTENDS, IMPLEMENTS, INJECTS.

### FR-07: Realtime Update — Critical
File save → graph update < 3s qua WebSocket STOMP.

### FR-08: Auto File Watcher — Critical
Java WatchService recursive, debounce 500ms, detect CREATE/MODIFY/DELETE `.java` files.

**Quan trọng:**
- Tạo file mới → tự thêm node lên graph (không cần lệnh)
- Sửa file → tự cập nhật
- Xóa file → tự xóa node + edge liên quan
- WebSocket push diff (added/changed/removed), frontend patch không full reload

### FR-09: REST API — Critical
Endpoints theo spec gốc + thêm 1 endpoint mới:

| Method | Path | Description |
|---|---|---|
| POST | /api/projects | Register project (local path) |
| **POST** | **/api/projects/import-github** | **(MỚI) Tarball stream từ GitHub API → parse in-memory** |
| POST | /api/projects/{id}/analyze | Trigger full re-analyze |
| GET | /api/projects/{id}/graph | Full graph |
| GET | /api/projects/{id}/graph/neighbors/{nodeId}?hops=N | N-hop neighborhood |
| GET | /api/projects/{id}/diagrams/usecase | Use Case Mermaid |
| GET | /api/projects/{id}/diagrams/class | Class Mermaid |
| GET | /api/projects/{id}/impact/{nodeId} | Blast radius |
| WS | /ws/graph-updates | Realtime push |

### FR-10: MCP Server — High (USP)
Spring AI MCP Boot Starter, Streamable HTTP transport, 4 tools (cắt từ 6):

1. `get_project_architecture(projectId)` — layers, patterns, naming
2. `get_class_context(projectId, className)` — related classes + diagram
3. `get_layer_pattern(projectId, layer)` — how to write in layer
4. `get_impact_analysis(projectId, target)` — blast radius

**Defer:** `get_usecase_context`, `get_coding_rules`

### FR-NEW: GitHub Import — High
User paste GitHub URL public → backend download tarball → parse in-memory → trả về projectId.

**Acceptance:**
- Hỗ trợ public repo (Phase 1, OAuth private repo defer)
- Download tarball từ GitHub API (1 request duy nhất, không clone)
- Parse `.java` files trực tiếp từ tar stream, KHÔNG ghi xuống disk
- Pre-flight check: HEAD request kiểm tra repo size, private flag, default branch
- Timeout 60s, repo > 100MB reject
- Yêu cầu `GITHUB_TOKEN` env var (rate limit 5000 req/giờ)
- Endpoint: `POST /api/projects/import-github` body `{"url": "https://github.com/abhigyanpatwari/GitNexus"}`
- Không cần scheduled cleanup job (không lưu file temp)

### FR-NEW-2: Local Watch CLI — High (Real-time Mode)
User cài CLI để watch local folder real-time, graph tự cập nhật khi tạo/sửa/xóa file.

**Acceptance:**
- CLI là npm package + Java JAR (yêu cầu Java 21 trên máy user)
- **Demo mode (2 tháng đầu):** Không cần login. CLI tự generate `sessionId` (hash từ folder path + timestamp), dùng làm projectId
- `vibegraph watch` — watch folder hiện tại, push diff lên server (không cần auth)
- **Auto-open browser** sau initial scan xong (mặc định), `--no-open` để tắt
- Tạo file .java mới → graph thêm node trong < 1s
- Xóa file .java → graph xóa node trong < 1s
- Sửa file .java → graph update trong < 3s
- **Privacy mức 1:** Chỉ gửi metadata (class/method/field/edges), KHÔNG gửi source code
- CLI parse local bằng JavaParser (reuse vibegraph-core)
- Giao tiếp server qua WebSocket
- **Header `X-API-Key`** vẫn gửi từ đầu (giá trị demo cố định) để dễ upgrade full auth sau này
- Post-2-month:
  - Thêm `vibegraph login --api-key=xxx`
  - Server validate API key trong filter (chỉ sửa 1 file)
  - Nâng cấp lên GraalVM native-image

---

## Non-Functional Requirements

| Metric | Target |
|---|---|
| Parse 500 files | < 30s |
| Incremental update | < 3s |
| Neo4j 3-hop query | < 500ms |
| Frontend 5000 nodes | 60fps |
| WebSocket latency | < 200ms |
| Deploy 1 lệnh | `docker compose up -d` |
| VPS minimum | 4GB RAM, 2 CPU |

## Out of Scope (defer post-2-month)

- Multi-language (TS/Python/Kotlin)
- Auth + Stripe + Free/Pro/Ultra plans
- GitHub OAuth + private repo
- IntelliJ Plugin
- CLI native-image (GraalVM) — 2 tháng đầu dùng JAR + Java 21
- Kuzu embedded
- Postgres+AGE SaaS
- Sequence diagram
- Steering file generation
- Pre-code hook templates
