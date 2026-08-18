# T14 — QA Visual Step 1 (Verification Report)

**Date:** 2026-08-18 ~10:20 (+07)
**Branch:** `feat/graph-zoom-invariant` @ `c203f23`
**Tested:** project `2c67c31c-b65d-42ba-b128-43cf88501339` (same project as T2 baseline — per BASELINE.md instruction)
**Method:** live browser session (Chrome DevTools), WebGL `readPixels` measurements against the `sigma-nodes` / `sigma-edges` canvases, Vue component state reads. **Not visual estimation** — all numbers below come from script output in this session.

---

## Checklist (from `05-IMPLEMENTATION-PLAN.md` §Verification for Step 1)

### 1. Fit view essentially unchanged — ✅ PASS
- At `r = 1`, `r^(−p) = 1` for every `p`, so `ZOOM_SIZE_POWER` 0.75→1.0 is mathematically inert at fit (`02-SIGMA-INTERNALS.md` §3).
- Measured fit-view node pixel footprint: 37,158 node px (stable across repeated reads in this session).
- Layout settle time: **36 s** (vs baseline 32 s) — expected: Step 1 does not touch layout budgets (`LAYOUT_AUTO_STOP_MS` 8 s + `NOVERLAP_AUTO_STOP_MS` 22 s ≈ 30 s still govern). Settle-time reduction is T9's job, not Step 1's.

### 2. Zoom in deeply: nodes grow much faster and separate — ✅ PASS
- Graph bounding span grew **×1.702 per zoom tick** (measured: 282 → 480 px after 1 tick), matching Sigma's `zoomingRatio = 1.7` — camera zoom behaves normally.
- Same-region node cluster footprint: 34×41 px at fit → **492×306 px after 11 zoom ticks** (area ×4.1). With `p = 1` node diameter grows ∝ `1/r`, i.e. **faster than the old `r^(−0.75)`** — consistent with the fix. (Cluster-level figure; single-node measurement is unreliable while nodes still overlap pre-Step-2.)
- Direct screenshot (attached to session record) at deep zoom shows the enlarged node circles.

### 3. Edges stay hairline-thin past ~11× and ~50× zoom — ✅ PASS (the critical T4/T5 proof)
Column-run thickness measurement on the `sigma-edges` WebGL buffer (median run length of consecutive edge pixels, DPR = 1.25, `minEdgeThickness` = 2.8 px → expected ≤ ~5 device px):

| State | Edge runs | **Median thickness** | P90 | Max |
|---|---|---|---|---|
| Fit view | 7,643 | **4 px** | 13 | 216 |
| **~70× zoom** (8 ticks toward centroid, below the 100× `minCameraRatio` clamp) | 1,501 | **4 px** | 14 | 68 |

Median thickness is **identical (4 px) at fit and at ~70×** — the `SIGMA_EDGE_SIZE` 0.25→0.02 + floor `{min:0.005}` change is in effect. Without it, `p = 1` would balloon edges starting at ~11× zoom (`02` §5). Max values are crossing/overlapping line segments, not thickness.

### 4. Zoom out stops at the clamp — ✅ PASS
`maxCameraRatio: 4` set at `useSigma.ts:172`. Measured: after zooming out **30 ticks**, then **30 more ticks**, the rendered node footprint is **pixel-identical** (30 node px, 14×15 bbox, centroid (238,151) in both states). Zoom-out is hard-bounded — matches grapuco's behavior (`01` §7.4).

### 5. Hit area matches the drawn circle at several zoom levels — ✅ PASS
Sigma 3.0.3 picking is WebGL framebuffer color picking (`07` §A1, verified T1) — geometry identical to the drawn circle by construction. Live confirmation this session at **~100× deep zoom**:
- Located node pixels via `readPixels` centroid, dispatched `mousemove` + `mousedown/mouseup/click` at that exact page coordinate:
  - hover → `hoverHit: true`
  - click → selected node `WishlistServiceImpl.java` (type `File`) — the node under the pixel.
- Earlier in session at moderate zoom: click selected `ProductServiceTest.java` (File), detail panel opened.
No raw-vs-scaled divergence exists (WebGL picking cannot diverge from rendering — both passes share `u_sizeRatio`, `07` §A1 evidence chain #4).

### 6. No console errors attributable to the graph — ✅ PASS
Only unrelated API responses observed (one 401, two 403 — telemetry/credit endpoints, pre-existing; graph load, layout, zoom, picking all error-free).

---

## Verdict

**T14 PASSED — Step 1 (T3–T7) is verified working end-to-end.** Wave 2 may be considered closed; Wave 3 (T8–T11, T15) is unblocked.

Remaining known limitations (by design, not defects):
- Nodes still overlap at fit view → Step 2 (T8) fixes the de-overlap pass units.
- 30+ s settle time → T9.
- Filter toggles still re-run layout → T10.

## Reproduction notes
- WebGL canvases have `preserveDrawingBuffer: false`: pixel reads only work when a render was triggered immediately before `readPixels` (a tiny wheel event + `requestAnimationFrame` chain works; a symmetric ±jiggle does NOT trigger Sigma's render).
- `readPixels` Y-axis is bottom-up; convert with `cssY = (canvasHeight − glY) / devicePixelRatio`.
- The Chrome MCP screenshot tool cannot write into this repo's path (workspace-root restriction) — the deep-zoom screenshot was captured inline in the session instead.
