# VibeGraph - Checklist file (Phạm vi 2 tháng)

Chú thích: `[x] xong`, `[s] scaffold/stub (file đã tạo nhưng chưa có logic — chỉ khung + TODO)`, `[~] đã hiện thực nhưng test còn @Disabled (chưa kiểm chứng)`, `[ ] cần làm`, `[defer] post-MVP`.

> **Đã đối soát ngày 2026-05-30 với cây repo thực tế.** Backend và frontend hoàn
> thiện hơn nhiều so với những bản nháp trước của checklist này ngụ ý. Tầng
> persistence giờ dùng **raw Neo4j Java Driver** (không dùng Spring Data Neo4j OGM):
> các entity `@Node` cũ dưới legacy graph-node package name và các interface `*NodeRepository` theo
> từng type đã bị xóa. Impl repository duy nhất là `graph/repository/impl/neo4j/`.

Checklist này bám theo bố cục repo hiện tại. Không tạo `vibegraph-core`, `vibegraph-server`, `vibegraph-cli`, hay `vibegraph-cli-npm` cho MVP 2 tháng.

## Backend - Module Spring Boot gốc

### `src/main/java/com/vibegraph/parser/node/`

- [x] `NodeData.java`
- [x] `EdgeData.java`
- [x] `ParseResult.java`

### `src/main/java/com/vibegraph/parser/visitor/`

- [x] `ClassVisitor.java`
- [x] `MethodVisitor.java`
- [x] `FieldVisitor.java`
- [x] `ImportVisitor.java` (`ImportVisitorTest` đã bật và pass)
- [x] `SpringAnnotationVisitor.java`

### `src/main/java/com/vibegraph/parser/service/`

- [x] `ParserService.java`
- [x] `impl/ParserServiceImpl.java` (`parseFile`/`parseProject` đã chạy; `parseFileWithCache` còn deferred Sprint 2 và ném `UnsupportedOperationException`)

> **Đã gỡ trong đợt refactor Sprint 3:** `SymbolResolverService.java` + impl và `CallGraphBuilderService.java` + impl (verify 2026-07-02: `glob **/{Symbol*,CallGraph*}.java` → 0 file). Symbol solving hiện nằm trong `ParserServiceImpl` + visitor; CALLS do `MethodVisitor` phát ra cho resolved in-project calls.

> **Empty placeholder stubs đã gỡ (2026-07-02):** `parser/service/CacheService.java` và `parser/util/TypeNames.java` (cả hai 0 byte, T84/T94 chưa implement) đã bị xóa. Nếu cần implement sau, tạo file mới.

### `src/main/java/com/vibegraph/common/config/`

- [x] `Neo4jMigrationRunner.java` (áp dụng `V1__init_schema.cypher` lúc khởi động; thay thế config Neo4j cũ đã bị xóa)
- [x] `WebSocketConfig.java` với `/ws/graph-updates` (endpoint/broker đã có; allowed origins/heartbeat còn TODO hardening)
- [x] `CorsConfig.java`
- [x] `McpServerConfig.java` (verify 2026-07-02: đã đăng ký 15 MCP tools qua Spring AI streamable HTTP tại `/mcp`)
- [x] `AsyncConfig.java` (`@EnableAsync` + bean `analysisExecutor` bounded thread pool đã có)

### `src/main/java/com/vibegraph/common/exception/`

- [x] `GlobalExceptionHandler.java`
- [x] `ProjectNotFoundException.java`
- [x] `ParseException.java`
- [x] `NodeNotFoundException.java`
- [x] `GithubImportException.java`
- [x] `FeatureNotImplementedException.java` (map 501 cho các feature/stub chưa mở)
- [x] `ArchiveImportException.java` (lỗi upload archive: unsupported type, oversize, unsafe entry, no Java files)

### `src/main/java/com/vibegraph/common/dto/`

- [x] `request/PaginationRequest.java`
- [x] `response/ApiResponse.java`
- [x] `response/ErrorResponse.java`

