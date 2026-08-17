# VibeGraph - Verified Activity Diagrams

## 3.1. Local login, OAuth and rotating refresh session

```plantuml
@startuml
|Guest/User|
start
if (Authentication path?) then (register)
  :POST /api/auth/register;
  |AuthController / AuthService|
  :Validate registration;
  :Persist new User and default account settings;
elseif (login)
  :POST /api/auth/login;
  |AuthController / AuthService|
  :Throttle and authenticate existing User;
else (OAuth callback)
  :Complete Google/GitHub OAuth2 login;
  |OAuth2LoginSuccessHandler / AuthService|
  :Link or create verified provider identity;
endif
|AuthService / RefreshSessionService / JwtService|
:Issue access JWT and hashed refresh-session token;
|AuthController or OAuth2LoginSuccessHandler|
:Use AuthCookieService to set access and refresh HttpOnly cookies;
|Browser|
:Use authenticated REST request;
if (Access token expires?) then (yes)
  :POST /api/auth/refresh;
  |AuthController / AuthService / RefreshSessionService|
  :Read refresh cookie, lock row and rotate family token;
  |AuthController / AuthCookieService|
  :Set replacement cookies or clear them on unauthorized refresh;
else (no)
endif
|User|
:POST /api/auth/logout;
|AuthService / RefreshSessionService|
:Revoke refresh session;
|AuthController / AuthCookieService|
:Clear authentication cookies;
stop
@enduml
```

Evidence: `src/main/java/com/vibegraph/auth/web/AuthController.java:60-135`,
`src/main/java/com/vibegraph/auth/service/AuthService.java:82-208`,
`src/main/java/com/vibegraph/auth/oauth/OAuth2LoginSuccessHandler.java:39-66`,
`src/main/java/com/vibegraph/auth/service/RefreshSessionService.java:76-116`, migrations
`src/main/resources/db/migration/V18__refresh_sessions.sql` and
`src/main/resources/db/migration/V19__refresh_session_retention.sql`.

## 3.2. Import and asynchronous analysis

```plantuml
@startuml
|User|
start
if (Source kind?) then (archive)
  :POST /api/projects/import-archive;
  |ArchiveImportService|
  :Extract archive and register project;
elseif (GitHub)
  :POST /api/projects/import-github;
  |TarballImportService|
  :Fetch and extract GitHub tarball;
else (local)
  :POST /api/projects/import-local;
  |LocalImportService|
  :Validate root and register local project;
endif
|Import service|
:Persist ownership and project metadata;
if (Synchronous archive request?) then (yes)
  |AnalyzeService|
  :Parse supported Java files;
  :Infer flow/CPG edges;
  :Atomic graphRepository.upsertAnalysis;
  |Import service|
  :Return imported project response (200);
else (async archive, GitHub or local)
  :Submit analysis directly to analysisExecutor;
  |Import controller|
  :Return 202 with ANALYZING/progress=0 after service submission;
  |AnalyzeService|
  :Parse supported Java files;
  :Infer flow/CPG edges;
  :Atomic graphRepository.upsertAnalysis;
  |Import service|
  :Mark ANALYZED or FAILED;
  :Broadcast status over /topic/projects/{id}/status;
endif
|User|
:Observe progress and final state;
stop
@enduml
```

Evidence: `src/main/java/com/vibegraph/graph/controller/ImportController.java:29-76`,
`src/main/java/com/vibegraph/graph/controller/LocalProjectController.java:31-50`,
`src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java:132-149`,
`src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java:135-148`,
`src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java:122-171`,
`src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java:39-120`.

Manual `POST /api/projects/{id}/analyze` is a separate path:
`src/main/java/com/vibegraph/graph/controller/ProjectController.java:105-121` queues
`src/main/java/com/vibegraph/graph/service/ProjectAnalysisScheduler.java:55-105`, which
coalesces duplicate manual requests.

## 3.3. File watcher incremental update

```plantuml
@startuml
|OS|
start
:CREATE/MODIFY/DELETE event;
|FileWatcherServiceImpl|
:WatchService receives event;
:Reject ignored directories/extensions;
:Debounce and emit FileChangeEvent;
|FileChangeBroadcaster|
:Resolve normalized project-relative path;
:getFileSlice(projectId, path) as before;
:deleteFile(projectId, path);
if (CREATE/MODIFY and file still exists?) then (yes)
  :parseFile(path);
  :upsertNodes;
  :upsertEdges;
else (DELETE/no file)
endif
:getFileSlice(projectId, path) as after;
:Compute added/removed delta from before and after slices;
|GraphUpdateController|
:Broadcast INCREMENTAL update;
|STOMP client|
:Apply graph patch;
stop

note right
The three replacement writes are separate repository calls.
The source catches/logs failure after deletion; no rollback claim is made.
SOURCE: src/main/java/com/vibegraph/graph/websocket/FileChangeBroadcaster.java:92-123
end note
@enduml
```

