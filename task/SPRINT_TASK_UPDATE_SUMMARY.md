# VibeGraph - Sprint Task Update Summary
*Cập nhật: 2026-06-05 - Sau khi đọc toàn bộ source code*

## 🔄 CẬP NHẬT QUAN TRỌNG

### Team Members (5 người - CORRECTED)

| Thành viên | Vai trò                                 | Chuyên môn                       |
| ---------- | --------------------------------------- | -------------------------------- |
| **Thái**   | Business Analyst, Product Owner, Tester | Requirements, acceptance testing |
| **Thịnh**  | Leader, Quản lý dự án, Vibecode         | Project management, coordination |
| **Khoa**   | Fullstack Developer                     | BE + FE, parser, controllers     |
| **Danh**   | Fullstack Developer                     | BE + FE, services, UI            |
| **Vinh**   | Backend Developer, Scrum Master         | BE, Neo4j, DevOps                |

### Progress Status (REVISED sau khi đọc source code)

- **Sprint 1:** 95% DONE (was estimated 70%)
- **Sprint 2:** 20% IN PROGRESS
- **Sprint 3-4:** NOT STARTED
- **Overall:** ~55% complete (was estimated 36%)

---

## ✅ Sprint 1 Tasks - STATUS UPDATED

### Parser Module (DONE 95%)

| Task ID | Task                          | File                                           | Status    | Assignee   | Note                                      |
| ------- | ----------------------------- | ---------------------------------------------- | --------- | ---------- | ----------------------------------------- |
| T12     | NodeData/EdgeData/ParseResult | `parser/node/*.java`                           | ✅ DONE    | Khoa       | Data contracts complete                   |
| T13     | ClassVisitor                  | `parser/visitor/ClassVisitor.java`             | ✅ DONE    | Khoa       | 250 LOC, full implementation              |
| T14     | MethodVisitor                 | `parser/visitor/MethodVisitor.java`            | ✅ DONE    | Vinh       | 320 LOC, Symbol Solver integrated         |
| T15     | FieldVisitor                  | `parser/visitor/FieldVisitor.java`             | ✅ DONE    | Danh       | 200 LOC, injection detection              |
| T16     | ImportVisitor                 | `parser/visitor/ImportVisitor.java`            | ✅ DONE    | Danh       | 120 LOC, IMPORTS edges                    |
| T17     | SpringAnnotationVisitor       | `parser/visitor/SpringAnnotationVisitor.java`  | ✅ DONE    | Khoa       | 180 LOC, Route nodes                      |
| T18     | Structural edges              | All visitors                                   | ✅ DONE    | Khoa       | HAS_METHOD/FIELD/INNER/EXTENDS/IMPLEMENTS |
| T19     | CALLS with SymbolSolver       | `MethodVisitor.java`, `ParserServiceImpl.java` | ✅ DONE    | Vinh       | Cross-class resolution working            |
| T20     | Unit test visitors            | `parser/visitor/*Test.java`                    | 🚧 PARTIAL | Thái/Thịnh | Some tests disabled, need enable          |

### Neo4j Repository (DONE 90%)

| Task ID | Task                      | File                             | Status | Assignee | Note                       |
| ------- | ------------------------- | -------------------------------- | ------ | -------- | -------------------------- |
| T21     | Neo4j Driver config       | `common/config/Neo4jConfig.java` | ✅ DONE | Vinh     | Raw driver configured      |
| T22     | Cypher schema migration   | `V1__init_schema.cypher`         | ✅ DONE | Vinh     | Constraints + indexes      |
| T23     | upsertProject/Nodes/Edges | `Neo4jGraphRepository.java`      | ✅ DONE | Vinh     | 450 LOC, batch UNWIND      |
| T24     | getFullGraph              | `Neo4jGraphRepository.java`      | ✅ DONE | Khoa     | Stable IDs với fullName    |
| T25     | deleteFile                | `Neo4jGraphRepository.java`      | ✅ DONE | Vinh     | DETACH DELETE ready        |
| T26     | ArchUnit test             | `ArchTest.java`                  | 🚧 TODO | Thái     | Need to enforce boundaries |

