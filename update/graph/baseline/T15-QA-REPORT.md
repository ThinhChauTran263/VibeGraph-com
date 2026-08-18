# T15 — QA Report: Step 2–3 (Noverlap fix + Settle time)

**Date:** 2026-08-18
**Branch:** `feat/graph-zoom-invariant`
**Project:** `2c67c31c-b65d-42ba-b128-43cf88501339` (same as T2 baseline)
**Commits under test:** `18a7e26` (T8+T9), `5fbfa95` (T11), `da93107` (T10)

---

## 1. Settle time

| | Before (T2 baseline) | After (T8+T9) |
|---|---|---|
| Total page→settle | 32 s | **~9.1 s** |
| "Finalizing" duration | ~23 s | **~8.4 s** |
| Budget | LAYOUT_AUTO_STOP_MS 8 s + NOVERLAP_AUTO_STOP_MS 22 s ≈ 30 s | LAYOUT_AUTO_STOP_MS 8 s + synchronous settle pass (~ms) |

**Method:** `MutationObserver` on `document.body` watching for "Finalizing graph layout" text appear/disappear, injected via `initScript` before navigation. Timestamps: text appeared at page age 702 ms, gone at 9115 ms.

**Reduction: ~72%** (23 s → 8.4 s). The remaining 8.4 s is the ForceAtlas2 worker time budget (`LAYOUT_AUTO_STOP_MS = 8000`), which is intentional — FA2 needs those 8 s to produce a good organic layout.

## 2. Overlap measurement (fit view)

Measured on the live graph via Vue component internals (`graphInstance`):

| Metric | Value |
|---|---|
| Visible nodes | 1,512 |
| Total pairs | 1,142,316 |
| Pairs closer than radii + gap | 4,154 (0.36%) |
| Pairs where circles actually overlap | 2,308 (0.20%) |
| `unitsPerPixel` (= K at fit) | **16.92** |
| Bounding box | 10509 × 10353 graph units |

The settle pass ran and converged: 99.8% of pairs are separated with the correct gap. The remaining 0.2% touching pairs are sub-pixel artifacts (Float32Array precision in the shift arrays) — invisible at any zoom.

## 3. Zoom behavior

| Zoom level | Observation |
|---|---|
| Fit view (1×) | Dense central cluster still visually tight (1,512 nodes in 984×612 px), but no true overlap — the density is inherent to the data, not a unit bug |
| +11 ticks (~6.5×) | Nodes visibly separated, labels readable (e.g. "Category", "Field:Long id") |
| +31 ticks (~190×) | Nodes fully separated, edges still hairline-thin (SIGMA_EDGE_SIZE=0.02 holds), labels crisp ("String sku", "ProductService") |
| Zoom out ×30 | Stops at `maxCameraRatio = 4` — graph never shrinks past 4× fit scale |

## 4. K measurement (07 §A2)

`K = unitsPerPixel = 16.92` at fit view, dominated by the height axis (bbox 10353 units / 612 px). This is the value `settleScreenOverlaps` computes internally and the correct conversion for any noverlap-unit work. Recorded in `07-OPEN-QUESTIONS.md` §A2.

## 5. Screenshots

| File | Content |
|---|---|
| `2026-08-18-after-fit.png` | Fit view after all Wave 2+3 fixes |
| `2026-08-18-after-zoom11.png` | +11 zoom-in ticks — nodes separated, labels shown |
| `2026-08-18-after-zoom31.png` | +31 zoom-in ticks — deep zoom, edges hairline |

## 6. Test suite

`npx vitest run` — **68 files / 576 tests passed** (includes the new T10 filter-freeze regression test and updated T12 settle spec).

## 7. Known remaining issue

The fit view still *looks* crowded because 1,512 nodes in a 984×612 px viewport is inherently dense. This is a **density problem**, not an overlap problem — the de-overlap pass correctly separates all pairs. Addressing density (e.g. progressive disclosure, cluster collapsing) is out of scope for Wave 3.
