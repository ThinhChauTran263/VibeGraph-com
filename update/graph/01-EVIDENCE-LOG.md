# 01 — Evidence Log

Raw evidence with provenance. Nothing here is inference; inference lives in `03-ROOT-CAUSE.md`.

Repo paths are relative to repo root `D:\Users\User\IdeaProjects\VibeGraph`.

---

## 1. VibeGraph rendering configuration (verified by reading source)

File: `vibegraph-web/src/composables/useSigma.ts`

| Line | Fact |
|------|------|
| 78–80 | `const ZOOM_SIZE_POWER = 0.75` and `zoomToSizeRatio = (ratio) => Math.max(0.001, Math.pow(ratio, ZOOM_SIZE_POWER))` |
| 146–181 | Sigma constructor options block |
| 154 | `minEdgeThickness: SIGMA_MIN_EDGE_THICKNESS` |
| 159 | `labelRenderedSizeThreshold: LABEL_RENDERED_SIZE_THRESHOLD` |
| 160 | **`itemSizesReference: 'screen'`** |
| 161 | `zoomToSizeRatioFunction: zoomToSizeRatio` |
| — | **No `minCameraRatio` / `maxCameraRatio` are set** → Sigma defaults them to `null` = unbounded zoom |

Constants — file `vibegraph-web/src/lib/runtimeConfig.ts`:

| Line | Constant | Default |
|------|----------|---------|
| 137 | `SIGMA_LABEL_RENDERED_SIZE_THRESHOLD` | 8 |
| 166 | `SIGMA_EDGE_SIZE` | 0.25 |
| 174 | `SIGMA_MIN_EDGE_THICKNESS` | 2.8 |
| 264 | `LAYOUT_NORMALIZE_SPAN` | 9000 |
| 289 | `NOVERLAP_MARGIN` | 40 |
| 290 | `NOVERLAP_RATIO` | 2.7 |
| 292 | `NOVERLAP_AUTO_STOP_MS` | 22000 |
| 299 | `LAYOUT_SCREEN_OVERLAP_GAP_PX` | 3 |
| 302 | `LAYOUT_SCREEN_OVERLAP_ITERATIONS` | 10 |
| 307 | `LAYOUT_SCREEN_OVERLAP_STRENGTH` | (see file) |
| 314 | `LAYOUT_AUTO_STOP_MS` | 8000 |

Node sizes come from `NODE_SIZE_BY_TYPE` (`graphAdapter.ts:187–189` → `getNodeSize`), observed range **8–18**.

---

## 2. Layout pipeline (verified by reading source)

`useSigma.ts`:

- `init(graph)` (129–257): calls `dispose()` → constructs a **new** `Sigma` → `startLayout(graph)` at line 256.
- `startLayout` (469–495): starts `FA2Layout` worker; `setTimeout(..., LAYOUT_AUTO_STOP_MS)` at 490–494 → `stopLayout(true)`.
- `stopLayout(true)` (500–520) → `runPostLayoutPass(graph)` (522–545).
- `runPostLayoutPass`: `normalizeLayout` → `spreadLayoutClusters` → `centerLayout` → then starts `NoverlapLayout` with **only** `margin` and `ratio` (534–538), and `setTimeout(..., NOVERLAP_AUTO_STOP_MS)` at 544.
- `settleScreenOverlaps` (754–882): the only pass that does a correct px→graph-unit conversion (`unitsPerPixel` at 792, `node.radius *= unitsPerPixel` at 798). Runs at most `LAYOUT_SCREEN_OVERLAP_ITERATIONS` (line 805) with effective per-pair force `LAYOUT_SCREEN_OVERLAP_STRENGTH * 0.5` (line 851).
- `normalizeLayout` (547–579) rescales bounding box to `LAYOUT_NORMALIZE_SPAN`.
- `spreadLayoutClusters` (617–690) then scales clusters up — `mainClusterScale` capped at `1 + 1.08 = 2.08` (line 637). **This runs AFTER normalize, so the final span is larger than `LAYOUT_NORMALIZE_SPAN`.**

---

## 3. graphology-noverlap collision math (verified by reading node_modules)

File: `vibegraph-web/node_modules/graphology-layout-noverlap/iterate.js`

Line 151:
```js
collision = dist < s1 * ratio + margin + (s2 * ratio + margin);
```

Line 98:
```js
size = NodeMatrix[i + NODE_SIZE] * ratio + margin;
```