### Service Layer (DONE 85%)

| Task ID | Task                       | File                                           | Status    | Assignee   | Note                             |
| ------- | -------------------------- | ---------------------------------------------- | --------- | ---------- | -------------------------------- |
| T27     | ProjectController          | `graph/controller/ProjectController.java`      | ✅ DONE    | Khoa       | 80 LOC, full CRUD                |
| T28     | AnalyzeService             | `graph/service/impl/AnalyzeServiceImpl.java`   | ✅ DONE    | Khoa       | 60 LOC, orchestration            |
| T29     | GraphController            | `graph/controller/GraphController.java`        | ✅ DONE    | Vinh       | 40 LOC, GET full graph           |
| T30     | GlobalExceptionHandler     | `common/exception/GlobalExceptionHandler.java` | ✅ DONE    | Danh       | 120 LOC, all exceptions mapped   |
| T31     | Node Detail API            | `GraphController.java`                         | 🚧 TODO    | Vinh       | GET /neighbors not implemented   |
| T32     | ImpactRepository.getImpact | `Neo4jGraphRepository.java`                    | 🚧 TODO    | Khoa       | Interface defined, impl Sprint 2 |
| T33     | Impact Analysis API        | `ImpactController.java`                        | 🚧 TODO    | Khoa       | GET /impact not implemented      |
| T34     | Integration test REST      | `*ControllerTest.java`, `*ServiceIT.java`      | 🚧 PARTIAL | Thái/Thịnh | Need more coverage               |

### Frontend Core (DONE 60%)

| Task ID | Task                   | File                                         | Status    | Assignee | Note                           |
| ------- | ---------------------- | -------------------------------------------- | --------- | -------- | ------------------------------ |
| T48     | API client             | `lib/api.ts`                                 | ✅ DONE    | Danh     | 180 LOC, full typed client     |
| T49     | Pinia store            | `stores/graph.ts`, `stores/project.ts`       | ✅ DONE    | Danh     | State management               |
| T50     | graphAdapter           | `lib/graphAdapter.ts`                        | ✅ DONE    | Thịnh    | 150 LOC, API → Graphology      |
| T51     | useSigma + GraphCanvas | `composables/useSigma.ts`, `GraphCanvas.vue` | ✅ DONE    | Danh     | 120+90 LOC, Sigma rendering    |
| T52     | SearchBar              | `components/graph/SearchBar.vue`             | 🚧 PARTIAL | Thịnh    | UI done, logic TODO            |
| T53     | Loading/error states   | `GraphCanvas.vue`                            | ✅ DONE    | Danh     | Overlay components             |
| T54     | Import Archive UI      | `AddProjectArchive.vue`                      | 🚧 PARTIAL | Thịnh    | 120 LOC, sync done, async TODO |

### Archive Import (BONUS - PARTIAL 70%)

| Task ID | Task                           | File                                              | Status    | Assignee | Note                      |
| ------- | ------------------------------ | ------------------------------------------------- | --------- | -------- | ------------------------- |
| T01     | ImportArchive DTO              | `graph/dto/request/*.java`, `dto/response/*.java` | ✅ DONE    | Khoa     | Request/response defined  |
| T02     | ArchiveImportService interface | `graph/service/ArchiveImportService.java`         | ✅ DONE    | Khoa     | Interface complete        |
| T03     | ArchiveTypeDetector            | `graph/service/impl/ArchiveTypeDetector.java`     | ✅ DONE    | Vinh     | ZIP/TAR/TAR_GZ detection  |
| T04     | Safe ArchiveExtractor          | `graph/service/impl/ArchiveExtractor.java`        | ✅ DONE    | Vinh     | Anti zip-slip, size limit |
| T05     | POST /import-archive           | `graph/controller/ImportController.java`          | 🚧 PARTIAL | Khoa     | Sync done, async 70%      |
| T06     | Unit test ArchiveExtractor     | `ArchiveExtractorTest.java`                       | ✅ DONE    | Thịnh    | Zip-slip tests passing    |

---

