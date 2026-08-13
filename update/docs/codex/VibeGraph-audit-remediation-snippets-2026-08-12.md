# Audit Remediation Snippets - 2026-08-12

Các mẫu dưới đây là khung thay thế tối thiểu cho từng finding trong
`audit-report-v2-2026-08-12.md`; cần điều chỉnh theo API/domain hiện tại và thêm regression test.

## Critical / High

| ID | Snippet đề xuất |
|---|---|
| C1 | `statusRepository.upsert(projectId, ANALYZING, progress, error);` và `@Scheduled(fixedDelayString = "${...}") void failStale() { statusRepository.markStaleAsFailed(cutoff); }` |
| H1 | `session.executeWrite(tx -> { writeChunk(tx, nodes); writeChunk(tx, edges); return null; });` rồi `try { ... } catch (RuntimeException e) { graphRepository.deleteProject(newProjectId); throw e; }` |
| H2 | `analysisExecutor.execute(() -> analyzeSafely(projectId)); return ResponseEntity.accepted().body(ApiResponse.success(jobId));` |
| H3 | `try (ExecutorService pool = boundedPool) { files.parallelStream().map(file -> parserFor(pool).parse(file)).toList(); }` (chỉ sau khi chứng minh parser/registry thread-safe) |
| H4 | `pg_dump --format=custom "$DATABASE_URL" > backups/postgres-$(Get-Date -Format yyyyMMdd).dump` + `neo4j-admin database dump neo4j --to-path=/backups` + restore rehearsal trong CI/runbook |
| H5 | `RUN addgroup --system app && adduser --system --ingroup app app` / `USER app`; `ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` |
| H6 | Xóa `- ./.env:/app/.env:ro`; truyền secrets qua runtime secret store hoặc chỉ `environment:` keys cần thiết |
| H7 | `AUTH_COOKIE_SECURE: ${AUTH_COOKIE_SECURE:-true}`; fail startup khi production + non-TLS: `if (prod && !secureCookies) throw new IllegalStateException(...)` |

## Medium

| ID | Snippet đề xuất |
|---|---|
| M1 | `MATCH (n:Symbol {projectId:$projectId}) RETURN n SKIP $nodeSkip LIMIT $nodeLimit` và query edges riêng với `LIMIT $edgeLimit`; mặc định limit phải > 0 |
| M2 | `MATCH (p:Project) WHERE p.ownerId IN $ownerIds RETURN p ...` hoặc SQL join theo `owner_id`; không load toàn tenant rồi filter Java |
| M3 | Xóa key không dùng (`vibegraph.parser.use-cache`, `FA2_ITERATIONS`) hoặc bind thật bằng `@ConfigurationProperties` + test đọc giá trị |
| M4 | `if (lastUsedAt.isBefore(now.minus(1, ChronoUnit.MINUTES))) repository.touchLastUsed(id, now);` |
| M5 | Dùng Redis/Rabbit relay cho session/rate-limit/import lock; tối thiểu ghi rõ `single-replica` trong deployment contract |
| M6 | CI parity check: `assert yamlKeys.symmetricDifference(envExampleKeys).isEmpty()`; sinh `.env.example` từ nguồn cấu hình chuẩn |
| M7 | Chỉ giữ `src/main/resources/db/migration`; bỏ `flyway.ignore-migration-patterns: "*:missing"` hoặc whitelist version cụ thể |
| M8 | CI: `docker compose config -q` và `docker build --check -f Dockerfile .` khi Docker/config thay đổi |
| M9 | `ports: ["127.0.0.1:5432:5432"]`; Neo4j APOC: `NEO4J_dbms_security_procedures_unrestricted: apoc.util.*` |
| M10 | `.dockerignore`: `.env`, `.env.*`, `.vibegraph/`, `logs/`, `qa-artifacts/`, `projects/`, `update/`, `task*/` |
| M11 | `component: () => import('@/views/GraphView.vue')`; thêm `manualChunks: { graph: ['sigma','graphology'] }` |
| M12 | `const { t } = useI18n(); <button>{{ t('graph.reset') }}</button>`; chuyển mọi user-visible string graph vào cả `en-US.json` và `vi-VN.json` |
| M13 | `for (let i=0; i<FA2_ITERATIONS; i++) { ... }`; `const seed = seededUnit(node.id);` thay `Math.random()` |
| M14 | `<img src="/assets/vibegraph-logo.webp" width="128" height="128" loading="lazy">`; convert source assets sang WebP/AVIF |
| M15 | Tách `useAdminUsersStore`, `useAdminSecurityStore`, `useAdminBillingStore`; tách `api/auth.ts`, `api/graph.ts`, `api/admin.ts` dùng chung Axios instance |
| M16 | `const { patchGraph } = useGraphPatcher()` và tách `GraphCanvas` thành `GraphToolbar`, `GraphSidebar`, `GraphStage`; giữ component dưới 300 dòng |

