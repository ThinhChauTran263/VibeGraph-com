# Live Diagram Evidence Status

- Evidence cycle ID: `06781111c05246da95a01280d781d1c3`
- Last checked: `2026-08-14T11:31:47.6292631+07:00`
- Repository HEAD: `e31f329ccbbe37c53e9010acb0cf1e534f733c2c`
- Branch: `backup-full-fixed-20260728`
- Explicitly accepted evidence baseline: `2026-08-14T11:14:12.4973275+07:00`
- Drift from audited baseline: **0 file(s)**
- Structural artifact checks: **STRUCTURAL PASS**
- Evidence reads: **CAPTURE PASS**
- Whole-cycle consistency: **CAPTURE PASS**
- Git evidence: **CAPTURE PASS**
- Semantic review: **NOT_PERFORMED** (never performed by this watcher)

Every PASS label in this report is mechanical and scoped to the named check. This report
is generated evidence, not a semantic approval. Potential diagram-family
labels are triage hints only. The watcher never edits canonical PlantUML, diagrams.net,
DOCX, production code, migrations, or the accepted baseline unless explicitly invoked with
`-AcceptCurrentBaseline`.

## Drift requiring audit

No evidence-scope file differs from the accepted audited baseline.

## Changes since previous watcher cycle

No evidence-scope file changed since the previous successful cycle.

## Artifact validation

| Artifact/check | Structural result | Detail |
| --- | --- | --- |
| `plantuml_usecase.md` | STRUCTURAL PASS | markers=7/7 |
| `plantuml_activity.md` | STRUCTURAL PASS | markers=6/6 |
| `plantuml_erd_component_class.md` | STRUCTURAL PASS | markers=5/5 |
| `VibeGraph_All_PlantUML_Diagrams.md` | STRUCTURAL PASS | markers=18/18; exactCopies=plantuml_usecase.md=True,plantuml_activity.md=True,plantuml_erd_component_class.md=True |
| `1.Usecase Diagram` | STRUCTURAL PASS | pages=10/10; duplicateIds=0; badEdges=0 |
| `2.Activity Diagram` | STRUCTURAL PASS | pages=6/6; duplicateIds=0; badEdges=0 |
| `3.ERD Diagram` | STRUCTURAL PASS | pages=2/2; duplicateIds=0; badEdges=0 |
| `4.1.Component_Deployment Diagram` | STRUCTURAL PASS | pages=1/1; duplicateIds=0; badEdges=0 |
| `4.2.Class Diagram` | STRUCTURAL PASS | pages=2/2; duplicateIds=0; badEdges=0 |
| `3.VibeGraph_ProjectReportDocument(Updated).docx` | STRUCTURAL PASS | valid OOXML package; entries=19 |
| `repo-relative numeric evidence references` | STRUCTURAL PASS | checked=185 |

## Runtime snapshot

- Observed at: `08/14/2026 11:29:58`
- Runtime data is reused from the prior collection; it is not newly queried this cycle.
- `vibegraph-postgres`: status=running, health=healthy, image=postgres:16.11-alpine
- `vibegraph-neo4j`: status=running, health=healthy, image=neo4j:5.26-community
- PostgreSQL: domain tables=21, migrations=19, FKs=23, indexes=68
- Neo4j: nodes=56724, relationships=116987

## Fast checks

Fast build checks are disabled for the watcher. Use `-RunFastChecks` for a one-shot
backend compile and frontend type-check.

## Current worktree

```text
 M src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java
 M src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java
 M src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineHelperTest.java
 M update/docs/Qwen/EXECUTION-REPORT-DOT4-7-2026-08-13.md
 M vibegraph-web/src/views/admin/DashboardView.vue
 M vibegraph-web/src/views/admin/UserDetailDrawer.vue
 M vibegraph-web/src/views/admin/__tests__/DashboardView.spec.ts
 M vibegraph-web/src/views/admin/__tests__/UserDetailDrawer.spec.ts
?? "Diagram/1.Usecase Diagram"
?? "Diagram/2.Activity Diagram"
?? "Diagram/3.ERD Diagram"
?? Diagram/3.VibeGraph_ProjectReportDocument(Loading).docx
?? "Diagram/4.1.Component_Deployment Diagram"
?? "Diagram/4.2.Class Diagram"
?? Diagram/VibeGraph_All_PlantUML_Diagrams.md
?? "Diagram/diagram update/1.Usecase Diagram"
?? "Diagram/diagram update/2.Activity Diagram"
?? "Diagram/diagram update/3.ERD Diagram"
?? "Diagram/diagram update/3.VibeGraph_ProjectReportDocument(Updated).docx"
?? "Diagram/diagram update/4.1.Component_Deployment Diagram"
?? "Diagram/diagram update/4.2.Class Diagram"
?? "Diagram/diagram update/BASELINE-MANIFEST.md"
?? "Diagram/diagram update/CHANGES-FROM-OLD.md"
?? "Diagram/diagram update/README.md"
?? "Diagram/diagram update/VibeGraph_All_PlantUML_Diagrams.md"
?? "Diagram/diagram update/changes/activity.changes.md"
?? "Diagram/diagram update/changes/component_class.changes.md"
?? "Diagram/diagram update/changes/erd.changes.md"
?? "Diagram/diagram update/changes/usecase.changes.md"
?? "Diagram/diagram update/plantuml_activity.md"
?? "Diagram/diagram update/plantuml_erd_component_class.md"
?? "Diagram/diagram update/plantuml_usecase.md"
?? Diagram/plantuml_activity.md
?? Diagram/plantuml_erd_component_class.md
?? Diagram/plantuml_usecase.md
?? scripts/drills/README.md
?? scripts/drills/nginx-t2-drill.conf
?? scripts/generate-updated-diagrams.ps1
?? scripts/sync-diagram-plantuml.ps1
?? scripts/update-diagram-evidence.ps1
?? scripts/update-diagram-report.py
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseActorGuesser.java
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseClassFallback.java
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseDomainGuesser.java
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseEndpointRules.java
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseModelMerge.java
?? src/main/java/com/vibegraph/diagram/service/impl/UseCaseNameNormalizer.java
?? src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineGraphFixtureTest.java
?? update/docs/Qwen/SESSION-REPORT-BM2-FM6-SPLIT-2026-08-14.md
?? update/docs/Qwen/SESSION-REPORT-DOT-4ITEMS-2026-08-14.md
?? update/docs/Qwen/SO-SANH-TRUOC-SAU-UPGRADE-2026-08-14.md
?? vibegraph-web/src/views/admin/UserApiKeyList.vue
?? vibegraph-web/src/views/admin/__tests__/dashboard-transforms.spec.ts
?? vibegraph-web/src/views/admin/__tests__/user-detail-format.spec.ts
?? vibegraph-web/src/views/admin/dashboard-echarts.ts
?? vibegraph-web/src/views/admin/dashboard-transforms.ts
?? vibegraph-web/src/views/admin/user-detail-format.ts
```

## Files

- Rolling JSON state: `CURRENT-STATE.json`
- Fixed accepted baseline: `AUDITED-BASELINE.json`
- Change-only event history: `DRIFT-HISTORY.jsonl`
- Watcher heartbeat/status: `WATCHER-STATUS.json`
- Watcher log/PID: `WATCHER.log`, `watcher.pid`