### `src/main/java/com/vibegraph/common/util/`

- [x] `FileUtils.java`
- [x] `HashUtils.java`
- [x] `JsonUtils.java`

### `src/main/java/com/vibegraph/graph/repository/`

- [x] `GraphRepository.java` (interface)
- [x] `impl/neo4j/Neo4jGraphRepository.java` (raw Driver + Cypher, impl duy nhất; `upsertProject`/`upsertNodes`/`upsertEdges`/`deleteFile`/`getFullGraph`/`searchNodes` đã có, `getNeighborhood`/`getImpact` còn Sprint 2 và ném `UnsupportedOperationException`)
- [x] `impl/neo4j/GraphSchema.java` (mapping label/edge-type + validate property)

> **Đã gỡ trong đợt refactor sang raw Driver:** `NodeRepository.java`,
> `ClassNodeRepository.java`, `MethodNodeRepository.java`, `FileNodeRepository.java`,
> và `ProjectNodeRepository.java`. Không còn dùng Spring Data Neo4j nên không có
> interface repository theo từng type.

### Graph node entity package removed

- [removed] Toàn bộ entity Neo4j `@Node` (`ProjectNode`, `PackageNode`, `FileNode`,
  `ClassNode`, `InterfaceNode`, `EnumNode`, `MethodNode`, `FieldNode`, `RouteNode`,
  `AnnotationNode`) đã bị xóa khỏi repo. Dữ liệu graph được mang bởi
  `NodeData`/`EdgeData` của parser và ghi xuống dưới dạng raw Cypher; "label" của node
  nằm trong `GraphSchema`, không phải dưới dạng class entity Java.

### `src/main/java/com/vibegraph/graph/controller/`

- [x] `ProjectController.java`
- [x] `GraphController.java`
- [x] `ImportController.java` (`POST /api/projects/import-archive` sync/async và `POST /api/projects/import-github` đã có)
- [removed] `ImpactController.java` — đã xóa (commit `331c613`); impact endpoint gộp vào `GraphController /graph/impact`

### `src/main/java/com/vibegraph/graph/service/`

- [x] `ProjectService.java` + [x] `impl/ProjectServiceImpl.java`
- [x] `AnalyzeService.java` + [x] `impl/AnalyzeServiceImpl.java`
- [x] `GraphService.java` + [x] `impl/GraphServiceImpl.java` (`GraphServiceTest` đã bật và pass cho `getFullGraph`/`searchNodes`)
- [x] `ArchiveImportService.java` + [x] `impl/ArchiveImportServiceImpl.java` (flow chính Sprint 2 — nhận upload ZIP/TAR/TAR.GZ, validate, materialize `.java`, register project, analyze sync/async)
- [removed] `ImpactService.java` + `impl/ImpactServiceImpl.java` — đã xóa (commit `331c613`); impact logic nằm trong `GraphServiceImpl.getImpactAnalysis`
- [x] `TarballImportService.java` (service contract có `importFromGithub`)
- [x] `impl/TarballImportServiceImpl.java` (GitHub public repo: parse URL, pre-flight, download tarball, extract qua archive pipeline, analyze async, broadcast status)

### `src/main/java/com/vibegraph/graph/importer/config/`

- [x] `ArchiveImportProperties.java` (config Sprint 2 cho `vibegraph.import.archive.max-size`, `workspace-root`, `ignored-paths`; đã tạo trong Task 1)

### `src/main/java/com/vibegraph/graph/importer/`

- [x] `ArchiveType.java` (ZIP/TAR/TAR_GZ/TGZ enum)
- [x] `ArchiveTypeDetector.java` (detect allow-list `.zip`, `.tar`, `.tar.gz`, `.tgz`)
- [x] `ArchiveExtractor.java` (extract safe `.java` entries, chống path traversal/absolute path/symlink/archive bomb theo giới hạn config)
- [x] `ArchiveExtractionResult.java` (extractedRoot, javaFiles, bytesWritten, warnings)

