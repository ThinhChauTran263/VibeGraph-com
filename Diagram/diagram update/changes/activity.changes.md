# Activity diagram changes: old artifact -> verified current artifact

## Artifact and flow mapping

| Old artifact/page | Current artifact/section | Evidence-backed change | Evidence |
| --- | --- | --- | --- |
| `Diagram/2.Activity Diagram` page `Authenticate Flow`; old auth PlantUML | `plantuml_activity.md` section `3.1 Local login, OAuth and rotating refresh session` | Splits register, login and OAuth callback. Only registration necessarily creates a new local user; login authenticates an existing user. Cookie writes/replacement/clearing are assigned to `AuthController` or `OAuth2LoginSuccessHandler` through `AuthCookieService`; refresh-session issue/rotate/revoke remains in `AuthService`/`RefreshSessionService`. | `src/main/java/com/vibegraph/auth/web/AuthController.java:60-135`; `src/main/java/com/vibegraph/auth/service/AuthService.java:82-208`; `src/main/java/com/vibegraph/auth/oauth/OAuth2LoginSuccessHandler.java:39-66`; `src/main/java/com/vibegraph/auth/service/RefreshSessionService.java:76-157`. |
| Page `Import Project Flow` | Section `3.2 Import and asynchronous analysis` | Corrects response semantics and ordering. Archive defaults to synchronous `200`; archive `async=true`, GitHub and local return `202`. In every asynchronous service path, submission to `analysisExecutor` occurs before the service returns and before the controller emits `202`. | `src/main/java/com/vibegraph/graph/controller/ImportController.java:41-76`; `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java:132-149`; `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java:135-148`; `src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java:122-171`. |
| Page `Code Analysis Flow` | Section `3.2` manual-analysis note and the current analysis status path | Separates import-owned executor submission from manual `POST /api/projects/{id}/analyze`, which uses `ProjectAnalysisScheduler` and coalesces duplicate manual requests. | `src/main/java/com/vibegraph/graph/controller/ProjectController.java:105-121`; `src/main/java/com/vibegraph/graph/service/ProjectAnalysisScheduler.java:55-105`; `src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java:39-120`. |
| Page `Realtime File Watcher Flow` | Section `3.3 File watcher incremental update` | Records the exact application order: `getFileSlice(before)`, `deleteFile`, optional `parseFile`/node upsert/edge upsert, `getFileSlice(after)`, delta calculation, then incremental broadcast. | `src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:99-123`; `src/main/java/com/vibegraph/watcher/service/impl/FileWatcherServiceImpl.java`. |
| Old browser-oriented graph/patch wording | Section `3.4 Graph/source/impact and CLI patch requests` | Keeps graph/source/impact in the browser branch. Moves patch to the controller's documented CLI/JWT-or-project-bound-API-key flow; a committed content change schedules coalesced full asynchronous re-analysis. No frontend patch caller is claimed. | `src/main/java/com/vibegraph/graph/controller/GraphController.java:27-136`; `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java:25-77`; `src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java:399-428`; `src/main/java/com/vibegraph/patch/service/PatchAnalysisScheduler.java:21-102`. |
| UML behavior implicit in the old set | Section `3.5 Use-case UML response` | Adds the only evidenced UML request flow and uses the real DTO contract: actors/use cases carry source/confidence; warnings expose inference limits; relations, PlantUML, Mermaid and projected views are returned. | `src/main/java/com/vibegraph/diagram/controller/DiagramController.java:39-79`; `src/main/java/com/vibegraph/diagram/service/impl/UseCaseDiagramServiceImpl.java:35-116`; `src/main/java/com/vibegraph/diagram/dto/response/UmlUseCaseResponse.java:20-130`. |
| Page `AI Interaction Flow (MCP Protocol)` | Section `3.6 MCP and admin event streams` | Replaces an invented generic MCP router with `ApiKeyAuthFilter`, Spring AI callbacks, 18 registered tools and `MeteredToolCallback`. Each selected tool resolves its own bounded data source. | `src/main/java/com/vibegraph/common/config/McpServerConfig.java:49-108`; `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java:100-124`; `src/main/java/com/vibegraph/mcp/MeteredToolCallback.java:22-72`. |
| Page `User Management Flow` | The sixth diagrams.net page plus section `3.6` admin branch | Preserves the old six-page diagrams.net inventory, but explicitly scopes the activity page to evidenced admin operations and audit/security SSE. Broader admin capabilities remain in the use-case and class views; the canonical activity does not claim that all admin CRUD occurs through the SSE path. | `src/main/java/com/vibegraph/auth/web/AdminAuditController.java:30-76`; `src/main/java/com/vibegraph/auth/web/AdminSecurityMonitorController.java:19-35`; `src/main/java/com/vibegraph/auth/web/AdminUserController.java:35-123`. |

## File-level mapping

| Old file | Current file | Change record |
| --- | --- | --- |
| `Diagram/2.Activity Diagram` | `Diagram/diagram update/2.Activity Diagram` | Updated diagrams.net companion with six pages; page topics are preserved for comparison but are not asserted to map one-to-one to the six canonical PlantUML sections. |
| `Diagram/plantuml_activity.md` | `Diagram/diagram update/plantuml_activity.md` | Canonical verified activity source. |
| Activity portion of `Diagram/VibeGraph_All_PlantUML_Diagrams.md` | Matching portion under `Diagram/diagram update/` | Generated combined mirror only. |

## Operational limitation proved by the source

`FileChangeBroadcaster` performs `deleteFile`, `upsertNodes` and `upsertEdges` as separate
repository calls and catches/logs a later failure (`FileChangeBroadcaster.java:102-123`). No
enclosing transaction is shown for the complete replacement sequence. The updated activity
therefore does not claim all-or-nothing rollback.

## Claims intentionally not proven

- Google/GitHub OAuth provider availability was not exercised.
- A `202` response proves acceptance, not eventual successful analysis.
- Runtime throughput, latency, STOMP/SSE delivery guarantees and production queue capacity were
  not measured.