## 3.4. Graph/source/impact and CLI patch requests

```plantuml
@startuml
|Caller|
start
if (Browser exploration request?) then (yes)
  :GET graph, neighbors, impact or bounded source slice;
  |Graph/source controller|
  :Assert project ownership;
  |GraphService / GraphRepository|
  :Read requested graph or source data;
  |GraphResponseFilter / GraphPayloadGuard|
  :Filter and cap graph payload when applicable;
  |Browser|
  :Render Sigma.js graph or source/impact result;
else (CLI patch)
  :POST /api/projects/{id}/patch or /current/patch;
  |LocalPatchController|
  :Validate project-bound key and ownership;
  |LocalPatchServiceImpl|
  :Validate all entries, atomically write/delete files, then commit;
  :Return requiresAnalyze=true when content changed;
  |PatchAnalysisScheduler|
  :Coalesce and schedule full asynchronous re-analysis;
endif
stop
@enduml
```

Evidence: `src/main/java/com/vibegraph/graph/controller/GraphController.java:27-136`,
`src/main/java/com/vibegraph/graph/service/impl/GraphResponseFilter.java:25-176`,
`src/main/java/com/vibegraph/graph/service/impl/GraphPayloadGuard.java:25-153`,
`src/main/java/com/vibegraph/graph/controller/SourceController.java:29-53`,
`src/main/java/com/vibegraph/patch/controller/LocalPatchController.java:25-77`,
`src/main/java/com/vibegraph/patch/service/impl/LocalPatchServiceImpl.java:399-428`,
`src/main/java/com/vibegraph/patch/service/PatchAnalysisScheduler.java:21-102`,
`vibegraph-web/src/components/graph/GraphCanvas.vue` and
`vibegraph-web/src/lib/api.ts:346-516`. No frontend caller for the local patch endpoint is
claimed; the controller documents it as a CLI/API-key flow.

## 3.5. Use-case UML response

```plantuml
@startuml
|Browser|
start
:GET /api/projects/{projectId}/diagrams/usecase?style=uml&mode=detailed;
|DiagramController|
:Assert owner, feature and ANALYZED status;
|UseCaseDiagramServiceImpl|
:Load graph through GraphService;
:UseCaseInferenceEngine infers actors/goals/relations;
:Return actors/use cases with source and confidence,\nrelations, inference warnings, PlantUML, Mermaid and views;
|Browser / DiagramPanel|
:Render sanitized SVG;
:Zoom, fit, fullscreen, download PNG;
stop
@enduml
```

Evidence: `src/main/java/com/vibegraph/diagram/controller/DiagramController.java:39-79`,
`src/main/java/com/vibegraph/diagram/service/impl/UseCaseDiagramServiceImpl.java:35-116`,
`src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java`,
`vibegraph-web/src/components/diagram/DiagramPanel.vue:347-596`,
`vibegraph-web/src/lib/api.ts:518-596`.

## 3.6. MCP and admin event streams

```plantuml
@startuml
|AI client|
start
:HTTP streamable request to /mcp;
|ApiKeyAuthFilter / SecurityConfig|
:Authenticate X-API-Key and project binding;
|Spring AI MCP|
:Resolve one of 18 registered tools;
|MeteredToolCallback|
:Check feature/ownership and meter credits;
|MCP analyzer|
:Resolve the selected tool's bounded data source;
|AI client|
:Receive bounded structured result;
stop

|Admin browser|
start
:GET /api/admin/security/stream or /api/admin/audit-logs/stream;
|Admin controller/service|
:Authorize ADMIN and publish sanitized SSE events;
|Admin browser|
:Update security/audit console;
stop
@enduml
```

Evidence: `src/main/java/com/vibegraph/common/config/McpServerConfig.java:49-108`,
`src/main/java/com/vibegraph/mcp/MODULE-GUIDE.md:8-42`,
`src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java:100-124`,
`src/main/java/com/vibegraph/auth/config/SecurityConfig.java:175-191`,
`src/main/java/com/vibegraph/auth/web/AdminSecurityMonitorController.java:20-33`,
`src/main/java/com/vibegraph/auth/web/AdminAuditController.java:31-73`.
