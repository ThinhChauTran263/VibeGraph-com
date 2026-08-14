# 06 — Backend Import Performance

Separate workstream from the graph rendering fixes. No dependency between them.

All findings verified by reading source. Paths relative to repo root.

---

## Pipeline overview

`AnalyzeServiceImpl.analyzeProject` (`src/main/java/com/vibegraph/graph/service/impl/AnalyzeServiceImpl.java:39–127`):

```
PARSE_START_PCT = 5      (line 34)
  parserService.parseProject(...)          ← dominant phase
PARSE_END_PCT = 70       (line 36)
  72%  "Building relationships"            (line 57)
       accumulate all nodes/edges          (59–67)
       size-limit check                    (72–77)
       projectContainsPackageEdges         (84)
       EventFlowResolver.inferTriggers     (89)
       DynamicDispatchResolver.inferDispatch (90)
       FlowAnalyzer.inferStepInFlow        (96)
  80%  "Saving nodes"                      (98)
  94%  "Saving relationships"              (105)
       graphRepository.upsertAnalysis(...) (112) ← single blocking call
  98%  "Finalizing"                        (115)
```

---

## Finding 1 — Parsing is single-threaded ⚠️ biggest opportunity, highest risk

**File:** `src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java:426–439`

```java
int parsed = 0;
for (Path javaFile : javaFiles) {
    try {
        ParseResult result = parseFileInternal(javaFile, parser, projectSymbols);
        results.add(result);
    } catch (Exception e) { ... }
    parsed++;
    listener.onFileParsed(parsed, totalFiles);
}
```

A plain sequential loop over every `.java` file. This is the dominant phase — it owns 5%→70% of the progress budget (`AnalyzeServiceImpl.java:34–36`).

### ⚠️ Why this is NOT a simple `.parallelStream()`

A **single shared** `JavaParser` is built once at `ParserServiceImpl.java:423` (`createProjectParser`, defined at :477–498) and reused for every file. It carries a `JavaSymbolSolver` over a `CombinedTypeSolver` (:478–492).

**JavaParser's symbol solver is not documented as thread-safe.** Sharing one instance across threads risks corrupted resolution results or intermittent failures — which would silently degrade CALLS-edge accuracy rather than throw.

Safer shapes to evaluate:
- one `JavaParser` **per worker thread**, over a shared type solver — requires confirming the type solver's own thread-safety and cache behaviour
- partition files across threads, each with a fully independent parser (costs memory and loses shared cache benefit)

**Recommendation: do this last**, after everything else is green, and validate by diffing node/edge counts against a sequential run on the same repo.

---

## Finding 2 — Every file is read twice ✅ cheap and safe

`ParserServiceImpl.java:209` — inside `parseFileInternal`:
```java
lineCount(filePath),
```

`ParserServiceImpl.java:399–406`:
```java
private int lineCount(Path filePath) {
    try (var lines = Files.lines(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
        long count = lines.count();
        ...
```

This performs a **second complete read of every source file purely to count lines** — while the `CompilationUnit` produced moments earlier already carries range / end-line information.

**Fix:** derive the line count from the parsed AST. Roughly halves the parse phase's file I/O.

**Risk: low.** Only caveat — for files that fail to parse, the AST fallback won't exist; keep a fallback path for that case.

---

## Finding 3 — Whole graph written in one transaction (deliberate tradeoff)

`src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java:174–193`:
```java
try (Session session = neo4jDriver.session()) {
    return session.executeWrite(tx -> {
        runProjectUpsert(...);
        for (...) runNodeGroupUpsert(...);
        for (...) persisted += runEdgeGroupUpsert(...);
        return persisted;
    });
}
```

**This is intentional.** `AnalyzeServiceImpl.java:107–111` documents it: *"B-M11: one write transaction for the whole analysis graph — a mid-write failure rolls back everything instead of leaving a half-written project graph in Neo4j."*

**Do not remove the atomicity for speed.** Record it only as a scaling ceiling: memory and lock pressure grow with project size, and `AnalyzeServiceImpl.java:59–67` accumulates **all** nodes and edges in memory before the write.

There is a fail-fast guard at `AnalyzeServiceImpl.java:72–77` (`VIBEGRAPH_ANALYZE_MAX_NODES` / `MAX_EDGES`) that rejects oversized projects rather than risking OOM.

---

## Finding 4 — Progress reporting is misleading ✅ cheap

`AnalyzeServiceImpl.java:98–115` moves 80 → 94 → 98 around the **single blocking** `upsertAnalysis` call at :112.

The user sees the bar sit at 94% for the entire database write with no movement. **Part of the perceived slowness of import is this, not actual elapsed time.**

**Fix:** emit incremental progress from inside `upsertAnalysis` — it already iterates label groups (`Neo4jGraphRepository.java:183–189`), so per-group callbacks are straightforward.

---

## Suggested order

| # | Change | Impact | Risk |
|---|---|---|---|
| 1 | Remove the double file read (Finding 2) | Medium | **Low** |
| 2 | Real progress during DB write (Finding 4) | Perceived only, but real UX win | **Low** |
| 3 | Parallelise parsing (Finding 1) | **Highest** | **High** — thread-safety |

Leave Finding 3 alone.

---

## Not investigated

- `FileUtils.scanJavaFiles` cost on large trees
- `detectSourceRoots` (`ParserServiceImpl.java:506+`) — does a `Files.walk(projectRoot, 6)` (:510) and calls `readPackageDeclaration` (:566), which opens files again. **Possibly a third read pass over some files — worth checking.**
- The inference passes (`EventFlowResolver`, `DynamicDispatchResolver`, `FlowAnalyzer`) were never profiled. They sit between 72% and 80% of the progress bar; their real cost is unknown.