### `src/main/java/com/vibegraph/graph/websocket/`

- [x] `GraphUpdateController.java` (`broadcastStatus` publish `/topic/projects/{id}/status`; `broadcastFullUpdate` và `broadcastIncremental` publish `/topic/projects/{id}/updates`. Producer broadcast `INCREMENTAL` (added/removed) cho mọi thay đổi file CREATE/MODIFY/DELETE qua `FileChangeBroadcaster`)
- [removed] `WebSocketEventListener.java` — đã xóa (commit `331c613`)

### `src/main/java/com/vibegraph/graph/dto/`

- [x] `request/CreateProjectRequest.java`
- [x] `request/AnalyzeRequest.java`
- [x] `request/GithubImportRequest.java`
- [x] `request/GraphFilterRequest.java`
- [optional] `request/ArchiveImportRequest.java` (không bắt buộc cho MVP nếu controller nhận multipart trực tiếp bằng `@RequestParam`/`@RequestPart`; chỉ tạo nếu cần gom validation riêng)
- [x] `response/GraphDataResponse.java`
- [x] `response/NodeDto.java`
- [x] `response/EdgeDto.java`
- [x] `response/NodeDetailResponse.java`
- [x] `response/ProjectResponse.java`
- [x] `response/ImpactAnalysisResponse.java`
- [x] `response/NodeTypeEnum.java`
- [x] `response/EdgeTypeEnum.java`

### `src/main/java/com/vibegraph/diagram/`

- [x] `controller/DiagramController.java` (`GET /diagrams/usecase` + `/diagrams/class?package=...`, bọc ApiResponse, 404/409 handling)
- [x] `service/UseCaseDiagramService.java` + [x] `impl/UseCaseDiagramServiceImpl.java` (Mermaid `flowchart LR` từ Route/HANDLES_ROUTE)
- [x] `service/ClassDiagramService.java` + [x] `impl/ClassDiagramServiceImpl.java` (Mermaid `classDiagram`, visibility/stereotype, package filter)
- [x] `service/MermaidGeneratorService.java` + [x] `impl/MermaidGeneratorServiceImpl.java` (sanitizeId/escapeLabel an toàn cho Mermaid)
- [removed] `repository/DiagramQueryRepository.java` — đã xóa (commit `331c613`); diagram queries thực hiện qua `GraphService.getFullGraph`
- [x] `dto/response/DiagramResponse.java`
- [x] `dto/response/UseCaseResponse.java`

> `DiagramServiceTest` đã có nhưng còn `@Disabled`.

### `src/main/java/com/vibegraph/mcp/`

MCP surface đã mở rộng từ 4 tool kế hoạch lên **15 tool đã ship** (verify 2026-07-02 qua `mcp/MODULE-GUIDE.md` + `mcp/service/impl/` = 14 analyzer/impl).

- [x] `tool/ArchitectureTool.java` — @Tool `get_project_architecture`
- [x] `tool/ClassContextTool.java` — @Tool `get_class_context`
- [x] `tool/LayerPatternTool.java` — @Tool `get_layer_pattern`
- [x] `tool/ImpactAnalysisTool.java` — @Tool `get_impact_analysis` (3 profile)
- [x] `tool/TraceEndpointTool.java` — @Tool `trace_endpoint`
- [x] `tool/FindReferencesTool.java` — @Tool `find_references`
- [x] `tool/SourceFileTool.java` — @Tool `get_source_file`
- [x] `tool/SearchSourceTool.java` — @Tool `search_source`
- [x] `tool/MethodSourceTool.java` — @Tool `get_method_source`
- [x] `tool/MethodCpgTool.java` — @Tool `get_method_cpg_context`
- [x] `tool/FindRelatedTestsTool.java` — @Tool `find_related_tests`
- [x] `tool/SuggestTestPlanTool.java` — @Tool `suggest_test_plan`
- [x] `tool/PlanCodeChangeTool.java` — @Tool `plan_code_change`
- [x] `tool/ExplainFailureTool.java` — @Tool `explain_failure_path`
- [x] `tool/ProjectConventionsTool.java` — @Tool `get_project_conventions`
- [x] `common/config/McpServerConfig.java` — đăng ký toàn bộ 15 tool
- [x] `service/impl/*AnalyzerImpl.java` × 14 (mỗi analyzer phục vụ 1 hoặc nhiều tool)
- [x] `source/GraphView` + `SourceFileService` + `SourceGraphSupport` — shared helpers
- [x] response DTOs + request DTOs

