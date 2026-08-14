# 02 — Sigma 3.0.3 Internals: How Node & Edge Sizes Actually Scale

> **This file contains the single most important finding of the investigation.**
> Read it before touching any sizing code.

Version in use: `sigma@^3.0.3` (`vibegraph-web/package.json`).
All line numbers refer to `vibegraph-web/node_modules/sigma/dist/`.

---

## 1. The one function that governs everything

`sigma.esm.js:3629–3633` (also `sigma.cjs.dev.js:3636`, identical):

```js
scaleSize(size = 1, cameraRatio = this.camera.ratio) {
  return size / this.settings.zoomToSizeRatioFunction(cameraRatio)
       * (this.getSetting("itemSizesReference") === "positions"
            ? cameraRatio * this.graphToViewportRatio
            : 1);
}
```

Notation used from here on:
- `r` = `cameraRatio`. **Smaller `r` = zoomed IN.** `r = 1` is the fit view.
- `M` = magnification = `1/r`.
- `f(r)` = `zoomToSizeRatioFunction(r)`. VibeGraph sets `f(r) = r^p` with `p = ZOOM_SIZE_POWER = 0.75` (`useSigma.ts:78–80`). Sigma's own default is `Math.sqrt`, i.e. `p = 0.5`.
- `S` = the raw `size` attribute on the node.

---

## 2. ⚠️ THE TRAP: `itemSizesReference` is NOT the zoom lever

`graphToViewportRatio` is defined at `sigma.esm.js:3496–3511`:

```js
getGraphToViewportRatio() {
  var graphP1 = { x: 0, y: 0 };
  var graphP2 = { x: 1, y: 1 };
  var graphD = Math.sqrt(...);              // constant = sqrt(2)
  var viewportP1 = this.graphToViewport(graphP1);
  var viewportP2 = this.graphToViewport(graphP2);
  var viewportD = Math.sqrt(...);
  return viewportD / graphD;
}
```

`graphToViewport` (3485–3489) → `framedGraphToViewport` (3423–3433) → multiplies by the **camera matrix**, which divides by `r`.

**Therefore `graphToViewportRatio ∝ 1/r`.** Write it as `G = K/r`, where `K` is constant for a given graph extent + viewport size.

Substituting into `scaleSize`:

| `itemSizesReference` | Rendered screen radius |
|---|---|
| `'screen'` | `S / f(r)` = `S · r^(−p)` |
| `'positions'` | `S / f(r) · (r · K/r)` = **`S · K · r^(−p)`** |

### ⇒ Both modes have the IDENTICAL zoom exponent. They differ only by the constant `K`.

**Consequence — this is the trap:**

Switching `itemSizesReference: 'screen'` → `'positions'` will:
- ❌ **NOT** change how node size responds to zoom
- ⚠️ **WILL** multiply every node's rendered size by `K`

`K` is roughly "viewport pixels per normalized graph unit". Sigma's normalization maps the graph into approximately a unit box, and the viewport is ~1048 px wide (measured, `01-EVIDENCE-LOG.md` §6.2), so **`K` is plausibly in the hundreds**.

The observable result of flipping that one setting is therefore *"all nodes suddenly became enormous and the graph looks broken"* — which reads as "this approach is wrong", prompting a revert. **It is not wrong; it is the wrong lever.**

> **Rule: do not change `itemSizesReference`. Leave it at `'screen'`. Add a comment there pointing at this document.**

---

## 3. The real lever: `zoomToSizeRatioFunction` (i.e. `ZOOM_SIZE_POWER`)

With `itemSizesReference: 'screen'`:

```
node screen radius      ∝ S · r^(−p)
screen distance between
two nodes               ∝ r^(−1)          (property of the coordinate system; not configurable)

node-to-gap ratio       ∝ r^(1−p)
```

| `p` | node/gap ratio vs zoom | Behavior |
|---|---|---|
| 0.5 (Sigma default) | `∝ r^0.5` | zoom OUT → nodes relatively bigger → overlap worsens |
| **0.75 (VibeGraph today)** | `∝ r^0.25` | same direction, milder |
| **1.0** | **`∝ r^0 = constant`** | **scale-invariant — overlap identical at every zoom** |
| >1.0 | inverted | zoom IN makes overlap worse |

### `p = 1` is the target. It is a one-constant change.

**Empirical cross-check** (`01-EVIDENCE-LOG.md` §6.5): measured node growth 2.1× across a 2.85× zoom → exponent ≈ 0.71, matching the configured 0.75. The model predicts observation correctly, which is why we can trust it to predict `p = 1` too.

### Bonus property: `p` has no effect at the fit view
At `r = 1`, `r^(−p) = 1` **for every `p`**. So changing `ZOOM_SIZE_POWER` **does not change how the graph looks at fit view** — only how it responds as you zoom away from it. This makes the change unusually low-risk for visual regression.

---

## 4. Why `p = 1` makes de-overlap solvable *once*

With `p = 1`:
- node screen radius = `S/r`
- a graph-space distance `d` renders as `d · K/r` on screen

⇒ **node radius expressed in graph coordinates** = `(S/r) ÷ (K/r)` = **`S/K`** — a constant, independent of zoom.

