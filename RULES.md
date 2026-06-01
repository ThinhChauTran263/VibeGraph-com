# VibeGraph — Universal AI Coding Rules

> **Single source of truth.** All IDE wrappers (CLAUDE.md, AGENTS.md, .cursorrules, GEMINI.md, .kiro/rules.md, .factory/rules.md) point here. When this file changes, all tools follow.

**Tools supported:** Claude Code · Codex · Gemini CLI · Cursor · Antigravity · Kiro · Factory AI

---

## 0. Project Snapshot

| Field | Value |
|-------|-------|
| Name | VibeGraph |
| Description | Realtime Java code analyzer with knowledge graph + AI integration via MCP |
| Backend | Spring Boot 4.0.6 + Java 21 + Maven + MapStruct 1.6.3 |
| Database | Neo4j 5.x (Bolt 7687) |
| Frontend | Vue 3.5 + Vite 8 + TypeScript 6 + Sigma.js 3 + Mermaid 11 |
| Realtime | WebSocket STOMP (`/ws/graph`) |
| MCP | Spring AI MCP Server 1.1.6 (`/mcp`) |
| Module layout | Single-module backend (`src/main/java/com/vibegraph/`) + `vibegraph-web` (Vue) |
| Coverage gate | JaCoCo 70% line minimum (BUNDLE) |

---

## 1. Golden Rules — Always / Never

### Always
- **Run impact analysis BEFORE editing any symbol.** Use `gitnexus_impact({target, direction: "upstream"})` and report blast radius (callers, processes, risk).
- **Run `gitnexus_detect_changes()` BEFORE every commit.** Verify only expected symbols/flows are touched.
- **Validate input at every system boundary** — REST controllers (`@Valid`), MCP tool args, WebSocket payloads, file paths.
- **Use parameterized Cypher queries.** Never concatenate user input into Cypher.
- **Handle errors explicitly.** No swallowed catches. Log with context, return typed errors.
- **Match existing conventions.** Read neighboring files before introducing a new pattern.

### Never
- NEVER ignore HIGH or CRITICAL impact warnings without explicit user approval.
- NEVER rename via find-and-replace — use `gitnexus_rename`.
- NEVER commit secrets. `.env` is gitignored — use `.env.example` for templates.
- NEVER bypass validation, auth, or coverage gates with `--no-verify` or skip flags.
- NEVER mutate request DTOs in controllers — copy into domain objects.
- NEVER block the WebSocket thread with synchronous parsing — offload to async service.
- NEVER return raw entities from REST controllers — always wrap in `ApiResponse<T>`.
- NEVER use `e.printStackTrace()` or `System.out.println` for error logging — use SLF4J.
- NEVER introduce new npm packages or CSS frameworks without explicit user permission.

---

## 2. Workflow — Spec-driven Planning + Free-flow Execution

### Phase 1: Understand
1. Run `/understand` or `gitnexus_query({query: "feature area"})` to map relevant code.
2. Read existing specs in `VibeGraph-specs-2month/` if the feature touches architecture.
3. Check `gitnexus://repo/VibeGraph-com/processes` for affected execution flows.

### Phase 2: Plan
1. Write a brief spec (1 page max) in `VibeGraph-specs-2month/` for non-trivial features.
2. Break into tasks — each task = one commit-sized unit.
3. Identify risk: run `gitnexus_impact` on symbols you plan to change.

### Phase 3: Execute (free-flow)
- Code freely within the planned scope.
- Backend and frontend can be developed in parallel.
- Use `mvn compile` (backend) and `npm run type-check` (frontend) as fast feedback loops.

### Phase 4: Commit Checklist (MANDATORY before every commit)
- [ ] `gitnexus_detect_changes()` — only expected symbols affected
- [ ] `mvn verify` passes (compiles + tests + coverage ≥ 70%)
- [ ] `cd vibegraph-web && npm run lint && npm run type-check` passes
- [ ] No secrets in staged files (`grep -r "password\|secret\|token" --include="*.java" --include="*.ts"`)
- [ ] Commit message follows: `<type>: <description>` (feat, fix, refactor, docs, test, chore, perf)