> Test coverage: `SeniorMcpToolsTest`, `McpToolsTest`, `ProjectConventionsServiceTest`, `ProjectRestartSourceTest`, `ProjectServicePersistenceTest` đã enabled và pass.

### `src/main/java/com/vibegraph/watcher/`

- [x] `config/WatcherProperties.java`
- [x] `service/FileWatcherService.java` + [x] `impl/FileWatcherServiceImpl.java` (`startWatching`/`stopWatching`/`isWatching` đã có; recursive WatchService + debounce; bộ phát sự kiện thuần — phát CREATE/MODIFY/DELETE cho `FileChangeBroadcaster` xử lý cập nhật graph tăng dần)
- [x] `service/DebouncedEventHandler.java` (debounce theo key, collapse burst events)

> Realtime hiện tại: status topic + graph update topic + FE consumer + watcher lifecycle đã có test. CREATE/MODIFY/DELETE đều incremental qua `FileChangeBroadcaster` (re-parse file đổi bằng `parserService.parseFile` → upsert/prune → broadcast `INCREMENTAL`); FE patch Sigma tại chỗ. Đã test tay thêm/xóa file end-to-end.

### Resources

- [x] `src/main/resources/application.yaml`
- [x] `src/main/resources/application-dev.yaml`
- [x] `src/main/resources/application-prod.yaml`
- [x] `src/main/resources/application-docker.yaml`
- [x] `src/main/resources/db/migration/V1__init_schema.cypher`
- [x] schema được áp dụng lúc khởi động bởi `common/config/Neo4jMigrationRunner.java`

### Test backend

Đã bật và pass:

- [x] `architecture/StorageAbstractionTest.java`
- [x] `common/exception/ExceptionsTest.java`
- [x] `common/util/FileUtilsTest.java`, `HashUtilsTest.java`, `JsonUtilsTest.java`
- [x] `parser/visitor/ClassVisitorTest.java`, `MethodVisitorTest.java`, `FieldVisitorTest.java`, `ImportVisitorTest.java`, `SpringAnnotationVisitorTest.java`
- [x] `parser/service/ParserServiceTest.java`
- [x] `graph/repository/impl/neo4j/GraphSchemaTest.java`
- [~] `graph/repository/impl/neo4j/Neo4jGraphRepositoryIT.java` (enabled, nhưng tự skip nếu không có Neo4j reachable)
- [x] `graph/controller/ProjectControllerTest.java`, `GraphControllerTest.java`
- [x] `graph/service/GraphServiceTest.java`
- [x] `graph/service/impl/ProjectServiceImplTest.java`
- [x] `graph/importer/config/ArchiveImportPropertiesTest.java`

Đã có nhưng còn `@Disabled` hoặc chưa kiểm chứng end-to-end:

- [~] `graph/service/ImpactServiceTest.java`
- [~] `graph/service/TarballImportServiceTest.java`
- [x] `graph/service/impl/ArchiveImportServiceImplTest.java` (flow chính archive import)
- [x] `graph/importer/ArchiveExtractorTest.java` (ZIP/TAR/TAR.GZ safe extraction + unsafe entry rejection)
- [x] `graph/controller/ImportControllerTest.java` (multipart `import-archive` và `import-github` controller coverage)
- [~] `graph/controller/ImportControllerTest.java`
- [~] `diagram/service/DiagramServiceTest.java`
- [~] `mcp/tool/McpToolsTest.java`
- [x] `watcher/service/FileWatcherServiceTest.java` + watcher integration tests (realtime path covered; watcher là bộ phát sự kiện thuần, FileChangeBroadcaster xử lý CREATE/MODIFY/DELETE)
- [~] `VibeGraphApplicationTests.java` (nạp context)

