# Structural accuracy evaluation (Phase 1)

Measures the graph VibeGraph generates for a **real Java repository** against an
**independent ground truth** counted directly from source, and emits a table for the
thesis Evaluation chapter.

## Why this is credible

- The ground truth is derived by **regex on comment/string-stripped `.java` source**, i.e.
  it does **not** use JavaParser — the engine under test. So the oracle is independent
  (no circular measurement).
- The primary signal is a **name-level set diff** (which exact types are missing/spurious),
  not just raw counts. Raw regex counts are noisy; the name diff is authoritative.

## Metrics

| Dimension | How measured |
|---|---|
| Type extraction (class / interface / enum) | name-level set match: source vs graph |
| Entity detection (`@Entity` -> DBModel) | count match |
| REST/MVC endpoint detection | count of the 5 verb-mapping annotations vs `APIEndpoint` nodes |
| Edge distribution (CALLS / HAS_METHOD / HANDLES_ROUTE / INJECTS) | reported from the graph |
| Method-call **precision** | Phase 2 (stratified manual sampling), not in Phase 1 |

## How to run

Prereqs: Neo4j up (`docker start vibegraph-neo4j`), backend on `:8080`.

```powershell
# 1) import the repo once (fire-and-forget; note the projectId from backend log)
#    OR let the script import it (pass no -ProjectId).
# 2) generate the report:
./run-eval.ps1 -RepoPath "<abs path to repo root>" -Name "<name>" -ProjectId "<id>"
```

Outputs `report.md` + `report.csv` in this folder.

Notes:
- Point `-RepoPath` at the repo root; the oracle counts `src/main/java` (production code).
  Import the **same** `src/main/java` path so tool and oracle share scope.
- `import-local`'s HTTP response can lag behind analysis; if so, read the `projectId`
  from the backend log and pass `-ProjectId` to skip the import step.

## Phase 1 result — spring-petclinic (main, 30 files)

| Element | Tool | Ground truth | Recall |
|---|---:|---:|---:|
| Types (class+iface+enum) | 25 | 25 | 100% |
| Entities | 6 | 6 | 100% |
| REST/MVC endpoints | 16 | 17 | 94.1% |

Name-level type diff: 0 missing, 0 spurious (exact).

Findings:
- Type + entity extraction is exact on this repo.
- One endpoint under-detected (94.1%) — candidate for Phase 2 root-cause.
- `INJECTS = 0`: spring-petclinic uses constructor injection, which the current
  annotation visitor does not capture as a dependency edge — a real, documented limitation.

## Next (Phase 2)

- Add 3-5 more repos (spring-petclinic-rest, a `@PreAuthorize` CRUD, a service-only lib).
- Method-call precision via stratified random sampling of CALLS edges + manual verification,
  reported with a confidence interval.
- Aggregate table + per-repo rows for the thesis.