This is the real prize:

| | `p = 0.75` (today) | `p = 1` |
|---|---|---|
| Node radius in graph units | `∝ r^0.25` — **changes with zoom** | **`S/K` — fixed** |
| Can one de-overlap solution be correct at all zooms? | **No — mathematically impossible** | **Yes** |

With `p = 0.75` there is no single node radius in graph space, so `graphology-noverlap` (which works purely in graph units — see `01-EVIDENCE-LOG.md` §3) **cannot** be given a correct input. That is the deep reason every previous tuning attempt failed.

With `p = 1`, `noverlap` becomes correctable with a single conversion constant, and `settleScreenOverlaps`'s existing `unitsPerPixel` math becomes exact and zoom-independent.

---

## 5. Side effect of `p = 1`: edges

Sigma applies **the same** `zoomToSizeRatioFunction` to edges. Edge vertex shader, `index-fad77a13.esm.js:914` (`edge-clamped` program; `edge-rectangle` at :789 is equivalent):

```glsl
float pixelsThickness = max(normalLength, minThickness * u_sizeRatio);
float webGLThickness  = pixelsThickness * u_correctionRatio / u_sizeRatio;
```

`u_sizeRatio` is set from `sigma.esm.js:2766`:
```js
sizeRatio: 1 / this.scaleSize(),      // = f(r) = r^p
```

Two regimes:

| Regime | Resulting thickness |
|---|---|
| Floor wins (`minThickness · r^p > size`) | `(minThickness · r^p) / r^p` = **`minThickness` — constant, independent of `p`** ✓ |
| Size wins | `∝ size / r^p` → **grows without bound as you zoom in** |

The floor holds while:
```
r^p  >  SIGMA_EDGE_SIZE / SIGMA_MIN_EDGE_THICKNESS  =  0.25 / 2.8  =  0.0893
```

| `p` | Edges stay thin up to magnification |
|---|---|
| 0.75 | `r < 0.040` → ~**25×** |
| 1.00 | `r < 0.089` → ~**11×** |

So `p = 1` makes edges start ballooning at ~11× zoom instead of ~25×.

### Fix: lower `SIGMA_EDGE_SIZE`

To keep edges thin up to magnification `M`:
```
SIGMA_EDGE_SIZE < SIGMA_MIN_EDGE_THICKNESS · M^(−p)
```
With `p = 1`, `minThickness = 2.8`, target `M = 100×`:
```
SIGMA_EDGE_SIZE < 2.8 / 100 = 0.028   →   use ≈ 0.02
```
(current value: 0.25)

**There is no `maxEdgeThickness` in Sigma 3.0.3** — verified: `dist/declarations/src/settings.d.ts:38` declares only `minEdgeThickness: number`. Lowering the edge size is the only available lever.

> Reference behavior: grapuco keeps edges hairline-thin at *every* zoom level (`01-EVIDENCE-LOG.md` §7.4), so matching that is the goal.

---

## 6. Zoom clamping is built in and currently unused

`dist/declarations/src/settings.d.ts:60–61`:
```ts
minCameraRatio: null | number;
maxCameraRatio: null | number;
```

Applied at `sigma.esm.js:2041–2042` and enforced at `3366–3369`:
```js
if (typeof maxCameraRatio === "number") newRatio = Math.min(newRatio, maxCameraRatio);
if (typeof minCameraRatio === "number") newRatio = Math.max(newRatio, minCameraRatio);
```

Semantics: `maxCameraRatio` bounds how far you can zoom **OUT**; `minCameraRatio` bounds how far you can zoom **IN**.

VibeGraph sets **neither** (`useSigma.ts:146–181`), so both default to `null` = unbounded.

Adding a clamp is a one-line settings change. With `p = 1` it is a UX nicety rather than a correctness requirement (overlap is already scale-invariant), but grapuco does clamp zoom-out and it prevents the "zoomed out into a useless dot" state.

---

## 7. Code that automatically adapts (no changes needed)

Verified by reading source:

| File | Why it is safe |
|---|---|
| `vibegraph-web/src/lib/sigmaRenderers.ts` | Sigma passes **already-scaled** sizes into `drawLabel` / `drawHover` (`sigma.esm.js:2214`, `:2330`, `:2294–2302`). So the label vertical offset (`sigmaRenderers.ts:142`, `data.y + data.size + LABEL_GAP`) and the hover ring radius (`:190`, `data.size + RING_GAP`) are in screen px and follow automatically. |
| `vibegraph-web/src/lib/ghostLayer.ts` | Already calls `sigma.scaleSize(rawSize, cameraRatio)` explicitly at lines **123** (edge width) and **140** (node radius). Correct by construction for any `p`. |

---

## 8. Summary of the mechanism

```
                     itemSizesReference   →  ONLY a constant factor K.  NOT a zoom lever.  ⚠️
        zoomToSizeRatioFunction (= r^p)   →  THE zoom lever.
                                  p = 1   →  node radius fixed in graph units
                                          →  overlap becomes scale-invariant
                                          →  noverlap becomes solvable with one constant
                                          →  but edges need SIGMA_EDGE_SIZE lowered
```
