# 07 — Open Questions, Traps, and Corrections

Read this before acting on anything in the other files.

---

## PART A — Unresolved questions (verify before relying on them)

### A1. ⚠️ BLOCKING — Does Sigma's hit-testing use scaled or raw node size?

**Why it matters:** with `ZOOM_SIZE_POWER = 1.0`, rendered node radius changes much more aggressively with zoom. If Sigma's mouse-picking compares against the **raw** `size` attribute rather than `scaleSize(size)`, the clickable area will drift away from the drawn circle as you zoom — clicking a large node would miss.

**Status: NOT VERIFIED.** I read `scaleSize` and the render path, but never traced the picking path.

**How to resolve:** in `vibegraph-web/node_modules/sigma/dist/sigma.esm.js`, locate the quadtree / `getNodeAtPosition` / mouse-move hover path and check whether the radius it compares passes through `scaleSize()`. Note Sigma 3 also ships a **WebGL picking mode** (`#ifdef PICKING_MODE` appears in the shaders, e.g. `index-fad77a13.esm.js:789`, `:914`) — determine which mechanism is actually active in this configuration, because they may behave differently.

**If it turns out to use raw size:** Step 1 still stands, but a compensating fix for picking is required before shipping.

### A2. What exactly is `K`?

`K = graphToViewportRatio × cameraRatio` — constant for a given graph extent + viewport, but its **magnitude was never measured**.

The estimate "plausibly in the hundreds" in `02-SIGMA-INTERNALS.md` §2 is reasoning from viewport width (~1048 px) against Sigma's normalized graph box — **not a measurement**.

Measure it at runtime during Step 2 rather than assuming. It also determines how badly a mistaken `itemSizesReference` flip would blow up sizes.

### A3. Does grapuco precompute layout server-side?

Their bundle contains `cooldownTicks: 0` and `warmupTicks: 0`, and their graph renders essentially instantly once data arrives. That **suggests** precomputed coordinates with no in-browser simulation.

**But this is inference, not proof.** Those values are very likely force-graph's own prop defaults, not app configuration. Their React fiber tree was unreachable (`Object.keys(container)` returned only `["__on"]`; no `__reactContainer*` found anywhere), so the live ForceGraph instance and its real props could not be read.

**Do not design around this assumption.** If backend-precomputed layout ever becomes a serious option for VibeGraph, evaluate it on its own merits.

### A4. Are grapuco's actual d3-force parameters knowable?

No. Only the library identity is confirmed (`force-graph`, `ngraph`, `forceCollide` — `04-GRAPUCO-REFERENCE.md` §2). Specific charge strength / collide radius / link distance were **not** obtained.

