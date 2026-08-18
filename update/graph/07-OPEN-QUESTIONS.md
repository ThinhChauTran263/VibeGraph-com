# 07 — Open Questions, Traps, and Corrections

Read this before acting on anything in the other files.

---

## PART A — Unresolved questions (verify before relying on them)

### A1. ⚠️ BLOCKING — Does Sigma's hit-testing use scaled or raw node size?

**Why it matters:** with `ZOOM_SIZE_POWER = 1.0`, rendered node radius changes much more aggressively with zoom. If Sigma's mouse-picking compares against the **raw** `size` attribute rather than `scaleSize(size)`, the clickable area will drift away from the drawn circle as you zoom — clicking a large node would miss.

**Status: VERIFIED (2026-08-17, task T1 — DeepSeek V4 Pro).** Picking uses the **SCALED size** — in fact there is no CPU-side size comparison at all. Sigma 3.0.3 hit-testing is **WebGL framebuffer pixel picking**: the picking hit region is pixel-aligned with the drawn circle at every zoom level. **No compensating fix is required; Wave 2 is unblocked.** Full evidence chain below.

**How to resolve:** in `vibegraph-web/node_modules/sigma/dist/sigma.esm.js`, locate the quadtree / `getNodeAtPosition` / mouse-move hover path and check whether the radius it compares passes through `scaleSize()`. Note Sigma 3 also ships a **WebGL picking mode** (`#ifdef PICKING_MODE` appears in the shaders, e.g. `index-fad77a13.esm.js:789`, `:914`) — determine which mechanism is actually active in this configuration, because they may behave differently.

**If it turns out to use raw size:** Step 1 still stands, but a compensating fix for picking is required before shipping.

---

**Verification result (T1, 2026-08-17):** All paths below verified in `vibegraph-web/node_modules/sigma@3.0.3`. Paths abbreviated as `sigma.esm.js` = `vibegraph-web/node_modules/sigma/dist/sigma.esm.js`, `index.esm.js` = `vibegraph-web/node_modules/sigma/dist/index-fad77a13.esm.js`.

**Conclusion: picking uses the SCALED size — and the active mechanism is WebGL PICKING_MODE (framebuffer color picking), not quadtree distance testing. The clickable region is the rasterized node pixels themselves, so it stays aligned with the drawn circle at every zoom. The feared raw-size drift cannot occur. No compensating fix needed for `ZOOM_SIZE_POWER` 0.75 → 1.0.**

Evidence chain:

1. **Both hover and click route through `getNodeAtPosition`.**
   - Hover: `handleMove` calls `_this3.getNodeAtPosition(event)` — `sigma.esm.js:1551` (then emits `leaveNode`/`enterNode`, :1552–1572; the "still hovered?" re-check calls it again at :1567).
   - Click: the generic interaction listener (`click`, `doubleClick`, `down`, `up`, …) calls `_this3.getNodeAtPosition(event)` — `sigma.esm.js:1648` (emits `clickNode` :1649–1651 / `clickStage` :1658). The app consumes `clickNode`/`clickStage`/`doubleClickNode`/`downNode`/`enterNode`/`leaveNode` — `useSigma.ts:199/212/220/345/356/361`; all of them flow through `getNodeAtPosition`.
2. **`getNodeAtPosition` reads a pixel color from the node picking framebuffer — there is no radius comparison anywhere.**
   `sigma.esm.js:1520`: `var color = getPixelColor(this.webGLContexts.nodes, this.frameBuffers.nodes, x, y, this.pixelRatio, this.pickingDownSizingRatio);` then `colorToIndex` decodes the node ID from that color (:1521–1523). Edge picking (`getEdgeAtPoint`) is the same mechanism on the edges framebuffer (:1883). `getPixelColor` itself just does `gl.readPixels(bufferX, bufferY, 1, 1, ...)` — `colors-beb06eb2.esm.js:332–344`.
