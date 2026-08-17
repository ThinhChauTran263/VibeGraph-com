# Component/class changes: old artifact -> verified current artifact

## Component and deployment mapping

| Old artifact/page | Current artifact/section | Evidence-backed change | Evidence |
| --- | --- | --- | --- |
| Old generic deployment page | `plantuml_erd_component_class.md` PART 3 and `4.1.Component_Deployment Diagram` | Makes the checked compose topology explicit: nginx frontend, Spring Boot backend, PostgreSQL 16.11, Neo4j 5.26, loopback database ports, backend healthy dependencies, frontend healthy dependency, and writable `./projects -> /app/projects` plus `./uploads -> /app/uploads` mounts. | `docker-compose.yml:2-69`; `docker-compose.yml:151-179`; backend/frontend Dockerfiles. |
| Old direct parser-to-driver arrow | PART 3 repository data flow | Corrects the abstraction boundary: parser/analysis and graph/MCP consumers use `GraphRepository`; only `Neo4jGraphRepository` owns the raw Neo4j `Driver`. | `src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java:29-112`; `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java:60`; `src/main/java/com/vibegraph/graph/repository/GraphRepository.java`. |
| Old narrow optional "telemetry DB" label | PART 3 optional storage cloud | Renames the optional integration to realtime/high-volume PostgreSQL-compatible storage. Its migration covers feedback, project runtime state, request/security events, announcements and notifications, not telemetry alone. | `src/main/resources/application.yaml:112-155`; `src/main/resources/db/supabase/V1__init_realtime_storage.sql:10-124`. |
| Old generic protocol arrows | PART 3 named components | Names REST/JSON, SockJS/STOMP `/ws/graph-updates`, MCP streamable HTTP `/mcp`, auth/control-plane PostgreSQL data and Neo4j graph data without claiming direct driver access from every component. | `src/main/java/com/vibegraph/common/config/WebSocketConfig.java:51-61`; `src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md:5-20`; `src/main/resources/application.yaml:17-78`. |

## Class-view mapping

| Old/stale representation | Corrected current representation | Evidence |
| --- | --- | --- |
| `ParseResult <<record>>` | `ParseResult` is a Lombok class; `NodeData` and `EdgeData` remain records | `src/main/java/com/vibegraph/parser/node/ParseResult.java:13-25`; current GitNexus symbol kind. |
| `FileChangeBroadcaster -> FileWatcherServiceImpl` | Broadcaster depends on `FileWatcherService`; `FileWatcherServiceImpl` realizes that interface | `src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:22-46`; `src/main/java/com/vibegraph/watcher/service/FileWatcherService.java:18`; `src/main/java/com/vibegraph/watcher/service/impl/FileWatcherServiceImpl.java:55`. |
| Only `Neo4jGraphRepository` shown as `GraphRepository` implementation | Adds `CachingGraphRepository` realization and retains delegation to `Neo4jGraphRepository`; marks the cache as the primary decorator in the diagrams.net note | `src/main/java/com/vibegraph/graph/repository/impl/CachingGraphRepository.java:45-54`; `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java:57-60`. |
| `MeteredToolCallback -> GraphRepository` | Removes the nonexistent dependency; shows `MeteredToolCallback` implementing/delegating `ToolCallback` | `src/main/java/com/vibegraph/mcp/MeteredToolCallback.java:22-72`. |
| `User` object aggregations to `ApiKey`/`ProjectOwnership` | Labels these as logical database-FK relationships through scalar IDs, not JPA `@OneToMany` object fields | `src/main/java/com/vibegraph/auth/domain/User.java:37`; `src/main/java/com/vibegraph/auth/domain/ApiKey.java:41`; `src/main/java/com/vibegraph/auth/domain/ProjectOwnership.java:39`. |
| Admin audit/security classes claimed but absent from compact view | Adds `AdminAuditController`, `AuditService`, `AuditLogEventStream`, `AdminSecurityMonitorController`, `AdminSecurityMonitorService` and `AdminSecurityRequestEventStream` with actual dependencies | `src/main/java/com/vibegraph/auth/web/AdminAuditController.java:30-76`; `src/main/java/com/vibegraph/auth/web/AdminSecurityMonitorController.java:19-35`. |
| Old graph/parser slice | Keeps analysis scheduler, payload guards, inference helpers, watcher/repository abstractions and MCP callback wiring with corrected direction | `src/main/java/com/vibegraph/graph/service/ProjectAnalysisScheduler.java:18-105`; `src/main/java/com/vibegraph/graph/websocket/GraphUpdateController.java:29-60`; `src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java`; `src/main/java/com/vibegraph/common/config/McpServerConfig.java:49-108`. |

## Compact-scope omissions

The current class diagrams are selected module slices, not an exhaustive call graph. `Plan`,
`UserCreditBalance`, `CreditLedger`, `AuditLog`, `FeatureFlag`, `Role`, `GraphService` and
`ProjectService` still exist in production code but are omitted from the compact views. Their
absence from the diagram is not a code deletion.

GitNexus was current at commit `d5154c4` with 1,173 files, 17,907 symbols, 41,198 edges and
300 execution flows. Those index counts are evidence metadata, not a class inventory promise.

## File-level mapping

| Old file | Current file | Change record |
| --- | --- | --- |
| `Diagram/4.1.Component_Deployment Diagram` | `Diagram/diagram update/4.1.Component_Deployment Diagram` | One-page diagrams.net deployment companion with health/mount/repository boundaries. |
| `Diagram/4.2.Class Diagram` | `Diagram/diagram update/4.2.Class Diagram` | Two-page diagrams.net companion: auth/control plane and graph/parser/diagram/MCP. |
| Component/class portions of `Diagram/plantuml_erd_component_class.md` | PART 3-PART 5 under `Diagram/diagram update/` | Canonical verified source. |
| Matching combined portions | `Diagram/diagram update/VibeGraph_All_PlantUML_Diagrams.md` | Generated mirror only. |

## Claims intentionally not proven

- The deployment view describes checked local compose/config and observed local containers, not an
  external production topology, TLS termination or provider credentials.
- Optional PostgreSQL-compatible storage is disabled by default; configuration support is not proof
  that it was active in the runtime snapshot.
- No Spring Data Neo4j `@Node` entity model is inferred. The represented persistence boundary is the
  raw-driver repository and V1/V2 Cypher startup migrations.