A further attempt to read their `graph/schema` payload was **blocked by policy** (would have required using the user's stored auth token). Not pursued.

### A5. Does VibeGraph's renderer actually freeze under normal use?

During testing, CDP `Page.captureScreenshot` timed out at 30 s twice and `Runtime.evaluate` at 45 s once, plus a stale-frame compositor artifact.

**Partial attribution caveat:** much of the instability followed a burst of **66 synthetic wheel events** — not normal user input. **However, the first freeze occurred immediately after layout settle, before any synthetic input.**

**Status: UNCONFIRMED.** Reproduce with ordinary interaction (real scrolling, clicking) before treating "renderer freezes" as a real defect. Measured `wheelFps: 33` during active zoom, and 8 canvas layers at 1414×1155 each.

### A6. Unprofiled backend costs

- `detectSourceRoots` (`ParserServiceImpl.java:506+`) does `Files.walk(projectRoot, 6)` (:510) and calls `readPackageDeclaration` (:566) which opens files again — **possibly a third read pass** over some files. Not measured.
- The inference passes (`EventFlowResolver.inferTriggers`, `DynamicDispatchResolver.inferDispatch`, `FlowAnalyzer.inferStepInFlow` — `AnalyzeServiceImpl.java:89–96`) occupy the 72–80% band of the progress bar. Their real cost is **unknown**.

---

## PART B — Traps

### B1. 🔴 Do NOT change `itemSizesReference` to `'positions'`
The single most likely way to waste a day. In Sigma 3.0.3 it does **not** change zoom scaling — it only multiplies all sizes by the constant `K`. Nodes will explode in size and the graph will look broken, making the whole approach seem wrong when it isn't. Full derivation: `02-SIGMA-INTERNALS.md` §2.

### B2. 🔴 Do NOT ship `ZOOM_SIZE_POWER = 1.0` without lowering `SIGMA_EDGE_SIZE`
Edges will begin thickening at ~11× zoom instead of ~25×. And note `runtimeConfig.ts:166` has `{ min: 0.05 }` — **that floor must be lowered too**, or the new value is silently clamped and the fix appears not to work. `02-SIGMA-INTERNALS.md` §5.

### B3. 🟠 Do NOT trust comments in `runtimeConfig.ts`
Three are provably false (lines 207, 211–215, 258–259). Details and quotes: `01-EVIDENCE-LOG.md` §4.

### B4. 🟠 Do NOT tune the four dead knobs
`FA2_ITERATIONS`, `FA2_ITERATIONS_LARGE`, `NOVERLAP_MAX_ITERATIONS`, `FA2_OUTLIER_CLAMP_PERCENTILE` are referenced nowhere. Changing them does **nothing**. `01-EVIDENCE-LOG.md` §5.

### B5. 🟠 Do NOT hide `Field` nodes "to reduce clutter"
Already hidden by default. `01-EVIDENCE-LOG.md` §6.4.

### B6. 🟠 Do NOT trim edge types to make the graph cleaner
VibeGraph has 18 edge types vs grapuco's 10 and renders **fewer** total nodes and edges while still looking more crowded. The richer taxonomy is an asset; the layout is the problem. `01-EVIDENCE-LOG.md` §8, `04-GRAPUCO-REFERENCE.md` §8.

### B7. 🟠 Do NOT remove the single-transaction Neo4j write for speed
It is a deliberate atomicity guarantee ("B-M11"). `06-IMPORT-PERFORMANCE.md` Finding 3.

### B8. 🟠 Do NOT rebuild Flows / Data Flow
Already shipped in VibeGraph (`lib/dataFlow.ts`, `lib/flowFocus.ts`). grapuco charges Pro for the equivalent. `04-GRAPUCO-REFERENCE.md` §6.

### B9. 🟡 Do NOT copy grapuco's label strategy
They switch all labels on at one zoom threshold with no density culling, producing unreadable text soup at that threshold. VibeGraph's handling is more sophisticated. `04-GRAPUCO-REFERENCE.md` §5.

### B10. 🟡 Do NOT naively `.parallelStream()` the parse loop
A single `JavaParser` + `JavaSymbolSolver` is shared across all files (`ParserServiceImpl.java:423`). Not documented as thread-safe; failures would likely be silent accuracy degradation rather than exceptions. `06-IMPORT-PERFORMANCE.md` Finding 1.

---

## PART C — Corrections made during the investigation

Recorded so nobody re-derives a superseded conclusion from earlier notes or chat logs.

### C1. "VibeGraph nodes stay a fixed 8–18 px at every zoom" — **WRONG**
`ZOOM_SIZE_POWER = 0.75` means they **do** grow, as `r^(−0.75)`. Corrected model: overlap has three compounding layers, not one cause. `03-ROOT-CAUSE.md`.

### C2. "Hide `Field` by default — biggest visual win for the least work" — **WITHDRAWN**
It is already hidden by default. This correction actually **strengthened** the diagnosis: VibeGraph renders fewer nodes than grapuco and still looks worse, eliminating density as a cause.

### C3. "grapuco's labels overlap badly" — **OVERSTATED**
True only at their label-reveal threshold. A few ticks deeper and text reads cleanly. It is a transitional problem, not permanent.

### C4. "grapuco's repository page is broken / hangs" — **WRONG**
It is very slow (~1 minute on first load), not broken. It eventually loaded fully: 2,020 nodes / 5,402 edges.

### C5. "Switch `itemSizesReference` to `'positions'` for graph-space sizing" — **WRONG, and dangerous**
This was the original plan for Option B. Reading Sigma's source showed it only changes a constant factor. The real lever is `ZOOM_SIZE_POWER`. This correction is the main reason the research phase was worth doing before coding.

### C6. "minZoom clamp is a nice-to-have (priority 5)" — **RE-RANKED**
With `p = 0.75` it is **structurally required**: overlap worsens as you zoom out, and with no clamp there is no worst case to solve for, making the problem unsolvable. After moving to `p = 1` it drops back to a UX nicety. Either way it belongs in the same commit as the sizing change.

---

## PART D — Session provenance

- Investigated 2026-08-13 by Claude Opus 5, ended at the user's weekly quota limit.
- Browser work used the user's own logged-in Chrome via the `claude-in-chrome` MCP tools.
- grapuco account state at time of testing: FREE plan, **−5 credits**. The RAG "Chat" feature was deliberately **not** exercised to avoid consuming credits.
- One action was **blocked by policy**: reading grapuco's `graph/schema` payload using the user's stored bearer token. Not worked around.
- Both products were compared on the **same** source repository (`fatc-Grocery-Store`), which is what makes the node/edge count comparison in `01-EVIDENCE-LOG.md` §8 meaningful.