3. **The node WebGL context is created with a picking framebuffer** — `this.createWebGLContext("nodes", { picking: true })` (`sigma.esm.js:1330`); the framebuffer object is allocated in `createWebGLContext` (:2886–2893) and its texture is re-created on **every** `render()` via `resetWebGLTexture` (:2436–2438), so the pick buffer is always current.
4. **The picking geometry is drawn with the SAME size transform as the visible circle.** Each program renders twice per frame from one shared vertex buffer: first into the pick framebuffer (`pickProgram`, `index.esm.js:373–378` in `Program.render`), then to screen (:379–383). Both passes get the **same** `params` object from `getRenderParams`, which includes `sizeRatio: 1 / this.scaleSize()` — `sigma.esm.js:2766`. `NodeCircleProgram.setUniforms` binds it straight to the shader uniform: `gl.uniform1f(u_sizeRatio, params.sizeRatio)` — `index.esm.js:775`. In the node circle vertex shader (`SHADER_SOURCE$5`, `index.esm.js:706`), `float size = a_size * u_correctionRatio / u_sizeRatio * 4.0;` is computed **outside** the `#ifdef PICKING_MODE` branch — PICKING_MODE (same line, inside the shader string) only swaps the output color to the item ID (`v_color = a_id`) and the fragment shader (`SHADER_SOURCE$6`, `index.esm.js:702`) only disables antialiasing. So the picking silhouette and the drawn circle are byte-identical geometry. With `itemSizesReference: 'screen'` (default, `sigma-settings.esm.js:62`; app also sets it explicitly — `useSigma.ts:160`) `scaleSize(size) = size / zoomToSizeRatioFunction(cameraRatio)` (`sigma.esm.js:3629–3633`), i.e. exactly what the visible circle uses.
5. **The app's custom `zoomToSizeRatioFunction` feeds the same single path.** `useSigma.ts:78–80` defines `ZOOM_SIZE_POWER = 0.75` / `zoomToSizeRatio = ratio^0.75` and passes it as `zoomToSizeRatioFunction` (`useSigma.ts:161`). It is consumed by `scaleSize` only, and `scaleSize` feeds both the visible render and the pick buffer through `getRenderParams.sizeRatio`. After T3 changes the power to 1.0, visible nodes and the pick buffer change together — no divergence is possible.
6. **Which mechanism is active?** WebGL PICKING_MODE is the ONLY picking mechanism in Sigma 3.0.3 — the "quadtree picking" hypothesis in the original question does not exist in this bundle. The only quadtree-ish reference is a historical comment ("we used to rely on the quadtree for this" — label culling, `sigma.esm.js:2220`); no quadtree is imported, built, or queried anywhere in `sigma.esm.js`. The PICKING_MODE shaders cited at `index.esm.js:789`/`:914` are the edge arrow-head/line programs; the node-circle equivalents are `index.esm.js:702/706/775`. Picking shaders are compiled by prefixing `#define PICKING_MODE` (`index.esm.js:142`, `:174`) for every program whose layer was created with `picking: true` — nodes always (`sigma.esm.js:1330`), edges only if `enableEdgeEvents` (:1326), which the app does not enable (default `false`, `sigma-settings.esm.js:25`; no override in `useSigma.ts`).
7. **Buffer freshness:** every camera update schedules a full re-render (`bindCameraHandlers` → `scheduleRender`, `sigma.esm.js:1490–1496`); `render()` (key at `sigma.esm.js:2410`) computes `var params = this.getRenderParams()` at :2468 and draws every node/edge program with those params at :2472–2474, and the pick texture is reset at the start of each render (:2436). Hover checks also re-run `getNodeAtPosition` on every `mousemove` (:1551, :1567), so there is no stale-state window at any zoom.

Minor caveat (not blocking): the pick framebuffer runs at half viewport resolution (`pickingDownSizingRatio = 2 × pixelRatio`, `sigma.esm.js:1297`; pick viewport scaled by `/ downSizingRatio`, `index.esm.js:374`), so the hit boundary is quantized to ~1 pick-buffer pixel (≈2 CSS px) — imperceptible in practice and unrelated to the raw-vs-scaled question.

**Decision:** branch "picking dùng scaleSize" applies → Wave 2 proceeds normally. The T14 QA item "Click node ở nhiều mức zoom: vùng click khớp vòng tròn" remains as empirical confirmation.

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