## 🚧 Sprint 2 Tasks - IN PROGRESS (20%)

### WebSocket Realtime (20%)

| Task ID | Task                      | File                                                     | Status | Assignee | Note                   |
| ------- | ------------------------- | -------------------------------------------------------- | ------ | -------- | ---------------------- |
| T35     | STOMP + AsyncConfig       | `common/config/WebSocketConfig.java`, `AsyncConfig.java` | ✅ DONE | Vinh     | Config ready           |
| T36     | GraphUpdatePublisher      | `graph/service/GraphUpdatePublisher.java`                | 🚧 TODO | Vinh     | Broadcast logic needed |
| T37     | FileWatcherService        | `watcher/FileWatcherService.java`                        | ❌ TODO | Khoa     | Empty skeleton         |
| T38     | Integration test realtime | `*WebSocketIT.java`                                      | ❌ TODO | Thái     | Not started            |

### GitHub Import (10%)

| Task ID | Task                    | File                                               | Status | Assignee | Note                     |
| ------- | ----------------------- | -------------------------------------------------- | ------ | -------- | ------------------------ |
| T07     | GitHubUrlParser         | `graph/service/impl/GitHubUrlParser.java`          | ❌ TODO | Khoa     | Not started              |
| T08     | GitHub pre-flight       | `graph/service/impl/GithubPreFlightService.java`   | ❌ TODO | Khoa     | Not started              |
| T09     | TarballImportService    | `graph/service/impl/TarballImportServiceImpl.java` | ❌ TODO | Vinh     | Throws "not implemented" |
| T10     | POST /import-github     | `graph/controller/ImportController.java`           | ❌ TODO | Vinh     | Endpoint exists, no impl |
| T11     | Integration test GitHub | `*GithubImportIT.java`                             | ❌ TODO | Thái     | Not started              |

### Diagram Services (0%)

| Task ID | Task                  | File                                                  | Status | Assignee | Note                |
| ------- | --------------------- | ----------------------------------------------------- | ------ | -------- | ------------------- |
| T39     | UseCaseDiagramService | `diagram/service/impl/UseCaseDiagramServiceImpl.java` | ❌ TODO | Khoa     | Empty with TODO     |
| T40     | ClassDiagramService   | `diagram/service/impl/ClassDiagramServiceImpl.java`   | ❌ TODO | Danh     | Empty with TODO     |
| T41     | DiagramController     | `diagram/controller/DiagramController.java`           | ❌ TODO | Danh     | No endpoint methods |
| T42     | Unit test diagrams    | `*DiagramServiceTest.java`                            | ❌ TODO | Thịnh    | Not started         |

### Frontend Panels (20%)

| Task ID | Task                 | File                                            | Status     | Assignee | Note                            |
| ------- | -------------------- | ----------------------------------------------- | ---------- | -------- | ------------------------------- |
| T55     | GitHub Import Form   | `components/projects/GithubImportForm.vue`      | ❌ TODO     | Thịnh    | Not started                     |
| T56     | FilterPanel          | `components/panels/FilterPanel.vue`             | 🚧 SCAFFOLD | Danh     | UI only, no logic               |
| T57     | Focus Mode           | `lib/focusMode.ts`, `composables/useFilters.ts` | 🚧 PARTIAL  | Danh     | Structure only                  |
| T58     | NodeDetailPanel      | `components/panels/NodeDetailPanel.vue`         | 🚧 SCAFFOLD | Thịnh    | UI only                         |
| T59     | ImpactAnalysis panel | `components/panels/ImpactPanel.vue`             | ❌ TODO     | Thịnh    | Not started                     |
| T60     | useWebSocket         | `composables/useWebSocket.ts`                   | 🚧 PARTIAL  | Danh     | Connection only, broadcast TODO |
| T61     | DiagramPanel         | `components/diagram/DiagramPanel.vue`           | ❌ TODO     | Thịnh    | Not started                     |
| T62     | Unit test FE         | `__tests__/*.spec.ts`                           | 🚧 MINIMAL  | Thái     | Very few tests                  |

### MCP Tools (5%)