---

## 3. Code Quality

### Java (Backend)
- **Max file size:** 400 lines (800 absolute max). Extract when growing.
- **Max method size:** 30 lines. Extract private helpers.
- **Naming:** PascalCase classes, camelCase methods/fields, UPPER_SNAKE constants.
- **Immutability:** Use `record` for read-only response DTOs. Use class with `@Builder` for DTOs needing MapStruct mapping. Prefer `final` fields. No setters on domain objects.
- **Layering:** Controller → Service (interface) → Repository. No cross-layer shortcuts.
- **Lombok:** `@Getter`, `@RequiredArgsConstructor`, `@Builder` only. No `@Data` on entities.
- **Neo4j persistence:** KHÔNG dùng Spring Data Neo4j OGM. KHÔNG tạo `@Node`/`@Relationship` model classes hay `*NodeRepository`. Persist graph qua `GraphRepository` → `Neo4jGraphRepository` dùng **raw Neo4j Java Driver + parameterized Cypher**. Graph metadata là Neo4j node properties (từ Cypher của repository hoặc `NodeData` properties), không phải field của entity Java.
- **Driver isolation:** Service KHÔNG import `org.neo4j.*` / `org.springframework.data.neo4j.*`; chỉ `repository/impl/neo4j/` và `common/config` (vd `Neo4jMigrationRunner`) được dùng driver. ArchUnit ép buộc (`StorageAbstractionTest`).
- **API Response:** REST endpoints trả về wrapper `ApiResponse<T>` cho response body. Ngoại lệ: các action delete / no-content được phép trả `204 No Content` với body rỗng (vd `DELETE /api/projects/{id}` → `ResponseEntity<Void>`):
  ```java
  public record ApiResponse<T>(int code, String message, T data) {
      public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(200, "OK", data); }
      public static <T> ApiResponse<T> error(int code, String message) { return new ApiResponse<>(code, message, null); }
  }
  ```
- **Error Handling:**
  - Use `@RestControllerAdvice` for global exception handling (one `GlobalExceptionHandler` class)
  - Log errors via SLF4J: `private static final Logger log = LoggerFactory.getLogger(X.class);`
  - Never use `e.printStackTrace()` or `System.out.println`
  - Log with context: `log.error("Failed to parse {}: {}", filePath, e.getMessage(), e);`
- **CORS:** Configure global CORS in `WebConfig` allowing frontend origin (`http://localhost:3000` dev, env-configurable in prod).
- **Data Mapping:** Không dùng `@Node` entity. Tại ranh giới Neo4j driver repository (`Neo4jGraphRepository`), ánh xạ **explicit/manual** từ raw `org.neo4j.driver.Record`/`Node` sang DTO (vd `mapNodeToDto`) là chấp nhận được — driver record không phải bean để MapStruct map tự động. MapStruct vẫn dùng được cho ánh xạ POJO ↔ DTO thuần ở nơi khác.
- **Neo4j Migration:** NEVER rely on auto-schema. Cypher migrations sống trong `src/main/resources/db/migration/` (vd `V1__init_schema.cypher`). `Neo4jMigrationRunner` áp dụng chúng idempotently lúc khởi động. Theo dõi version/metadata các migration đã áp dụng (vd `:Migration` node) là kế hoạch sau, CHƯA hiện thực.
- **Soft Delete / Hard Delete:**
  - Soft delete (`deleted: boolean` + `deletedAt`) áp dụng cho business / user-owned persistent entities (nếu/khi có).
  - Derived graph data (sinh từ parser) ĐƯỢC PHÉP hard delete bằng parameterized Cypher trong `GraphRepository`/`Neo4jGraphRepository`.
  - `DETACH DELETE` được phép cho `deleteFile` / incremental re-parse cleanup và migration/maintenance Cypher.
  - `DETACH DELETE` KHÔNG được dùng ở controller/service và KHÔNG được nối chuỗi input người dùng (chỉ parameterized Cypher trong `repository/impl/neo4j`).
