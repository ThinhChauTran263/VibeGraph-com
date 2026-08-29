# VibeGraph — Graph Rendering & Import Performance Investigation

> **Status:** Research complete. No code changed yet.
> **Date:** 2026-08-13
> **Investigator:** Claude Opus 5 (session ended at quota limit — this folder is the handoff)

## Purpose of this folder

A complete, evidence-backed handoff so **another model/engineer can continue this work without re-deriving anything**.

Every claim here is backed by one of:
- an exact `file:line` reference in this repo or in `node_modules`
- a quoted source snippet
- a measurement taken from a live browser session

**Where something was NOT verified, it is explicitly marked `⚠️ UNVERIFIED`.** Do not treat unverified items as fact.

## Read in this order

| # | File | What it gives you |
|---|------|-------------------|
| 1 | [`01-EVIDENCE-LOG.md`](01-EVIDENCE-LOG.md) | Raw evidence + measurements. The factual base. |
| 2 | [`02-SIGMA-INTERNALS.md`](02-SIGMA-INTERNALS.md) | **How Sigma 3.0.3 actually scales sizes. Contains a critical trap.** |
| 3 | [`03-ROOT-CAUSE.md`](03-ROOT-CAUSE.md) | Why nodes overlap. The math. |
| 4 | [`04-GRAPUCO-REFERENCE.md`](04-GRAPUCO-REFERENCE.md) | Competitor teardown (grapuco.com). |
| 5 | [`05-IMPLEMENTATION-PLAN.md`](05-IMPLEMENTATION-PLAN.md) | **The actual work to do, in order.** |
| 6 | [`06-IMPORT-PERFORMANCE.md`](06-IMPORT-PERFORMANCE.md) | Backend import speed findings. |
| 7 | [`07-OPEN-QUESTIONS.md`](07-OPEN-QUESTIONS.md) | What is still unknown + traps to avoid. |

## TL;DR for whoever picks this up

**The problem:** VibeGraph's graph nodes visually overlap ("dính node") at every zoom level. The reference product (grapuco.com) never does.

**The root cause (3 layers, all verified):**
1. `graphology-noverlap` is fed node sizes in **pixels** but computes in **graph units** → the de-overlap pass under-separates by ~25%.
2. `ZOOM_SIZE_POWER = 0.75` means node size grows *slower* than distance → overlap is **worst when zoomed out**, and there is **no zoom clamp**, so there is no worst case to solve for. The problem is mathematically unsolvable as currently configured.
3. VibeGraph renders **fewer** nodes than grapuco (1,512 vs 2,020) and still looks more crowded — so this is **not** a data-density problem.

**The chosen fix (user approved "option B"):** make node size scale *linearly* with zoom so overlap becomes **scale-invariant** — solve it once, correct at every zoom forever.

**⚠️ THE #1 TRAP:** The obvious way to do this — flipping `itemSizesReference` from `'screen'` to `'positions'` — **does not work** and will make nodes explode ~100×+ in size. See [`02-SIGMA-INTERNALS.md`](02-SIGMA-INTERNALS.md). The real lever is `ZOOM_SIZE_POWER`.

## Ground rules for continuing this work

1. **Do not trust the comments in `runtimeConfig.ts`.** Several are provably wrong (documented in `01-EVIDENCE-LOG.md` §4). They describe behavior that does not exist in the code.
2. **Four config knobs are dead** (declared, documented, referenced nowhere). Tuning them does nothing. This is very likely where previous attempts burned their effort.
3. **Verify `⚠️ UNVERIFIED` items before relying on them.** The most important one is hit-testing behavior (`07-OPEN-QUESTIONS.md` §1).
4. The three changes in Step 1 of the plan are **interdependent** — ship them together or the graph will look broken mid-way.