Test fixture:

- [x] `src/test/resources/sample-project/` (4 file Java mẫu)

## Frontend - `vibegraph-web/src/`

### Views

- [s] `HomeView.vue` (khung trang đã có; project list/create còn `// TODO`)
- [x] `GraphView.vue`
- [s] `SettingsView.vue` (khung trang đã có; settings form còn `// TODO`)

### Components

- [s] `components/layout/HeaderBar.vue` (khung đã có; project selector/search/settings còn `// TODO`)
- [s] `components/layout/MainLayout.vue` (khung đã có; chưa render `SidePanel | GraphCanvas | NodeDetailPanel`)
- [s] `components/layout/SidePanel.vue` (khung đã có; tabs/panel content còn `// TODO`)
- [s] `components/layout/StatusBar.vue` (khung đã có; connection/stats còn `// TODO`)
- [x] `components/panels/FilterPanel.vue` (207 LOC, functional — toggle node/edge type + counts, wired vào GraphCanvas)
- [s] `components/panels/ExplorerPanel.vue` (khung đã có; search/folder tree còn `// TODO`)
- [s] `components/panels/FlowsPanel.vue` (khung đã có; flow list/highlight còn `// TODO`)
- [x] `components/panels/NodeDetailPanel.vue` (465 LOC — incoming/outgoing connections, properties, redaction, wired vào GraphCanvas)
- [s] `components/panels/LegendPanel.vue` (khung đã có; render legend còn `// TODO`)
- [x] `components/panels/FocusDepthControl.vue` (removed; hop-depth Focus Mode không còn dùng trong UI)
- [s] `components/panels/CodeInspector.vue` (khung đã có; code viewer/syntax highlight còn `// TODO`)
- [x] `components/graph/GraphCanvas.vue`
- [s] `components/graph/GraphControls.vue` (khung đã có; control buttons còn `// TODO`)
- [x] `components/graph/SearchBar.vue` (181 LOC — functional client-side search, suggestions dropdown, wired vào GraphCanvas)
- [x] `components/diagram/DiagramPanel.vue` (998 LOC — renders Use Case SVG UML 2.5 + Class Mermaid, package filter, loading/error/empty states)
- [deleted] `components/diagram/UseCaseDiagram.vue` (stub đã xóa 2026-07-02 — `DiagramPanel.vue` render trực tiếp)
- [deleted] `components/diagram/ClassDiagram.vue` (stub đã xóa 2026-07-02 — `DiagramPanel.vue` render trực tiếp)
- [deleted] `components/diagram/SequenceDiagram.vue` (stub đã xóa 2026-07-02 — sequence diagram defer post-MVP)
- [x] `components/projects/AddProjectArchive.vue` (flow chính — chọn file `.zip`/`.tar`/`.tar.gz`, bấm Add, hiển thị trạng thái/progress)
- [x] `components/projects/GitHubImportForm.vue` (351 LOC — form + validation + progress + tests)
- [x] `components/ui/Button.vue`
- [x] `components/ui/Input.vue`
- [s] `components/ui/Tabs.vue` (khung đã có; tab triggers/content slot còn `// TODO`)
- [x] `components/ui/Spinner.vue` (CSS spinner đã hoạt động; TODO SVG animation không chặn MVP)

### Composables, Stores, Types

