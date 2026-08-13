# 05 — Implementation Plan

**Approved approach: "Option B"** — make node size scale linearly with zoom so overlap becomes scale-invariant.

> **Before writing any code, read `02-SIGMA-INTERNALS.md` §2.** There is a trap that will make you think this approach is wrong when it isn't.

---

## Step 0 — Mandatory pre-flight

### 0.1 Resolve the one blocking unknown
Verify how Sigma's hit-testing / hover picking computes node radius — whether it uses `scaleSize()` (scaled) or the raw `size` attribute.

**Why it blocks:** if picking uses raw `size`, then with `p = 1` the clickable area will diverge from the drawn circle as you zoom, and clicking a big node will miss. This would need a separate fix.

**How to check:** in `vibegraph-web/node_modules/sigma/dist/sigma.esm.js`, find the quadtree / `getNodeAtPosition` / mouse-picking path and confirm whether the radius it compares against passes through `scaleSize`. Sigma 3 also has a WebGL picking mode (`PICKING_MODE` in the shaders) — determine which one is active.

Full detail: `07-OPEN-QUESTIONS.md` §1.

### 0.2 Follow repo rules
`CLAUDE.md` requires GitNexus impact analysis before editing symbols:
```
gitnexus_impact({target: "useSigma", direction: "upstream"})
```
and `gitnexus_detect_changes()` before committing.

### 0.3 Capture a baseline
Before changing anything, record for project `431ee9dc` at `http://localhost:5173/projects/431ee9dc/graph`:
- screenshot at fit view
- screenshot at fit + ~11 scroll ticks
- settle time (poll for the `"Finalizing graph layout"` text to disappear)

These are your regression comparators. Existing baseline values are in `01-EVIDENCE-LOG.md` §6.

---

## Step 1 — The sizing change ⚠️ SHIP AS ONE COMMIT

**These three edits are interdependent. Landing them separately will make the graph look broken in between.**

### 1.1 `ZOOM_SIZE_POWER`: 0.75 → 1.0
File: `vibegraph-web/src/composables/useSigma.ts:78`
```ts
const ZOOM_SIZE_POWER = 0.75   // → 1.0
```
This is the core change. Rationale: `02-SIGMA-INTERNALS.md` §3–4.

### 1.2 `SIGMA_EDGE_SIZE`: 0.25 → ~0.02
File: `vibegraph-web/src/lib/runtimeConfig.ts:166`

Without this, edges begin thickening at ~11× magnification instead of ~25×. Formula and derivation: `02-SIGMA-INTERNALS.md` §5.
```
SIGMA_EDGE_SIZE < SIGMA_MIN_EDGE_THICKNESS · M^(−p)
                = 2.8 / 100  = 0.028   for thin edges up to 100× zoom
```
⚠️ `runtimeConfig.ts:166` declares `{ min: 0.05 }` on this env value — **that floor must be lowered too**, or 0.02 will be clamped back up to 0.05 and the fix will silently not apply.

### 1.3 Add a zoom clamp
File: `vibegraph-web/src/composables/useSigma.ts`, in the `new Sigma(...)` options block (146–181):
```ts
maxCameraRatio: <value>,   // bounds zoom OUT
minCameraRatio: <value>,   // bounds zoom IN (optional)
```
Both currently unset → `null` → unbounded. See `02-SIGMA-INTERNALS.md` §6.

Pick `maxCameraRatio` by experiment: zoom out until the graph is as small as you'd ever want, read the camera ratio, use that.

### 1.4 Add guard comments
At `useSigma.ts:160`, next to `itemSizesReference: 'screen'`:
```ts
// DO NOT change to 'positions'. In Sigma 3.0.3 it does NOT alter zoom scaling —
// it only multiplies all sizes by a large constant (~100×+), which looks broken.
// The zoom lever is ZOOM_SIZE_POWER. See update/graph/02-SIGMA-INTERNALS.md §2.
itemSizesReference: 'screen',
```

### Verification for Step 1
- **Fit view must look essentially unchanged.** At `r = 1`, `r^(−p) = 1` for every `p`, so `p` has no effect at fit. If the fit view changed noticeably, something else went wrong — investigate before proceeding.
- Zoom in deeply: node circles should now grow **much faster** than before and separate visibly.
- Zoom in past ~11× and ~50×: **edges must stay hairline-thin.** If they thicken, 1.2 didn't take (check the `min: 0.05` floor).
- Zoom out: must stop at the clamp.
- Click nodes at several zoom levels: hit area must still match the drawn circle (this is what Step 0.1 protects).

---

## Step 2 — Fix the noverlap units

**Only meaningful after Step 1.** With `p = 1`, node radius in graph coordinates becomes the constant `S/K` (`02-SIGMA-INTERNALS.md` §4), so a single conversion makes `graphology-noverlap` correct.

### 2.1 Measure `K` at runtime — do not guess
Derive it from Sigma's own conversion rather than hardcoding. Sketch:
```ts
// graph-units per screen pixel, at the current camera
const a = sigma.graphToViewport({ x: 0, y: 0 })
const b = sigma.graphToViewport({ x: 1, y: 0 })
const pxPerGraphUnit = Math.hypot(b.x - a.x, b.y - a.y)
// a node of attribute size S draws at S px (at r=1) → S / pxPerGraphUnit graph units
```
`settleScreenOverlaps` (`useSigma.ts:754–882`) already does this correctly via `unitsPerPixel` (:792) — reuse that logic rather than writing a second version.