Defaults (`defaults.js`): `gridSize: 20, margin: 5, expansion: 1.1, ratio: 1.0, speed: 3`.

**`s1`/`s2` are the raw `size` node attribute, interpreted in the graph coordinate system.**

---

## 4. Provably wrong / stale documentation in the codebase

These are **verified false** and will mislead anyone reading the code.

### 4.1 `runtimeConfig.ts:258–259`
```
 * Node sizes are rendered in the SAME layout-coordinate space (see Sigma
 * `itemSizesReference: 'positions'`), so a fixed span makes a node of `size` s
```
**FALSE** — `useSigma.ts:160` sets `itemSizesReference: 'screen'`, not `'positions'`.

### 4.2 `runtimeConfig.ts:211–215`
```
// The reference "grapuco" look is standard ForceAtlas2 (NOT LinLog) with strong
// repulsion + dissuade-hubs: ...
```
**FALSE** — grapuco.com does not use ForceAtlas2 at all. See §7 below and `04-GRAPUCO-REFERENCE.md`.

### 4.3 `runtimeConfig.ts:207`
```
/** Synchronous ForceAtlas2 iterations run once before first paint (no live animation). */
export const FA2_ITERATIONS = envInt('VITE_FA2_ITERATIONS', 700, { min: 1 })
```
**FALSE** — `useSigma.ts:474` uses the **async worker** `FA2Layout` with a time-based stop, not synchronous iterations. And `FA2_ITERATIONS` is never read (see §5).

### 4.4 `runtimeConfig.ts:231–238`
```
 * After layout, pull the farthest nodes ... inward to this radius percentile ...
export const FA2_OUTLIER_CLAMP_PERCENTILE = ...
```
**FALSE** — no outlier-clamping code exists anywhere. The constant is never read.

### 4.5 One comment that IS correct — worth knowing
`runtimeConfig.ts:295` already states:
```
// in graph units, while `itemSizesReference: 'screen'` renders node radii in px;
```
So the unit mismatch **was already known** to whoever wrote `settleScreenOverlaps` — that function is a workaround for it, not a fix of it.

---

## 5. Dead configuration knobs (verified by grep)

Command run from `vibegraph-web/src`:
```bash
grep -rn "\b<NAME>\b" --include=*.ts --include=*.vue . | grep -v "runtimeConfig.ts"
```

Result: **zero matches** for all four:

| Constant | Declared at | Referenced anywhere else? |
|---|---|---|
| `FA2_ITERATIONS` | `runtimeConfig.ts:208` | **No** |
| `FA2_ITERATIONS_LARGE` | `runtimeConfig.ts:254` | **No** |
| `FA2_OUTLIER_CLAMP_PERCENTILE` | `runtimeConfig.ts:238` | **No** |
| `NOVERLAP_MAX_ITERATIONS` | `runtimeConfig.ts:291` | **No** |

Control check (same command, a knob that IS used): `FA2_BARNES_HUT_MIN_NODES` → matches at `useSigma.ts:19` and `useSigma.ts:478`. So the grep method is sound.

**Implication:** anyone tuning these four env vars will observe zero effect. This is the most likely explanation for repeated failed tuning attempts.

---

## 6. Live measurements — VibeGraph (localhost:5173, project `431ee9dc`)

Same source repository as the grapuco instance (`fatc-Grocery-Store`).

### 6.1 Time from page load to settled layout
Measured twice with an in-page polling script watching for the `"Finalizing graph layout"` text to disappear:

- Run 1: `pageAgeSec: 35`
- Run 2: `settleSec: 21`, `pageAgeSec: 32`

Consistent with `LAYOUT_AUTO_STOP_MS` (8 s) + `NOVERLAP_AUTO_STOP_MS` (22 s) = 30 s.

### 6.2 Canvas layers
```json
{"canvasCount":8,"sizes":["1414x1155", ...×8]}
```
Container box measured: `{x:552, y:61, w:1048, h:856}`, `devicePixelRatio: 1.35`. `1048 × 1.35 ≈ 1414` ✓ consistent.

### 6.3 Frame rate
```json
{"idleFps":1,"wheelFps":33,"wheelEventsSent":66}
```
33 fps while actively zooming.

### 6.4 Node / edge counts as rendered
Node types (greyed = OFF by default, **not** rendered): Field **891**, Annotation 1, Project 1.
Edge types OFF by default: HAS_FIELD 891, RETURNS 332, TYPE_OF 270, PARAMETER_TYPE 228, INSTANTIATES 69.