- [x] `composables/useSigma.ts`
- [x] `composables/useWebSocket.ts` (STOMP-over-SockJS connect/subscribe/disconnect đã có; dùng cho status topic)
- [x] `composables/useGraphData.ts`
- [x] `composables/useDiagrams.ts` (150 LOC — loads UML Use Case + Class diagram from backend)
- [x] `composables/useFilters.ts` (25 LOC thin wrapper + `graphFilters.ts` logic)
- [x] `composables/useArchiveImport.ts` (flow chính — gọi `POST /api/projects/import-archive` multipart sync/async, theo dõi progress qua status topic)
- [x] `composables/useGitHubImport.ts` (225 LOC — import logic + error mapping + tests)
- [x] `lib/constants.ts`
- [x] `lib/api.ts`
- [x] `lib/graphAdapter.ts` (được bao phủ bởi `lib/__tests__/graphAdapter.spec.ts`)
- [s] `lib/focusMode.ts` (N-hop BFS/reducers còn `// TODO`)
- [deleted] `lib/colors.ts` (dead code, đã xóa 2026-07-02 — functions sống trong `graphAdapter.ts` + `lib/color.ts`)
- [x] `stores/graph.ts`
- [s] `stores/filter.ts` (state đã có; toggle actions còn `// TODO`)
- [s] `stores/project.ts` (state đã có; project management actions còn `// TODO`)
- [x] `types/graph.ts`

> ~~`stores/counter.ts` là scaffolding Vue còn sót lại và có thể gỡ bỏ.~~ ✅ Đã xóa 2026-07-02.

## DevOps - Gốc repo

- [x] `pom.xml` có dependency `commons-compress` (đã có; dùng cho TAR/TAR.GZ và có thể dùng chung với GitHub tarball import)
- [x] `docker-compose.yml` cho dev cục bộ
- [x] `Dockerfile` cho module backend gốc
- [x] `vibegraph-web/Dockerfile`
- [x] `vibegraph-web/nginx.conf`
- [x] `.env.example`
- [x] `.dockerignore`
- [~] `README.md`
- [x] `.github/workflows/backend.yml` + `.github/workflows/frontend.yml` (CI trên PR/push cho poc/develop/main)
- [~] file compose/nginx/certbot cho production (template đã có tài liệu trong `deployment-plan.md`; chưa commit thành file)
- [x] `docs/setup.md`
- [x] `docs/mcp-integration.md` (hướng dẫn MCP 15 tools cho AI coding assistants)

## Hoãn lại tường minh

- [defer] `vibegraph-core/`
- [defer] `vibegraph-server/`
- [defer] `vibegraph-cli/`
- [defer] `vibegraph-cli-npm/`
- [defer] package npm wrapper
- [defer] module `steering/`
- [defer] Use-case-context MCP tool (not in current backlog)
- [defer] Coding-rules MCP tool (not in current backlog)
- [defer] service sequence diagram và UI sequence diagram bản production
- [defer] billing và tài khoản người dùng đầy đủ (auth/rate-limit tối thiểu cho public demo được theo dõi trong Sprint 2 backlog)

## Hạng mục công việc tiếp theo

> **Cập nhật 2026-07-02:** Hầu hết các hạng mục trước đây đã hoàn thành hoặc bị loại bỏ:
> - ImpactController/ImpactService/WebSocketEventListener/DiagramQueryRepository/SymbolResolverService/CallGraphBuilderService đã XÓA
> - GitHub Import UI, FilterPanel, NodeDetailPanel, DiagramPanel, SearchBar, useDiagrams, useFilters đã SHIP
> - MCP surface mở rộng từ 4 lên 15 tool
> - CI workflow backend + frontend đã có
>
> Việc còn lại (Sprint 4 polish):
> 1. ~~Gỡ dead Vue stubs~~ ✅ Đã xóa ClassDiagram.vue, UseCaseDiagram.vue, SequenceDiagram.vue (2026-07-02)
> 2. ~~Gỡ `stores/counter.ts`~~ ✅ Đã xóa (2026-07-02)
> 3. ~~Gỡ empty Java stubs~~ ✅ Đã xóa CacheService.java, TypeNames.java, GraphStats.vue (2026-07-02)
> 4. OpenAPI/Swagger annotations (T78/T79)
> 5. Production Docker + domain + SSL (T99/T103-T105)