| Task ID | Task                     | File                                 | Status    | Assignee | Note                         |
| ------- | ------------------------ | ------------------------------------ | --------- | -------- | ---------------------------- |
| T43     | get_project_architecture | `mcp/tool/ArchitectureTool.java`     | ❌ TODO    | Khoa     | Class exists, no @Tool       |
| T44     | get_class_context        | `mcp/tool/ClassContextTool.java`     | ❌ TODO    | Vinh     | Class exists, no @Tool       |
| T45     | get_impact_analysis      | `mcp/tool/ImpactAnalysisTool.java`   | ❌ TODO    | Khoa     | Class exists, no @Tool       |
| T46     | get_layer_pattern        | `mcp/tool/LayerPatternTool.java`     | ❌ TODO    | Danh     | Class exists, no @Tool       |
| T47     | Spring AI MCP config     | `common/config/McpServerConfig.java` | 🚧 PARTIAL | Danh     | Config present, needs wiring |

### DevOps (PARTIAL)

| Task ID | Task                | File                             | Status    | Assignee | Note                      |
| ------- | ------------------- | -------------------------------- | --------- | -------- | ------------------------- |
| T63     | Backend Dockerfile  | `Dockerfile`                     | ✅ DONE    | Vinh     | Multi-stage build         |
| T64     | Frontend Dockerfile | `vibegraph-web/Dockerfile`       | 🚧 TODO    | Vinh     | Needs nginx config        |
| T65     | docker-compose      | `docker-compose.yml`             | ✅ DONE    | Vinh     | BE+FE+Neo4j configured    |
| T66     | Env profiles        | `application-*.yml`              | 🚧 PARTIAL | Thịnh    | Dev done, prod needs work |
| T67     | Backend CI          | `.github/workflows/backend.yml`  | 🚧 TODO    | Vinh     | Not configured            |
| T68     | Frontend CI         | `.github/workflows/frontend.yml` | 🚧 TODO    | Thịnh    | Not configured            |
| T69     | Docker guide        | `DEVOPS-GUIDE.md`                | 🚧 PARTIAL | Thịnh    | Basic guide exists        |

---

## 📋 Sprint 3-4 Tasks - NOT STARTED

### Sprint 3 (Weeks 5-6) - Polish & Performance

| Area               | Tasks                                   | Priority | Assignee     | Status |
| ------------------ | --------------------------------------- | -------- | ------------ | ------ |
| **Testing**        | Boost coverage to 70%                   | Critical | Thái + Thịnh | ❌ TODO |
| **Performance**    | Benchmark 500-file parse                | High     | Vinh         | ❌ TODO |
| **Performance**    | Optimize Neo4j queries                  | High     | Vinh         | ❌ TODO |
| **Performance**    | Test 5000-node rendering                | High     | Danh         | ❌ TODO |
| **Caching**        | Content-hash based caching              | Medium   | Khoa         | ❌ TODO |
| **Technical Debt** | Consolidate spring layer detection (D1) | Medium   | Khoa         | ❌ TODO |
| **Technical Debt** | Extract TypeNames utility (D2)          | Medium   | Khoa         | ❌ TODO |
| **Technical Debt** | Handle unresolved calls (D5)            | High     | Vinh         | ❌ TODO |

### Sprint 4 (Weeks 7-8) - Deployment & Documentation

| Area              | Tasks                     | Priority | Assignee     | Status |
| ----------------- | ------------------------- | -------- | ------------ | ------ |
| **Deployment**    | Production Docker setup   | High     | Vinh         | ❌ TODO |
| **Deployment**    | Domain + SSL setup        | Medium   | Vinh         | ❌ TODO |
| **CI/CD**         | Complete GitHub Actions   | High     | Vinh + Thịnh | ❌ TODO |
| **Documentation** | Complete README           | Critical | Thái         | ❌ TODO |
| **Documentation** | MCP integration guide     | High     | Thái         | ❌ TODO |
| **Documentation** | Deployment guide          | High     | Vinh         | ❌ TODO |
| **Demo**          | Sample project setup      | Critical | Thái         | ❌ TODO |
| **Demo**          | Demo script preparation   | Critical | Thái + Thịnh | ❌ TODO |
| **Bug Fixes**     | High-priority bug backlog | Critical | All          | ❌ TODO |