- **Auditing:** KHÔNG dùng Spring Data auditing annotations (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy`) cho graph — không có `@Node` entity. Nếu cần timestamp, set như node property trong Cypher của `Neo4jGraphRepository`.
- **API Documentation:** Add OpenAPI 3 annotations (`@Tag`, `@Operation`, `@Schema`) on all controllers and DTOs.

### TypeScript / Vue (Frontend)
- **Max file size:** 300 lines for components, 400 for composables/stores.
- **Vue 3 `<script setup>` only.** No Options API. No `export default {}` blocks. Use `ref`, `reactive`, `computed`, `watch` from Composition API.
- **Naming:** PascalCase components, camelCase composables (`useXxx`), kebab-case files.
- **State:** Pinia stores for shared state. Composables for reusable logic. No Vuex. No raw `localStorage` for app state — use Pinia with persistence plugin if needed.
- **Types:** Explicit types in `types/` directory. No `any` — use `unknown` + type guards.
- **Sigma.js:** Graph logic in `composables/useSigma.ts`. Never manipulate graph in components directly.
- **WebSocket:** Single STOMP connection managed in `composables/useWebSocket.ts`.
- **HTTP Client:** Axios with global interceptor in `src/plugins/axios.ts`:
  - Auto-attach auth token (Bearer) to all requests
  - Handle 401 globally (redirect to login or refresh token)
  - Show user-friendly toast/notification on API errors
  - Never silently swallow errors
- **Router Guards:** All protected routes must use `beforeEach` navigation guard checking authentication state from Pinia store.
- **Strict Dependencies:** Vite only (no Webpack). No UI frameworks (Bootstrap/Tailwind/ElementUI). Ask permission before adding new npm packages.

### Language Rules (Bilingual)
- **Communication:** Explain concepts and chat in Vietnamese.
- **Code:** All variables, class names, method names, database columns, and file names MUST be in English.
- **Git:** Commit messages and branch names MUST be in English.
- **Docs:** Inline comments and docstrings MUST be in English.

### Comments & Documentation

**Inline comments:**
- Do not comment the obvious (e.g., no `// loop through array`)
- Only explain **WHY**: business logic, algorithm choice, workarounds
- Keep to 1 line max

**JavaDoc (Backend) — add when preparing for release/team onboarding:**
- Required for all `public` classes and methods
- Include `@param`, `@return`, `@throws`
- Skip for trivial getters/setters and obvious `@Override` methods

**JSDoc (Frontend) — add when preparing for release/team onboarding:**
- Required for: complex Vue components, composables, utility functions
- Include `@param`, `@returns`
- Skip for: simple components, internal helpers

### Shared
- No TODO/FIXME in committed code — track in issues instead.
- Prefer early returns over deep nesting (max 3 levels).
- **Code Formatting:**
  - **Java:** Follow Google Java Style (4-space indent, no trailing spaces).
  - **Frontend:** Follow Prettier config (single quotes, no trailing commas, 2-space indent).
  - Run formatters before commit. CI will reject unformatted code.

---

## 4. Security

### Input Validation
- All REST endpoints: `@Valid` on request bodies, `@NotBlank`/`@Size`/`@Pattern` on fields.
- File paths: Validate against allowed base directories. Reject `..` traversal.
- Cypher: Always use `$paramName` placeholders. Never string concatenation.
- WebSocket: Validate message payloads before processing.

### Secrets Management
- **NEVER hardcode** API keys, passwords, database credentials, or secret tokens in source code.
- **Backend:** Store in `.env` (gitignored), access via `@Value("${...}")` or `@ConfigurationProperties`. Use `application.yml` with `${ENV_VAR}` syntax.
- **Frontend:** Use `import.meta.env.VITE_*` variables. Never expose secrets in client-side code.
- **Documentation:** All sample keys must be placed in `.env.example` with placeholder values.
- **Logging:** Never log secrets. Mask sensitive data in error messages.

### Database Performance (Neo4j)
- **Avoid round-trips:** Use single Cypher query with pattern matching instead of multiple queries.
- **Pagination:** Always use `Pageable` and return `Page<T>` for list APIs. Never fetch entire graph.
- **Depth limit:** Use `@Depth` annotation or explicit `LIMIT` in Cypher for large result sets.
- **Indexing:** Ensure frequently queried properties have Neo4j indexes.
- **Example:**
  ```java
  @Query("MATCH (n:Symbol) WHERE n.name CONTAINS $keyword RETURN n SKIP $skip LIMIT $limit")
  Page<Symbol> searchByName(String keyword, Pageable pageable);
  ```

### Authentication / Authorization
- MCP endpoints: Validate tool arguments. Reject unknown tools.
- File watcher: Only watch configured project directories.
- Neo4j: Use dedicated app user, not `neo4j` admin in production.

### OWASP Checklist (verify before release)
- [ ] No SQL/Cypher injection
- [ ] No path traversal
- [ ] No sensitive data in logs
- [ ] No hardcoded credentials
- [ ] CORS configured for allowed origins only
- [ ] Rate limiting on public endpoints

---

## 5. Testing

### Coverage Requirements
- **Backend:** 70% line coverage (enforced by JaCoCo in `mvn verify`).
- **Frontend:** Run `npm run test:unit` — aim for 60% coverage on critical paths (composables, stores, utilities).

### Test Types
| Type | Backend | Frontend |
|------|---------|----------|
| Unit | JUnit 5 + Mockito | Vitest + Vue Test Utils |
| Integration | `@SpringBootTest` + Testcontainers Neo4j | — |
| Architecture | ArchUnit (enforce layering) | — |

### Test Conventions
- **Naming:** `MethodName_StateUnderTest_ExpectedBehavior` (Java), `describe/it` blocks (TS).
- **Structure:** Arrange → Act → Assert.
- **Mocking:** Mock external dependencies (Neo4j, file system). Never mock the class under test.
- **Data:** Use builders/factories for test data. No magic strings.

### When to Write Tests
- New public method → unit test required.
- Bug fix → regression test required (prove it was broken, prove it's fixed).
- Neo4j query → integration test with Testcontainers.
- WebSocket handler → integration test verifying message flow.

---

## 6. Git Workflow

### Branching
- `main` — protected, always deployable
- `feature/<short-name>` — new features
- `fix/<short-name>` — bug fixes
- `refactor/<short-name>` — refactoring without behavior change
- `chore/<short-name>` — tooling, deps, config

### Commit Format
```
<type>: <description>

<optional body explaining WHY, not WHAT>
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`

**Examples:**
- `feat: add WebSocket reconnection with exponential backoff`
- `fix: prevent NPE when parsing classes without package declaration`
- `refactor: extract CallGraphBuilder from AnalyzeService`

### Commit Rules
- One logical change per commit.
- Keep commits small enough to review in <5 min.
- Run commit checklist (Section 2 Phase 4) before every commit.
- No `--no-verify` unless user explicitly authorizes.

### Pull Requests
- Title: same format as commit message.
- Body must include: **Summary**, **Test Plan**, **Risk Assessment** (from `gitnexus_impact`).
- Link related specs in `VibeGraph-specs-2month/`.
- Wait for all CI checks green before merge.

---

## 7. CI/CD

### Required Checks (must pass before merge)
1. **Backend build:** `mvn verify` (compile + test + JaCoCo 70%)
2. **Frontend build:** `npm run build` + `npm run lint` + `npm run type-check`
3. **Frontend tests:** `npm run test:unit`
4. **Security scan:** No secrets in diff (gitleaks or equivalent)
5. **Architecture:** ArchUnit tests pass (enforced layering)

### Local Pre-Commit (mirror CI locally)
```bash
# Backend
mvn verify

# Frontend
cd vibegraph-web && npm run lint && npm run type-check && npm run test:unit && npm run build
```

### Docker
- `docker compose up -d` for full local stack (Neo4j + backend + frontend)
- `docker compose down -v` to reset Neo4j volume
- Healthchecks must pass before backend starts (Neo4j ready)

### Release
- Tag format: `v<major>.<minor>.<patch>`
- Update `CHANGELOG.md` with notable changes
- Verify `docker compose up -d` works on a clean checkout

---

## 8. AI Skills & Tools Usage

### Code Intelligence (use BEFORE editing)
| Task | Tool |
|------|------|
| Understand architecture | `gitnexus_query({query: "..."})` or `/understand` |
| Check blast radius | `gitnexus_impact({target, direction: "upstream"})` |
| Trace a bug | `gitnexus_context({name: "symbolName"})` |
| Safe rename | `gitnexus_rename({symbol_name, new_name})` |
| Pre-commit check | `gitnexus_detect_changes()` |
| Visual dashboard | `/understand-dashboard` |
| Onboarding guide | `/understand-onboard` |
| Domain mapping | `/understand-domain` |

### Code Review
| When | Agent |
|------|-------|
| After writing Java code | `java-reviewer` |
| After writing Vue/TS code | `typescript-reviewer` |
| Security-sensitive changes | `security-reviewer` |
| Architecture changes | `architect` |

### Frontend Design
| Task | Skill |
|------|-------|
| UI component design | `/ui-ux-pro-max` |
| Graph visualization styling | Use Sigma.js best practices from skill data |

### Planning & Shipping
| Phase | Skill |
|-------|-------|
| Write spec/PRD | `/addy-spec` or `/addy-plan` |
| Build incrementally | `/addy-build` |
| Code review | `/addy-review` |
| Ship & verify | `/addy-ship` |

---

## 9. MCP Configuration

VibeGraph exposes an MCP server for AI tools. Add to your tool's MCP config:

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "http://localhost:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

### MCP Tool Development Rules
- Each tool must have: name, description, input schema (JSON Schema).
- Validate all tool arguments before processing.
- Return structured JSON responses using `ApiResponse<T>` format: `{code, message, data}`.
- Keep tool execution under 10 seconds. Offload heavy work to async.
- Log tool invocations at INFO level (tool name + project path, no secrets).

---

## 10. File Structure Reference

```
VibeGraph/
├── src/main/java/com/vibegraph/
│   ├── common/          # Shared utilities
│   ├── diagram/         # UML diagram generators
│   ├── graph/           # Graph-related logic (controller, service, repository, node, dto)
│   ├── mcp/             # MCP Server tools
│   ├── parser/          # JavaParser visitors
│   ├── watcher/         # File watcher (WatchService)
│   ├── mapper/          # MapStruct mappers (to be created)
│   ├── exception/       # Custom exceptions + GlobalExceptionHandler (to be created)
│   ├── config/          # Spring config (Neo4j, WS, CORS, Security) (to be created)
│   └── VibeGraphApplication.java
├── src/test/java/       # Mirror main structure
├── vibegraph-web/
│   └── src/
│       ├── components/  # Vue SFCs
│       ├── composables/ # Reusable logic (useSigma, useWebSocket)
│       ├── stores/      # Pinia stores
│       ├── types/       # TypeScript interfaces
│       ├── views/       # Route-level pages
│       └── router/      # Vue Router config
├── VibeGraph-specs-2month/     # Feature specs & architecture docs
├── RULES.md             # THIS FILE — single source of truth
├── docker-compose.yml   # Full local stack
└── pom.xml              # Maven build
```

---

## 11. Quick Reference — Copy-Paste Commands

### Backend
```bash
# Build + test + coverage
mvn verify

# Run locally (requires Neo4j running)
mvn spring-boot:run

# Run single test class
mvn test -Dtest=AnalyzeServiceTest

# Check coverage report
open target/site/jacoco/index.html
```

### Frontend
```bash
cd vibegraph-web

# Dev server
npm run dev

# Lint + type check
npm run lint && npm run type-check

# Unit tests
npm run test:unit

# Production build
npm run build
```

### Docker
```bash
# Start full stack
docker compose up -d

# View logs
docker compose logs -f backend

# Reset everything
docker compose down -v
```

### GitNexus
```bash
# Re-index after major changes
npx gitnexus analyze

# Check index status
npx gitnexus status
```

---

## 12. Changelog

| Date | Change |
|------|--------|
| 2026-05-28 | Initial version — unified rules for all AI tools |

---

> **Maintained by:** VibeGraph Team  
> **Last updated:** 2026-05-28
