# Changes From The Old Diagram Folder

This index answers what changed between the original `Diagram/` artifacts and the
evidence-backed artifacts in this folder. It is an index, not a second source of truth:
the detailed files under `changes/` contain the page-level mapping and source/migration/
runtime references.

## Verification boundary

- **Old side:** files that were already present under `Diagram/` at the audit snapshot.
- **Current side:** files under `Diagram/diagram update/`, checked against the current
  source tree, checked-in migrations, local PostgreSQL/Neo4j runtime, and recorded tests.
- **Evidence rule:** a claim is included only when a path, migration, runtime query, or
  test/build result supports it. Unknown or stale claims are listed as exclusions.
- **Concurrent work:** the watcher records later worktree drift separately; it does not
  silently promote another session's edits into the audited baseline.

## Artifact mapping

| Old artifact | Current artifact | What changed | Evidence class | Detailed record |
| --- | --- | --- | --- | --- |
| `Diagram/plantuml_usecase.md` and `Diagram/1.Usecase Diagram` | `plantuml_usecase.md` and `1.Usecase Diagram` | Rebuilt the use-case boundary from actual controllers, frontend behavior, MCP registration, and admin routes. Removed unsupported forgot-password, email-verification, GitLab, 3D graph, broad export, and unproven class/sequence API claims. Separated watcher broadcast from user graph exploration. | SOURCE, TEST | [`changes/usecase.changes.md`](changes/usecase.changes.md) |
| `Diagram/plantuml_activity.md` and `Diagram/2.Activity Diagram` | `plantuml_activity.md` and `2.Activity Diagram` | Split real execution paths: import-owned async executor versus manual analyze scheduler; documented refresh-session rotation, watcher slice replacement, graph payload guards, UML response rendering, MCP callbacks, and admin SSE streams. | SOURCE, MIGRATION, TEST | [`changes/activity.changes.md`](changes/activity.changes.md) |
| `Diagram/plantuml_erd_component_class.md` and `Diagram/3.ERD Diagram` | `plantuml_erd_component_class.md` and `3.ERD Diagram` | Rebuilt PostgreSQL ERD from migrations/runtime: 21 domain tables, 23 physical FKs, 66 domain-table indexes and 68 public-schema indexes including Flyway metadata. Corrected Neo4j relationship endpoints, added current non-zero `INSTANTIATES`, `CATCHES`, and `STEP_IN_FLOW`, separated schema-only `Route` from emitted `APIEndpoint`, and explicitly recorded persisted legacy `ANNOTATED_BY`. | MIGRATION, RUNTIME, SOURCE, TEST | [`changes/erd.changes.md`](changes/erd.changes.md) |
| Component section of `Diagram/plantuml_erd_component_class.md` and `Diagram/4.1.Component_Deployment Diagram` | Component section and `4.1.Component_Deployment Diagram` | Made the checked-in Docker topology explicit: frontend/nginx, Spring Boot, PostgreSQL, Neo4j, writable mounts, health dependencies, STOMP, and MCP. Services use the `GraphRepository` boundary; runtime graph query/persistence access is isolated to `Neo4jGraphRepository`, while `Neo4jMigrationRunner` uses the driver only for schema migrations. Optional PostgreSQL-compatible realtime/high-volume storage is marked disabled by default. | SOURCE, RUNTIME, TEST | [`changes/component_class.changes.md`](changes/component_class.changes.md) |
| Class section of `Diagram/plantuml_erd_component_class.md` and `Diagram/4.2.Class Diagram` | Class section and `4.2.Class Diagram` | Corrected `ParseResult` to a class, added the `FileWatcherService` abstraction and `CachingGraphRepository` realization, removed the unsupported `MeteredToolCallback -> GraphRepository` dependency, and represented refresh sessions, admin streams, graph guards, scheduler, parser/inference helpers, and MCP callback wiring. | SOURCE, TEST | [`changes/component_class.changes.md`](changes/component_class.changes.md) |
| `Diagram/VibeGraph_All_PlantUML_Diagrams.md` | `VibeGraph_All_PlantUML_Diagrams.md` | Converted the combined file into an exact mirror of the three canonical current PlantUML files, with copy markers and 18 balanced blocks. | SOURCE, TEST | [`README.md`](README.md) and [`BASELINE-MANIFEST.md`](BASELINE-MANIFEST.md) |
| `Diagram/3.VibeGraph_ProjectReportDocument(Loading).docx` | `3.VibeGraph_ProjectReportDocument(Updated).docx` | Rewrote the report around the same evidence boundary and corrected stale execution-flow and capability claims. OOXML structure and rendered pages were checked. | SOURCE, MIGRATION, RUNTIME, TEST | [`README.md`](README.md) and [`BASELINE-MANIFEST.md`](BASELINE-MANIFEST.md) |

## Concurrent refactors audited as non-semantic

The following changes were observed in the shared worktree and checked for diagram
impact. They do not add a new actor, endpoint, database table, migration, relationship
vocabulary, or deployment component, so canonical diagram semantics remain unchanged:

| Observed change | Evidence-backed conclusion |
| --- | --- |
| `vibegraph-web/src/views/admin/UserApiKeyList.vue` extraction | Presentation extraction; parent keeps the existing `disable`, `lock`, and `unlock` handlers and store operations. |
| Dashboard ECharts/helper split (`DashboardView.vue`, `dashboard-echarts.ts`, `dashboard-transforms.ts`) | Loading/performance and pure-transform refactor; no API/domain/schema change found. |
| Admin/user-detail formatter/helper extraction | Presentation/helper refactor; no new capability found. |
| `UseCaseInferenceEngine` helper split | Behavior was extracted into helpers; current inference flow and diagram evidence remain the same. |
| `Neo4jGraphRepository.getFullGraph` read-path refactor | Same graph snapshot contract; no migration, label, or relationship change found. |
| `scripts/drills/*` | Verification drill/example material, not evidence that TLS/reverse proxy is deployed in production. |

## Explicitly not current capabilities

No current diagram asserts backend class/sequence diagram APIs, a 3D graph view,
GitLab import, forgot-password/email-verification endpoints, broad backend SVG export,
or a six-tool MCP surface. The frontend's evidenced PNG download is not removed by this
exclusion. The detailed change notes record the source checks behind
these exclusions.

## Live revalidation

The rolling watcher writes observed drift and validation results to
[`live/LATEST-EVIDENCE.md`](live/LATEST-EVIDENCE.md) and machine-readable state to
`live/CURRENT-STATE.json`. `live/AUDITED-BASELINE.json` is changed only by an explicit
`-AcceptCurrentBaseline` invocation after all evidence gates pass.
