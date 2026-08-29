# Use-case diagram changes: old artifact -> verified current artifact

## Inventory boundary

The old diagrams.net file has 10 pages and the old PlantUML file has a separate set of
sections, but the two inventories are not a one-to-one match. The diagrams.net file includes
a standalone source page, while the old PlantUML file includes an MCP section. The current
diagrams.net companion preserves 10 pages for visual comparison; the canonical current model
is consolidated into seven evidence-backed PlantUML views.

| Old artifact/page | Current artifact/section | Evidence-backed change | Evidence |
| --- | --- | --- | --- |
| `Diagram/1.Usecase Diagram` page `Use Case Tổng quát`; old system-boundary PlantUML | `plantuml_usecase.md` section `2.1 Current system boundary` | Adds explicit CLI/API-key and background-worker boundaries. Manual analysis is associated with the authenticated user; workers represent queued/import/watcher execution rather than the only initiator. | `src/main/java/com/vibegraph/graph/controller/ProjectController.java:105-121`; `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java:25-77`; `src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:85-123`. |
| Old guest registration/login page | Section `2.2 Authentication and account` | Removes unproved forgot-password and email-verification workflows. Shows local register/login, OAuth account linking, rotating refresh sessions, logout, profile/API-key and current self-service controllers. | `src/main/java/com/vibegraph/auth/web/AuthController.java:60-127`; `src/main/java/com/vibegraph/auth/service/AuthService.java:82-208`; `src/main/java/com/vibegraph/auth/oauth/OAuth2LoginSuccessHandler.java:39-66`; `src/main/resources/db/migration/V18__refresh_sessions.sql:1-20`. |
| Old project-management and import pages | Section `2.3 Project lifecycle and import` | Replaces immediate deletion with trash/list/restore/purge. Corrects import behavior: archive is synchronous `200` by default and asynchronous only with `async=true`; GitHub/local return `202`. Each async import service submits work before the controller returns; manual analyze remains a separate `ProjectAnalysisScheduler` path. | `src/main/java/com/vibegraph/graph/controller/ImportController.java:41-76`; `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java:132-149`; `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java:135-148`; `src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java:122-171`; `src/main/java/com/vibegraph/graph/controller/ProjectController.java:124-156`. |
| Old generic analysis page | Section `2.4 Source analysis and realtime` | Separates full analysis/status publication from watcher-driven per-file replacement and STOMP incremental broadcast. No all-or-nothing watcher transaction is claimed. | `src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java:39-120`; `src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:99-123`; `src/main/java/com/vibegraph/common/config/WebSocketConfig.java:51-61`. |
| Old graph, impact, source and UML pages | Section `2.5 Graph, source, patch and UML` | Consolidates graph/filter/neighbors/impact/bounded-source behavior. Moves local patch to a CLI/project-bound API-key actor and records scheduled full re-analysis. Keeps only the evidenced use-case UML endpoint and describes its real response fields: source/confidence, warnings, PlantUML/Mermaid and projected views. | `src/main/java/com/vibegraph/graph/controller/GraphController.java:27-136`; `src/main/java/com/vibegraph/patch/controller/LocalPatchController.java:25-77`; `src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java:399-428`; `src/main/java/com/vibegraph/diagram/dto/response/UmlUseCaseResponse.java:20-130`. |
| Old PlantUML MCP section (no matching old diagrams.net MCP page) | Section `2.6 MCP and AI client` | Uses the 18 registered tool objects and groups them by purpose. The note states that each tool resolves its own bounded data source; it does not claim every tool directly reads source files or Neo4j. | `src/main/java/com/vibegraph/common/config/McpServerConfig.java:49-108`; `src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md:8-42`; `src/main/java/com/vibegraph/mcp/MeteredToolCallback.java:22-72`. |
| Old admin page | Section `2.7 Admin and security operations` | Adds credit overview/adjustment, pricing-rule CRUD/deactivation, request-event/top-user/top-IP/suspicious-network reads, and IP-block list/create/update/delete. Storage remains read-only because no destructive storage endpoint is evidenced. | `src/main/java/com/vibegraph/auth/web/AdminCreditController.java:21-42`; `src/main/java/com/vibegraph/auth/web/AdminPricingController.java:24-52`; `src/main/java/com/vibegraph/abuse/AdminAbuseController.java:23-84`; `src/main/java/com/vibegraph/auth/web/AdminStorageController.java:15-22`. |

## File-level mapping

| Old file | Current file | Change record |
| --- | --- | --- |
| `Diagram/1.Usecase Diagram` | `Diagram/diagram update/1.Usecase Diagram` | Updated diagrams.net companion. It remains an `mxCell` diagrams.net document; neither the old nor current XML embeds PlantUML source. |
| `Diagram/plantuml_usecase.md` | `Diagram/diagram update/plantuml_usecase.md` | Canonical verified PlantUML use-case source. |
| Use-case portion of `Diagram/VibeGraph_All_PlantUML_Diagrams.md` | Matching portion under `Diagram/diagram update/` | Generated combined mirror, not an independent source of truth. |

## Claims intentionally not proven

- A database `email_verified` column is not proof of a working email-verification workflow.
- OAuth wiring is present, but external provider availability depends on credentials/configuration
  and was not exercised during this audit.
- No current GitLab import route, class-diagram API, sequence-diagram API, backend SVG-export API,
  or 3D graph renderer was established.
- The old `Diagram/` artifacts are untracked; "old" identifies their audited content, not a
  versioned predecessor with a provable commit date.
