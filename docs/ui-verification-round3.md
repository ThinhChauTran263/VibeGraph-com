# UI re-verification — round 3 (narrow)

Follow-up to `docs/ui-verification-round2.md`, which passed 3/3. This round checks one small change
made *because of* your round-2 measurements, plus the breakpoint seam it touches.

Short: one measurement repeated at six widths. Still uncommitted, still do not commit.

## What changed and why

Your round-2 numbers showed the stage is **736 px** at a 1000 px viewport, not the ~443 px I had
estimated — because below 64 rem the Explorer becomes a full-width row above the stage instead of a
column beside it.

That exposed a flaw. At 1000 px two rules fight over the same selector:

| Rule | Sets | Specificity |
|---|---|---|
| `@media (max-width: 64rem)` — panel is docked, so release the reservation | `right: 1rem` | (0,2,0) |
| `@container (min-width: 40rem)` — stage is 46 rem, so reserve the panel width | `right: calc(1rem + 23rem + 0.75rem)` | (0,2,0) |

Equal specificity, and the container block sits later in the file, so it won. The toolbar reserved
23 rem for a panel that had already docked to the bottom — roughly 380 px of width wasted between
900 px and 1024 px. Not a visible break (you correctly reported no overlap and a working search box),
just waste.

The breakpoint rule now carries a deliberate `.graph-canvas-wrapper` prefix so it wins at (0,3,0) on
specificity rather than on source order.

I measured this in a standalone harness, not in the app. **That is exactly what needs confirming** —
please treat my numbers as unverified.

## Setup

The repository is already imported (`431ee9dc`, `ThinhChauTran263/fatc-Grocery-Store`). Open it →
Graph view → click any node so the detail panel is open. Do not delete it.

If the Vite dev server is not running, start it (`npm run dev` in `vibegraph-web`).

## The measurement

At each viewport width below, run this in the console and record the output:

```js
(() => {
  const stage = document.querySelector('.graph-canvas__stage');
  const bar = document.querySelector('.graph-top-controls');
  const panel = document.querySelector('.graph-canvas__detail');
  const s = stage.getBoundingClientRect(), b = bar.getBoundingClientRect(), p = panel.getBoundingClientRect();
  return {
    viewport: innerWidth,
    stage: Math.round(s.width),
    stageRem: +(s.width / 16).toFixed(1),
    toolbarRight: getComputedStyle(bar).right,
    toolbarWidth: Math.round(b.width),
    panelDocked: p.width > s.width * 0.8,
    verticalOverlap: Math.round(Math.min(b.bottom, p.bottom) - Math.max(b.top, p.top)),
  };
})()
```

`verticalOverlap` ≤ 0 means the panel and the toolbar do not touch. A positive number is a failure.

| Viewport | Expected |
|---|---|
| 1440 | panel on the right, `toolbarRight` ≈ 396 px, overlap ≤ 0 |
| 1100 | panel docked, overlap ≤ 0 |
| **1024** | panel docked, **`toolbarRight` = 16px**, toolbar nearly the full stage width |
| **1000** | panel docked, **`toolbarRight` = 16px**, toolbar nearly the full stage width |
| **950** | panel docked, **`toolbarRight` = 16px** |
| **900** | panel docked, **`toolbarRight` = 16px** |

The three bold rows are the point of this round. If `toolbarRight` comes back as roughly `396px`
at any of them, the specificity fix did not take and the toolbar is still reserving space for a
panel that is not there.

## Also check the seam

`1024 px` and `1025 px` sit on either side of the breakpoint, where the viewport rule hands over to
the container query. Step between them and confirm:

- the layout does not visibly jump or flicker
- the search box stays fully visible and usable at both
- `toolbarRight` is `16px` on both sides

This is the seam most likely to misbehave, because two different mechanisms decide the same property
there.

## Report back

- The JSON output for each of the six widths
- Pass/fail on the seam check, with a screenshot at 1000 px showing the toolbar spanning the width
- Anything else that looked wrong

## Constraints

- Do not commit, do not modify source files.
- Do not delete the repository.
- Do not run any `/uploads` cleanup command from earlier notes — those keep-lists are stale.