---

## 🎯 Immediate Action Items (Week 3)

### Critical Priority (Must Complete This Week)

1. **Complete File Watcher** (12h - Khoa)
   - File: `watcher/FileWatcherService.java`, `watcher/DebouncedEventHandler.java`
   - Implement Java WatchService với recursive monitoring
   - Debounce 500ms
   - Connect to incremental parser

2. **Connect WebSocket Broadcasting** (8h - Vinh)
   - File: `graph/service/GraphUpdatePublisher.java`
   - Implement broadcast logic
   - Wire watcher events → WebSocket messages
   - Test với frontend

3. **Complete Frontend Filters** (16h - Danh)
   - File: `components/panels/FilterPanel.vue`, `composables/useFilters.ts`
   - Node/edge type toggles
   - Count display
   - Apply filters to Sigma

4. **Write Integration Tests** (12h - Thái + Thịnh)
   - File: `*IT.java`, `*.spec.ts`
   - Neo4jGraphRepositoryIT với Testcontainers
   - Parser E2E test
   - Frontend component tests

### High Priority (This + Next Week)

5. **Implement Diagram Services** (16h - Khoa + Danh)
   - Files: `UseCaseDiagramServiceImpl.java`, `ClassDiagramServiceImpl.java`
   - Mermaid generation logic
   - DiagramController endpoints
   - Frontend DiagramPanel

6. **GitHub Import** (12h - Khoa + Vinh)
   - Files: `TarballImportServiceImpl.java`, `GithubPreFlightService.java`
   - Pre-flight API check
   - Tarball streaming
   - Frontend form

---

## 📊 Velocity & Capacity (REVISED)

### Team Capacity
- **Team Size:** 5 developers (Khoa, Danh, Vinh + Thái, Thịnh part-time)
- **Effective Capacity:** ~100 hours/week
  - Khoa, Danh, Vinh: 40h each = 120h
  - Thái, Thịnh: 20h each (PM + testing) = 40h
  - Total: 160h raw → ~100h effective (accounting for meetings, coordination)

### Progress Tracking
- **Sprint 1 Delivered:** ~180h worth of work in 2 weeks
- **Sprint 2 Remaining:** 137h / 4 weeks = 34h/week → achievable
- **Sprint 3-4:** 200h / 4 weeks = 50h/week → comfortable

### Risk Assessment
- ✅ **Low Risk:** Core infrastructure complete
- 🟡 **Medium Risk:** WebSocket, Diagrams, MCP (well-scoped)
- 🟡 **Medium Risk:** Testing coverage (need dedicated effort)
- 🟢 **Low Risk:** Documentation & deployment (standard work)

---

## 📝 Notes for Task Assignment

### Khoa (Fullstack - BE focus)
- Primary: Parser, Services, Controllers
- Strength: JavaParser, Spring Boot, business logic
- Current load: FileWatcher, Diagrams, MCP tools

### Danh (Fullstack - FE focus)
- Primary: Frontend components, API integration
- Strength: Vue 3, TypeScript, Sigma.js
- Current load: Filters, Focus Mode, Frontend polish

### Vinh (Backend + DevOps + Scrum Master)
- Primary: Neo4j, WebSocket, Docker, CI/CD
- Strength: Database, infrastructure, coordination
- Current load: WebSocket, GitHub import, DevOps

### Thái (BA + PO + Tester)
- Primary: Requirements, acceptance testing, documentation
- Strength: Business analysis, test design, stakeholder communication
- Current load: Integration tests, documentation, demo preparation

### Thịnh (Leader + PM + Vibecode)
- Primary: Project management, frontend testing, coordination
- Strength: Planning, testing, team coordination
- Current load: Frontend tests, DevOps guide, demo script

---

*Last Updated: 2026-06-05*  
*Next Review: End of Week 3 (Sprint 2 mid-point)*