Actually rendered:
- Nodes: 922 Method + 205 File + 161 Class + 142 APIEndpoint + 30 Constructor + 30 Interface + 13 DBModel + 5 Enum + 4 Record = **1,512**
- Edges: 1021 + 952 + 752 + 213 + 200 + 148 + 142 + 130 + 82 + 57 + 36 + 10 + 1 = **3,744**

Note: `graphAdapter.ts:45–61` (`EDGE_TYPE_PRIORITY`) collapses all relationships between a node pair into **one** drawn line, so drawn edges ≤ 3,744.

### 6.5 Zoom behavior (visual, screenshots taken)
- At fit view: node circles ≈14 px, **already visibly touching/overlapping** in dense regions. Labels already ON.
- After +11 scroll ticks: node circles ≈30 px, **still overlapping**.
- Derived exponent: node grew ≈2.1× while zoom ≈2.85× → `ln(2.1)/ln(2.85) ≈ 0.71`, consistent with `ZOOM_SIZE_POWER = 0.75`. ✓ Theory matches observation.

### 6.6 Renderer instability
CDP `Page.captureScreenshot` timed out at 30 s twice; `Runtime.evaluate` timed out at 45 s once. Compositor also served a stale frame (DOM reported container at `x=552` while the captured image drew the graph at `x≈0`).

⚠️ **Partial attribution caveat:** some instability followed a burst of **66 synthetic wheel events** dispatched by the investigator — not normal user input. However, **the first freeze occurred immediately after layout settle, before any synthetic input.** Treat "renderer freezes under normal use" as **⚠️ UNCONFIRMED** until reproduced with ordinary interaction.

---

## 7. Live measurements — grapuco.com (reference product)

Repository `fatc-Grocery-Store`, id `eae6552e-f8df-47f9-a688-ce1418bfa06e`. Status "Ready". **2,020 nodes • 5,402 edges • java** (read from their status bar).

### 7.1 Rendering stack (verified by scanning their shipped JS)
Script executed in-page: fetched all 32 `/_next/` chunks and counted library-name matches.
```json
{"scanned":32,"libs":{"force-graph":5,"ngraph":2},"layout":{"forceCollide":4},"hosts":[]}
```
The search pattern also included `sigma`, `graphology`, `forceatlas2`, `cytoscape`, `cosmograph` → **zero matches**.

Additional confirmation: the canvas's parent element has `className === "force-graph-container"` (vasturiano/force-graph's own container class).

**Conclusion: grapuco uses `force-graph` (d3-force based) + `ngraph`, with `forceCollide` for overlap prevention. It does NOT use Sigma or ForceAtlas2.**

### 7.2 Their API
Captured by monkey-patching `window.fetch` then navigating client-side:
```
https://api.grapuco.com/repository/status/{id}      → 200
https://api.grapuco.com/dashboard/stats             → 200
https://api.grapuco.com/graph/schema/{id}           → (very slow; eventually resolves)
```
The graph endpoint took roughly a minute on first load. Unauthenticated request returns `401` with `{message, error, statusCode}`.

### 7.3 Layout params found in their bundle
```json
{"cooldownTicks => 0":3, "warmupTicks => 0":2, "strength => -3":2,
 "strength => .02":2, "distanceMax => 5":2, "radius => 3":1}
```
⚠️ **These are mostly force-graph library defaults, not necessarily app config.** `cooldownTicks: 0` + `warmupTicks: 0` is *suggestive* of precomputed backend coordinates with no in-browser simulation, but this is **inference, not proof** — the app's own ForceGraph props could not be read (their React fiber tree was not reachable; `__reactContainer*` lookup returned nothing).

### 7.4 Zoom sweep (visual, full range)
- **Zoom OUT: hard-clamped.** Scrolling 30 additional ticks past the floor produced a **pixel-identical** screenshot. They set a min-zoom.
- **Zoom IN: effectively unbounded.** 40+ ticks past the "huge nodes" level still zoomed further.
- Node circle diameter measured across the range: floor ≈1–2 px → fit ≈4 px → mid ≈10–14 px → deep ≈**80–100 px**.
- **At no sampled zoom level did node circles overlap.** Visible dark gaps at every level.
- Edge lines stayed hairline-thin at all zoom levels including the deepest.

