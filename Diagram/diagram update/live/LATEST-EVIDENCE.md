# Live Diagram Evidence Status

- Evidence cycle ID: `2204b1e59dd24590a24422935956b15f`
- Last checked: `2026-08-14T11:40:06.8572339+07:00`
- Repository HEAD: `e8c79e3115114d9d381332391e4d6d7f7aa90977`
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

- Observed at: `08/14/2026 11:38:37`
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
 M update/docs/Qwen/EXECUTION-REPORT-DOT4-7-2026-08-13.md
?? scripts/drills/README.md
?? scripts/drills/nginx-t2-drill.conf
?? update/docs/Qwen/SESSION-REPORT-BM2-FM6-SPLIT-2026-08-14.md
?? update/docs/Qwen/SESSION-REPORT-DOT-4ITEMS-2026-08-14.md
?? update/docs/Qwen/SO-SANH-TRUOC-SAU-UPGRADE-2026-08-14.md
```

## Files

- Rolling JSON state: `CURRENT-STATE.json`
- Fixed accepted baseline: `AUDITED-BASELINE.json`
- Change-only event history: `DRIFT-HISTORY.jsonl`
- Watcher heartbeat/status: `WATCHER-STATUS.json`
- Watcher log/PID: `WATCHER.log`, `watcher.pid`