### 2.2 Feed noverlap correct inputs
`useSigma.ts:534–538` currently passes only `margin` and `ratio`. Options:
- convert sizes to graph units before handing them to noverlap, keeping `ratio: 1`, and expressing `margin` as the desired gap **in graph units**; or
- drop `graphology-noverlap` entirely and let the (now correct and zoom-independent) `settleScreenOverlaps` do the whole job with more iterations.

The second option is worth serious consideration — see Step 3.

### 2.3 Re-derive `NOVERLAP_MARGIN` / `NOVERLAP_RATIO`
Current values (40 / 2.7, `runtimeConfig.ts:289–290`) were tuned against the broken unit system. They are meaningless afterwards. Recompute from the target on-screen gap.

---

## Step 3 — Cut the 21–32 s "Finalizing graph layout"

Measured twice: 21 s and ~32 s (`01-EVIDENCE-LOG.md` §6.1). Budget is `LAYOUT_AUTO_STOP_MS` 8 s + `NOVERLAP_AUTO_STOP_MS` 22 s.

**22 of those seconds are currently spent on a pass that computes in the wrong units** — i.e. largely wasted. After Step 2 makes it correct (or removes it), drop `NOVERLAP_AUTO_STOP_MS` (`runtimeConfig.ts:292`) hard.

Also consider `onConverged` (already wired at `useSigma.ts:539`) as the primary exit and the timer as a fallback only.

Reference target: grapuco renders essentially instantly once data arrives (`04-GRAPUCO-REFERENCE.md` §9).

---

## Step 4 — Filter must not restart the layout

**Current behavior:** `useSigma.ts:129–257` — `init(graph)` calls `dispose()`, constructs a **new** `Sigma`, then `startLayout(graph)` at :256. Even though `positionCache` (:265) re-seeds known positions, FA2 restarts and **moves nodes again**, so toggling a filter makes the graph jump.

**Target behavior:** grapuco keeps node positions **completely frozen** when filtering (`04-GRAPUCO-REFERENCE.md` §6).

**Approach:** hide/show via the node/edge reducers (`setReducers`, `useSigma.ts:948–965`) instead of rebuilding the graph and re-running layout. Note `settleScreenOverlaps` already honours a `filterHidden` attribute (:768), so that concept exists.

**Optional UX:** consider grapuco's *solo/isolate* semantics (click a type → show only that type). With 11 node types, solo is faster than unchecking ten boxes.

---

## Step 5 — Delete the dead knobs and fix the false comments

### 5.1 Remove or wire up (all currently referenced nowhere — `01-EVIDENCE-LOG.md` §5)
- `FA2_ITERATIONS` (`runtimeConfig.ts:208`)
- `FA2_ITERATIONS_LARGE` (`runtimeConfig.ts:254`)
- `FA2_OUTLIER_CLAMP_PERCENTILE` (`runtimeConfig.ts:238`)
- `NOVERLAP_MAX_ITERATIONS` (`runtimeConfig.ts:291`)

`FA2_OUTLIER_CLAMP_PERCENTILE` is the most dangerous: its comment describes outlier clamping that **does not exist**. Either implement it or delete it — leaving it is a trap for the next person.

### 5.2 Correct the provably false comments
- `runtimeConfig.ts:258–259` — says `itemSizesReference: 'positions'`; the code uses `'screen'`.
- `runtimeConfig.ts:211–215` — says grapuco uses ForceAtlas2; it uses `force-graph` + `forceCollide` (`04-GRAPUCO-REFERENCE.md` §2).
- `runtimeConfig.ts:207` — says FA2 iterations run synchronously before first paint; the code uses the async worker with a time-based stop.

---

## Step 6 — Backend import performance

Separate workstream, no dependency on Steps 1–5. See `06-IMPORT-PERFORMANCE.md`.

---

## Recommended order & risk

| Step | Impact | Risk | Notes |
|---|---|---|---|
| 0 | — | — | **Blocking.** Resolve hit-testing first. |
| 1 | **High** | **Low** | `p` has no effect at fit view → low visual-regression risk. Ship as one commit. |
| 2 | High | Medium | Needs careful unit work; reuse `settleScreenOverlaps` math. |
| 3 | High (perceived speed) | Low | Mostly lowering a timeout once Step 2 lands. |
| 4 | Medium | Medium | Touches the rebuild path; `positionCache` interaction needs care. |
| 5 | Low direct | **Very low** | High value for the *next* maintainer. Cheap. |
| 6 | High (import) | Mixed | 6.1/6.2 cheap; parallel parsing is the risky one. |

**If you only have budget for one thing: do Step 1.** It is the change that makes the problem solvable at all.

---

## Testing

Repo standard is 80% coverage (`RULES.md`, global testing rules). Existing specs live in `vibegraph-web/src/composables/__tests__/` and `vibegraph-web/src/lib/__tests__/`.

Unit-testable without a browser:
- the size→graph-unit conversion from Step 2 (pure math)
- `settleScreenOverlaps` given synthetic node sets — assert **zero remaining collisions**, which currently is not asserted anywhere

Requires the browser (use the preview tooling, not manual checking):
- visual separation across the zoom range
- edge thickness at deep zoom
- hit-testing accuracy at deep zoom
- filter toggle causing no node movement