### 7.5 Label behavior (3 distinct thresholds observed)
1. Below threshold 1 — no labels, colored dots only.
2. Threshold 1 — **all node labels switch on at once** → immediately unreadable text soup in dense areas. No density-based culling.
3. Threshold 2 (deeper) — edge labels appear (`CALLS`, `DEFINES`, `IMPORTS`, `HAS_METHOD`), colored per type.
4. Very deep — edge labels scale up with zoom (the word "CALLS" became huge).

**Correction to an earlier claim:** their label overlap is a **transitional** problem at threshold 1, not permanent. A few ticks deeper and text becomes clean and readable.

### 7.6 Their UI features (tested)
| Feature | Behavior |
|---|---|
| Click node | Focus mode: selection chip + right `NODE DETAIL` panel (name, type badge, full path, `INCOMING (n)` / `OUTGOING (n)` lists with edge types). Background dims to near-black. |
| Explorer tab | File tree, **bidirectionally synced** — selecting a graph node auto-expands and highlights the file. |
| Flows tab | "Data Flow Analysis" — **gated behind Pro plan**. VibeGraph already ships this (`lib/dataFlow.ts`, `lib/flowFocus.ts`). |
| Filters | Clicking a node type **solos/isolates** it (others turn off). **Node positions do not move at all** — no re-layout. |
| Search (Ctrl+K) | Returns node name + type. **BUG: returns "No results" whenever a node is selected.** Verified: with a node selected, `OrderService` and `Order` both returned "No results" while `handleOrderException` was visible in the detail panel; after clicking Clear, `Order` returned 8 results. |
| Analyze | See §7.7 |

### 7.7 Their "Code Analysis" feature (the one worth copying)
Header: Health Score ring (78), `Domain: E-Commerce`, `layered`, buttons `Import to Spec` + `Re-analyze`.

- **Overview** — Business Summary (generated **in the user's language**, Vietnamese here), Tech Stack chips, Strengths list, `DETECTED MODULES (5)` each with description + capability chips.
- **Issues** — counters `1 critical / 5 warning / 4 info`. Each issue = title + category tag (Security / Tech-Debt / Performance / Architecture) + description + **exact file path**. Real examples: `Sử dụng biến môi trường không an toàn` → Security → `common/config/DotenvConfig.java`; `Truy vấn cơ sở dữ liệu lặp lại` (N+1) → Performance → `module/product/repository/ProductRepository.java`.
- **Recommendations (18)** — each has priority (HIGH/MEDIUM), type (Refactor/Test/Upgrade/Feature), description, **effort estimate** (`~1 month`, `3+ months`), and a **delete button** (curatable backlog, not a static report).
- **Chat** — RAG over the codebase with 3 starter prompts. *(Not exercised — would consume the account's credits.)*

Their issues are **not clickable through to the graph**. That is an opening for VibeGraph.

### 7.8 Schema comparison
grapuco node types: Method 933, File 225, Class 181, Function 163, APIEndpoint 142, DBModel 38, Interface 36, Constructor 21, Enum 5, Annotation 1. **No `Field` type at all.**

grapuco edge types (10): CALLS 1487, DEFINES 1367, HAS_METHOD 954, STEP_IN_FLOW 641, IMPORTS 511, STEP_IN_PROCESS 241, HANDLES_ROUTE 142, IMPLEMENTS 27, EXTENDS 20, READS_FROM_DB 12.

VibeGraph edge types (18) — has these that grapuco lacks: `INJECTS`, `OVERRIDES`, `RESOLVES_TO`, `HAS_RELATION`, `TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `INSTANTIATES`, `HAS_INNER`.
grapuco has these that VibeGraph lacks: `STEP_IN_PROCESS`, `READS_FROM_DB`.

**VibeGraph's semantic model is richer.** Do not cut edge types to make the picture cleaner — fix the layout instead.

---

## 8. The headline comparison

| | VibeGraph | grapuco |
|---|---|---|
| Nodes rendered | **1,512** | 2,020 |
| Edges rendered | **3,744** (≤, after pair-collapse) | 5,402 |
| Overlap at fit view | **Yes, heavy** | No |
| Overlap at deep zoom | **Yes** | No |
| Time to settled layout | **21–32 s** | ~instant once data arrives |

**VibeGraph draws 25% fewer nodes and 31% fewer edges than grapuco and still looks more crowded.** This eliminates data density as a cause.
