# VibeGraph Diagram Update

This folder is an evidence-backed update of the diagrams under `Diagram/`. It is based on
the source, migrations, local runtime and test boundary recorded in `BASELINE-MANIFEST.md`,
not on assumptions copied from the older report.

## Evidence policy

Every architectural claim in this folder is classified as one of:

- **SOURCE** - observed in current source/configuration, with a path and line reference.
- **MIGRATION** - declared by a checked-in SQL/Cypher migration.
- **RUNTIME** - observed from the local PostgreSQL/Neo4j containers at the snapshot time.
- **TEST** - demonstrated by a test or build command recorded in the manifest.
- **UNKNOWN** - not proven; it is intentionally excluded from the canonical diagrams.

The old diagrams remain untouched. A statement is not copied into the new diagrams merely
because it appears in the old DOCX, PlantUML, or draw.io file.

## Canonical files

| File | Purpose |
| --- | --- |
| `plantuml_usecase.md` | Current business use cases and real route groups. |
| `plantuml_activity.md` | Current authentication, import/analyze, watcher, graph, MCP, and admin flows. |
| `plantuml_erd_component_class.md` | Current PostgreSQL/Neo4j schema, deployment and class/module views, plus checked-in optional PostgreSQL-compatible realtime/high-volume migration/configuration that is disabled by default. |
| `VibeGraph_All_PlantUML_Diagrams.md` | Combined copy of the three canonical PlantUML sources. |
| `1.Usecase Diagram` ... `4.2.Class Diagram` | Uncompressed diagrams.net companions preserving the verified `10 / 6 / 2 / 1 / 2` page inventory. Page inventory preservation is not a claim of one-to-one semantic equivalence with PlantUML sections. |
| `3.VibeGraph_ProjectReportDocument(Updated).docx` | Evidence-backed project report replacing stale claims from the old DOCX. |
| `changes/*.changes.md` | Explicit old-versus-current evidence for each diagram family. |
| `CHANGES-FROM-OLD.md` | Cross-family old → current index, including audited concurrent refactors. |
| `BASELINE-MANIFEST.md` | Evidence boundary, artifact inventory, recorded checks, and timestamped runtime snapshots. |
| `scripts/generate-updated-diagrams.ps1` | Regenerates the five diagrams.net companion files. |
| `scripts/sync-diagram-plantuml.ps1` | Regenerates the exact combined PlantUML mirror. |
| `scripts/update-diagram-report.py` | Reconciles the updated DOCX content and package metadata. |
| `scripts/update-diagram-evidence.ps1` | Performs one-shot or continuous evidence capture without rewriting canonical diagrams. |
| `live/LATEST-EVIDENCE.md` | Rolling evidence report after the watcher completes a cycle; not semantic approval. |
| `live/WATCHER-STATUS.json` | Atomic heartbeat created by the watcher, including failures and the last committed cycle ID. |

## Deliberate exclusions

The current code does not provide evidence for backend class-diagram or sequence-diagram
generation endpoints, a 3D graph view, GitLab import, email-verification/password-reset
endpoints, or a six-tool MCP surface. Those claims are recorded as stale/unknown in the
change notes instead of being drawn as current behavior.

## Revalidation

The runtime counts in this folder are a timestamped snapshot. Re-run the evidence commands
listed in `BASELINE-MANIFEST.md` before treating counts as current after the database changes.

## Continuous evidence watcher

The watcher observes source/configuration/migration/test evidence and local PostgreSQL/Neo4j
runtime without rewriting canonical diagrams. It separates the fixed audited baseline from
the current rolling state and records only observed drift.

```powershell
# One-shot refresh (does not accept a new baseline)
pwsh -NoProfile -File scripts/update-diagram-evidence.ps1

# Accept the reviewed current state only after every validation gate passes
pwsh -NoProfile -File scripts/update-diagram-evidence.ps1 -AcceptCurrentBaseline

# Start continuous observation in a hidden background process
Start-Process pwsh -WindowStyle Hidden -ArgumentList @(
  '-NoProfile', '-File', 'scripts/update-diagram-evidence.ps1',
  '-Watch', '-IntervalSeconds', '20', '-RuntimeEveryCycles', '6'
)

# Stop only the watcher whose recorded process identity matches
pwsh -NoProfile -File scripts/update-diagram-evidence.ps1 -Stop
```

`-RunFastChecks` enables backend compile and frontend type-check; in watch mode they run
only on `-FastChecksEveryCycles` (default `6`) to avoid overlapping expensive checks. The
watcher performs structural XML/PlantUML/DOCX/reference validation; it does not claim that
these checks replace semantic review or a diagram renderer.

Read `live/WATCHER-STATUS.json` before relying on the rolling report. `refresh-complete`
means that the mechanical capture finished; `refresh-complete-with-warnings` means the
state/report committed but an auxiliary history/log append failed. Neither is a semantic
approval. A `refresh-failed` status leaves `CURRENT-STATE.json` at the prior committed cycle.
The report is either still on that cycle or contains an uncommitted next cycle; the required
cycle-ID comparison detects the latter instead of presenting it as a successful refresh.

`CURRENT-STATE.json` and `LATEST-EVIDENCE.md` carry the same `cycleId`. Each file is replaced
atomically, the report is written first, and `CURRENT-STATE.json` is written last as the
commit marker. Consumers reading both files must reject a cycle-ID mismatch, which denotes
an in-progress or interrupted multi-file update. The JSONL history is appended only after
both rolling outputs commit. Git consistency/status capture also excludes `live/**`, so the
watcher's own generated files cannot create self-drift.

Baseline acceptance is explicit and fail-closed. The script performs two independent source
and Git captures before and after replacing `AUDITED-BASELINE.json`; if another session
changes evidence during that acceptance window, the prior baseline is restored and the
command fails. Changes that occur after the acceptance boundary are future drift and require
another one-shot refresh or a running watcher.
