# 04 — grapuco.com Reference Teardown

Why this matters: `runtimeConfig.ts:211–215` bases VibeGraph's ForceAtlas2 tuning on an assumption about how grapuco works. **That assumption is false.** This file records what grapuco actually does.

Investigated: 2026-08-13, live session in the user's Chrome, logged into their own account.
Target: `https://grapuco.com/dashboard/repositories/eae6552e-f8df-47f9-a688-ce1418bfa06e` — repo `fatc-Grocery-Store`, **the same source repo imported into VibeGraph as project `431ee9dc`**.

---

## 1. The false premise in our codebase

`vibegraph-web/src/lib/runtimeConfig.ts:211–215`:
```
// The reference "grapuco" look is standard ForceAtlas2 (NOT LinLog) with strong
// repulsion + dissuade-hubs: connected nodes stay close (short, local edges) while
// unrelated nodes push far apart, so the graph spreads into organic branches
// instead of one dense hairball. LinLog is intentionally OFF — it lengthens edges
// and pulls the body toward the center (measured edgeToRadius 0.48 vs 0.29).
```

**grapuco does not use ForceAtlas2. It does not use Sigma or graphology at all.**

Any tuning derived from this comment was aimed at reproducing a d3-force layout using ForceAtlas2 parameters — which is why it never converged.

## 2. What they actually use — evidence

Script run in-page: fetch all `/_next/` chunks, count library-name occurrences.

```json
{"scanned":32,"libs":{"force-graph":5,"ngraph":2},"layout":{"forceCollide":4},"hosts":[]}
```

The same regex also tested for `sigma`, `graphology`, `forceatlas2`, `cytoscape`, `cosmograph`, `deck.gl`, `pixi`, `regl` → **zero matches**.

Independent confirmation: the `<canvas>`'s parent element carries
```
className === "force-graph-container"
```
which is vasturiano/force-graph's own container class.

**Stack: `force-graph` (d3-force based) + `ngraph`, using `forceCollide` for overlap prevention.**

### Architectural significance
`forceCollide` is a **constraint inside the simulation** — nodes are never permitted to overlap at any step. VibeGraph instead runs a *post-hoc repair* chain (FA2 → normalize → spread → noverlap → screen-overlap), which is always fighting a layout that has already settled into overlapping positions.

## 3. Layout parameters found in their bundle

```json
{"cooldownTicks => 0":3, "warmupTicks => 0":2, "strength => -3":2,
 "strength => .02":2, "distanceMax => 5":2, "radius => 3":1,
 "radius => null":2, "strength => function":1}
```

⚠️ **UNVERIFIED / low confidence.** Most of these are almost certainly force-graph's own prop defaults rather than grapuco's app config. `cooldownTicks: 0` + `warmupTicks: 0` *suggests* they precompute coordinates server-side and run no in-browser simulation, which would explain their instant render — but this is **inference, not proof**.

**Why it could not be confirmed:** their React fiber tree was not reachable. `Object.keys(el)` on the canvas container returned only `["__on"]` (a d3-selection artifact), and a search for `__reactContainer*` across all `div`s returned nothing. The live ForceGraph instance (which would expose `.d3Force()`) could not be obtained.

