# 03 — Root Cause Analysis: Why Nodes Overlap

Conclusions here are **derived** from the facts in `01-EVIDENCE-LOG.md` and `02-SIGMA-INTERNALS.md`. Each claim links back to its evidence.

---

## What is NOT the cause (ruled out with evidence)

### ❌ Not data density
VibeGraph renders **1,512 nodes / 3,744 edges**. grapuco renders **2,020 nodes / 5,402 edges** — for the *same* source repository. VibeGraph draws **25% fewer nodes and 31% fewer edges and still looks more crowded**. (`01-EVIDENCE-LOG.md` §8)

### ❌ Not "too many `Field` nodes"
`Field` (891) is **already OFF by default** — it is greyed out in the Node Types panel, along with `Annotation` and `Project`; and `HAS_FIELD`, `RETURNS`, `TYPE_OF`, `PARAMETER_TYPE`, `INSTANTIATES` are OFF on the edge side. (`01-EVIDENCE-LOG.md` §6.4)

*(An earlier draft of this investigation recommended hiding `Field` by default. That recommendation was wrong and has been withdrawn — it was already done.)*

### ❌ Not a missing feature or a wrong layout algorithm
ForceAtlas2 produces a perfectly reasonable silhouette. The problem is downstream of it.

---

## The actual cause: three layers, all verified

### Layer 1 — `graphology-noverlap` is fed the wrong units *(the origin)*

**The mismatch:**
- `useSigma.ts:160` sets `itemSizesReference: 'screen'` → node `size` is in **screen pixels** (range 8–18).
- `useSigma.ts:534–538` passes those same raw `size` values to `NoverlapLayout`.
- `graphology-layout-noverlap/iterate.js:151` computes collisions in **graph coordinate units**:
  ```js
  collision = dist < s1 * ratio + margin + (s2 * ratio + margin);
  ```
- The layout span is normalized to `LAYOUT_NORMALIZE_SPAN = 9000` graph units (`runtimeConfig.ts:264`).

**Worked example** (two `File` nodes, `size = 18`, `ratio = 2.7`, `margin = 40`):

```
required centre-to-centre distance = 2 × (18 × 2.7 + 40) = 177 graph units
```
At a ~1400 px viewport showing 9000 units → `9000/1400 ≈ 6.4 units/px`
```
177 units ≈ 27.5 px
```
But two circles of radius 18 px need **≥ 36 px** centre-to-centre just to touch.

**⇒ noverlap guarantees ~27 px where ~36 px is required — a ~25% shortfall. Overlap is structurally guaranteed.**

It is worse than that in practice: `spreadLayoutClusters` (`useSigma.ts:617–690`) runs **after** `normalizeLayout` and scales clusters by up to **2.08×** (`useSigma.ts:637`), so the real span exceeds 9000 and the pixel gap shrinks further.

> The codebase already knows about this mismatch — `runtimeConfig.ts:295` states it explicitly. `settleScreenOverlaps` was written as a **workaround**, not a fix.

### Layer 2 — the workaround is under-powered *and* mathematically incapable

`settleScreenOverlaps` (`useSigma.ts:754–882`) does the px→graph-unit conversion **correctly** (`unitsPerPixel` at :792, `node.radius *= unitsPerPixel` at :798). But:

- it runs only `LAYOUT_SCREEN_OVERLAP_ITERATIONS = 10` iterations (`:805`) at effective per-pair force `STRENGTH × 0.5` (`:851`);
- it breaks early on `collisions === 0` (`:863`) but **silently gives up** if it exhausts 10 iterations with collisions remaining — no retry, no report;
- it runs **once**, and only after up to `NOVERLAP_AUTO_STOP_MS = 22 s` of the mis-united noverlap pass.

**And crucially — even a perfect version of this function could not fix the problem.** See Layer 3.

### Layer 3 — with `p = 0.75` no zoom-independent solution exists *(the deep reason)*

From `02-SIGMA-INTERNALS.md` §4: a node's radius **expressed in graph coordinates** is `∝ r^(1−p)`.

With `p = 0.75` that is `∝ r^0.25` — **it changes as you zoom**.

`graphology-noverlap` operates purely in graph coordinates. If the node's graph-space radius is not constant, there is **no single value you can hand it that is correct at more than one zoom level**.

Practical consequence, confirmed by observation (`01-EVIDENCE-LOG.md` §6.5):

```
node/gap ratio ∝ r^(1−p) = r^0.25

zoom IN   (r ↓)  →  ratio ↓  →  overlap gradually improves
zoom OUT  (r ↑)  →  ratio ↑  →  overlap gets WORSE
```

And since VibeGraph sets **no `maxCameraRatio`** (`02-SIGMA-INTERNALS.md` §6), you can zoom out arbitrarily far — so overlap is **unbounded**. There is no worst case to solve for.

**This is why every previous round of parameter tuning failed.** The parameters were being tuned against a target that does not exist.

---

## The compounding factor: four dead knobs

`FA2_ITERATIONS`, `FA2_ITERATIONS_LARGE`, `NOVERLAP_MAX_ITERATIONS`, `FA2_OUTLIER_CLAMP_PERCENTILE` are declared and documented but **referenced nowhere** (`01-EVIDENCE-LOG.md` §5).

`FA2_OUTLIER_CLAMP_PERCENTILE` is the worst offender: its doc comment describes an outlier-clamping behavior that **does not exist in the code at all**.

Anyone tuning these observes **zero effect** — which, combined with Layer 3 making the real knobs ineffective too, produces the experience of "I changed everything and nothing helped."

---

## Why the fit view is the worst case

At fit view `r = 1`, so `node/gap ∝ r^0.25 = 1` — the *reference* ratio. Zooming out from there makes it worse.

Since fit view is the **default view a user sees first**, VibeGraph presents its near-worst-case appearance immediately, and any zoom-out makes it worse still.

grapuco is scale-invariant, so its fit view looks exactly as good as every other zoom level.

---

## The fix, stated precisely

Set `ZOOM_SIZE_POWER = 1.0`.

Then:
- node radius in graph units becomes the constant `S/K` (`02-SIGMA-INTERNALS.md` §4)
- `node/gap ∝ r^0 = constant` → **overlap becomes scale-invariant**
- de-overlap can be solved **once**, at any zoom, and is then correct at **every** zoom
- `graphology-noverlap` becomes correctable with a single conversion constant

Required companion changes (they are not optional — see `05-IMPLEMENTATION-PLAN.md`):
- lower `SIGMA_EDGE_SIZE` 0.25 → ~0.02, or edges balloon past ~11× zoom (`02-SIGMA-INTERNALS.md` §5)
- add `maxCameraRatio` clamp (UX; no longer strictly required for correctness once `p = 1`)

Explicitly **do not** change `itemSizesReference` — see the trap in `02-SIGMA-INTERNALS.md` §2.