## Low / Hygiene

| ID | Snippet đề xuất |
|---|---|
| L1 | `const safeSvg = DOMPurify.sanitize(svg, { USE_PROFILES: { svg: true } });` rồi render `safeSvg`; thêm test payload `<script>` |
| L2 | `if (document.visibilityState !== 'hidden') await refresh(); document.addEventListener('visibilitychange', refresh);` và đặt `GRAPH_SAFE_NODE_LIMIT=3000` |
| L3 | `@RequiredArgsConstructor` + extract `ImportWorkspaceSupport`; xóa helper/import không còn caller |
| L4 | Dùng Spring CSRF token cookie/header: `csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))` |
| L5 | `if (environment.acceptsProfiles("prod") && props.isAllowUnconfined()) throw new IllegalStateException(...)` |
| L6 | `spring.datasource.hikari.maximum-pool-size: ${DB_POOL_MAX:10}`; Caffeine `expireAfterWrite`; scheduled cleanup cho workspace FAILED |
| L7 | Nếu không expose search: xóa `searchNodes`, delegate/cache/test và fulltext index; nếu expose: `QueryParser.escape(keyword)` + migration index đủ label |

## Database / additional findings

| ID | Snippet đề xuất |
|---|---|
| D1 | `Neo4jGraphRepository.java:235-256`: `MATCH (n:Symbol {projectId:$projectId}) ...` thay label-less match; chạy `PROFILE` trước/sau |
| D2 | `V2__symbol_label.cypher:30`, `Neo4jMigrationRunner.java:35-41`: `CALL { MATCH ... LIMIT $batch SET n:Symbol } IN TRANSACTIONS OF $batch ROWS` |
| D3 | `Neo4jGraphRepository.java:420-459`: dùng subquery/`LIMIT` trước `collect(dependent)` để tránh materialize toàn bộ traversal |
| D4 | `Neo4jGraphRepository.java:166-229,245-257`: `tx.run(...)` cho toàn bộ delete/upsert liên quan; invalidate cache sau `tx.commit()` |
| D5 | `CachingGraphRepository.java:48-68`: `maximumWeight(...).weigher((k,v) -> v.nodes().size()+v.edges().size())`; single-flight `cache.get(key, loader)` |
| D6 | `AdminAnnouncementService.java:28-34,95-105`, `JdbcAnnouncementRepository.java:31-34`: `LEFT JOIN users ... LIMIT :limit OFFSET :offset` |
| D7 | `JdbcFeedbackReportRepository.java:60-64,95-97`, `FeedbackReportService.java:79-90`: `LIMIT :limit OFFSET :offset`/keyset thay full-table read |
| D8 | `JdbcFeedbackReportRepository.java:111-119`, `V1__init_realtime_storage.sql:24-27`: `CREATE INDEX ... USING gin (lower(title) gin_trgm_ops)` |
| D9 | `JdbcAnnouncementRepository.java:53-57`, `V1__init_realtime_storage.sql:108-109`: index `ends_at` partial + batched delete |
| D10 | `JdbcRequestEventRepository.java:136-149`: `ROW_NUMBER() OVER (PARTITION BY ip_address ORDER BY count(*) DESC) AS rn` rồi `WHERE rn <= :perIpLimit` |
| D11 | `JdbcFeedbackMessageRepository.java:46-50`, `FeedbackReportService.java:99-107`: `WHERE (created_at,id) > (:cursorTime,:cursorId) ... LIMIT :limit` |