A further attempt to read their `graph/schema` payload directly was **blocked by policy** (it required using the user's stored auth token). Not pursued.

## 4. Zoom behavior — the key finding

Full-range sweep, screenshots at each step.

### Zoom OUT: hard-clamped
Scrolled 30 additional ticks past the apparent floor → **pixel-identical screenshot**. They set a min-zoom. At the floor the whole graph is a small cluster but nodes remain **discrete dots**, never a solid mass.

### Zoom IN: effectively unbounded
40+ ticks past the "huge nodes" level and still zooming (eventually showing only edge lines crossing empty space).

### Node size across the range

| Zoom level | Node diameter on screen |
|---|---|
| Floor (min) | ~1–2 px |
| Fit | ~4 px |
| Mid | ~10–14 px |
| Deep | **~80–100 px** |

Node circles grow roughly **linearly** with magnification — consistent with graph-space sizing (`p ≈ 1` in the terminology of `02-SIGMA-INTERNALS.md`).

### **At no sampled zoom level did node circles overlap.** Visible dark gaps at every level.

Edge lines stayed **hairline-thin at every zoom**, including the deepest.

## 5. Label behavior — 3 thresholds

1. Below threshold 1 — no labels, colored dots only.
2. **Threshold 1** — *all* node labels switch on simultaneously → immediately unreadable text soup in dense regions. **No density-based culling.**
3. Threshold 2 (deeper) — edge labels appear (`CALLS`, `DEFINES`, `IMPORTS`, `HAS_METHOD`), colored per edge type.
4. Very deep — edge labels scale up with zoom.

⚠️ **Correction to an earlier claim in this investigation:** grapuco's label overlap is **transitional** (bad only at threshold 1), not permanent. A few ticks deeper and text reads cleanly.

**VibeGraph's label handling is more sophisticated** — it has `SIGMA_MAX_EDGE_LABELS_PER_FRAME` budgeting and hides edge labels that don't fit their edge (`lib/sigmaRenderers.ts`). Do not copy grapuco here.

## 6. UI/UX features tested

| Feature | Behavior | Verdict for VibeGraph |
|---|---|---|
| **Click node** | Selection chip at top + right `NODE DETAIL` panel: name, type badge, full path, then `INCOMING (n)` / `OUTGOING (n)` lists each showing target name + edge type. Background dims to near-black. | Similar to VibeGraph's focus mode, but their dimming is **much stronger**. Worth comparing against `FOCUS_OPACITY_DIMMED`. |
| **Explorer tab** | File tree, **bidirectionally synced**: selecting a graph node auto-expands the tree and highlights the file. | VibeGraph has an Explorer too — verify the sync is bidirectional. |
| **Flows tab** | "Data Flow Analysis" — **locked behind Pro plan**. | **VibeGraph already ships this** (`lib/dataFlow.ts`, `lib/flowFocus.ts`). Do not rebuild. |
| **Filters** | Clicking a node type **solos/isolates** it. **Node positions do not move at all.** | **Copy this.** VibeGraph's filter calls `init()` → new Sigma → `startLayout()`, so nodes jump. |
| **Search (Ctrl+K)** | Returns node name + type. | **They have a bug here** — see below. |
| **Analyze** | See §7. | The feature worth studying. |

### Their search bug (reproducible)
With a node selected, typing `OrderService` → *"No results"*. Typing `Order` → *"No results"* — **while `handleOrderException` was visibly listed in the detail panel**. After clicking `Clear`, `Order` returned 8 results (`OrderServiceTest.java`, `OrderServiceImpl.java`, `OrderRepository.java`, …).

⇒ their search is broken/scoped while focus mode is active. Avoid this class of bug in VibeGraph.

## 7. Their "Code Analysis" feature

Header: Health Score ring (**78**), `Domain: E-Commerce`, `layered`, plus `Import to Spec` and `Re-analyze` buttons.

**Overview tab** — Business Summary written **in the user's own language** (Vietnamese), Tech Stack chips (Java, Spring Boot, Spring Security, Spring Data JPA, Thymeleaf, Selenium, Caffeine Cache, SendGrid, Cloudinary), Strengths list, `DETECTED MODULES (5)` each with description + capability chips.

**Issues tab** — counters `1 critical / 5 warning / 4 info`. Each issue carries title + category tag (Security / Tech-Debt / Performance / Architecture) + description + **exact file path**. Verified examples:
- `Sử dụng biến môi trường không an toàn` — Security — `common/config/DotenvConfig.java`
- `Thiếu kiểm thử đơn vị cho các lớp Service quan trọng` — Tech-Debt — `module/order/service/OrderServiceImpl.java`
- `Truy vấn cơ sở dữ liệu lặp lại` (N+1) — Performance — `module/product/repository/ProductRepository.java`

**Recommendations tab (18)** — each has priority (HIGH/MEDIUM), type (Refactor/Test/Upgrade/Feature), description, **effort estimate** (`~1 month`, `3+ months`), and a **delete button**. It is a curatable backlog, not a static report.

**Chat tab** — RAG over the codebase, 3 starter prompts. *Not exercised — would have consumed the account's credits.*

### What actually makes it valuable
Not the report itself — the **path from report to action**: `Import to Spec` feeds the analysis into their "Spec Designer".

### The gap VibeGraph can exploit
**Their issues are not clickable through to the graph.** VibeGraph owns the graph *and* the MCP integration, so "issue → node on graph" and "issues → MCP → agent fixes it" are both stronger endpoints than `Import to Spec`.

## 8. Schema comparison

**grapuco node types:** Method 933, File 225, Class 181, Function 163, APIEndpoint 142, DBModel 38, Interface 36, Constructor 21, Enum 5, Annotation 1. **No `Field` type at all.**

**grapuco edge types (10):** CALLS 1487, DEFINES 1367, HAS_METHOD 954, STEP_IN_FLOW 641, IMPORTS 511, STEP_IN_PROCESS 241, HANDLES_ROUTE 142, IMPLEMENTS 27, EXTENDS 20, READS_FROM_DB 12.

**VibeGraph edge types (18).** Present in VibeGraph, absent in grapuco: `INJECTS`, `OVERRIDES`, `RESOLVES_TO`, `HAS_RELATION`, `TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `INSTANTIATES`, `HAS_INNER`.
Present in grapuco, absent in VibeGraph: `STEP_IN_PROCESS`, `READS_FROM_DB`.

**VibeGraph's semantic model is richer.** Treat that as an asset. Do not trim edge types to make the picture cleaner — fix the layout instead.

## 9. Performance comparison

| | grapuco | VibeGraph |
|---|---|---|
| Waiting for data | very slow (`graph/schema` took ~1 min on first load) | fast |
| Data → settled graph | **~instant** | **21–32 s** ("Finalizing graph layout…") |
| Renderer stability during test | no issues | multiple CDP timeouts (see caveat in `01-EVIDENCE-LOG.md` §6.6) |

grapuco's backend is *slower*; their **frontend** is what's fast. That is the part worth matching.

## 10. Where VibeGraph is already ahead

- Labels visible at fit view (grapuco requires deep zoom)
- `Edge labels: On` / `Node kind: On` toggles — grapuco has no equivalent
- Flows / Data Flow shipped free (grapuco charges Pro)
- Richer edge taxonomy (18 vs 10)
- Smarter label culling
- Search works during focus mode (theirs doesn't)
