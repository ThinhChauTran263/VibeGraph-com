# BASELINE — Graph page BEFORE zoom-sizing changes (T2)

**Captured:** 2026-08-18 01:14 (+07:00 local) / 2026-08-17T18:14Z
**Task:** T2 baseline capture (`update/graph/08-TASK-ASSIGNMENT.md` §T2). No source code was modified.

## Project used

| Field | Value |
|---|---|
| **Project ID** | `2c67c31c-b65d-42ba-b128-43cf88501339` |
| **Project name** | `fatc-Grocery-Store-baseline-T2` |
| **URL** | http://localhost:5173/projects/2c67c31c-b65d-42ba-b128-43cf88501339/graph |
| **Owner account** | `user@vibegraph.com` (seeded local test account) |
| **Graph size** | 4,826 nodes / 9,422 edges / 205 Java files (ANALYZED) |
| **Rendered** | Safe Mode cap at 3,000 nodes (`VITE_GRAPH_SAFE_NODE_LIMIT=3000`) |

### Why not the original project `431ee9dc`

The project referenced in `01-EVIDENCE-LOG.md` §6 **no longer exists**:
`GET /api/projects/431ee9dc` → `404 PROJECT_NOT_FOUND`; no row in Postgres `projects`
(not in trash — it was purged). No live large graph existed in any accessible
account.

**Recovery:** re-imported the same source repo (`fatc-grocery-store`,
`d:\Users\User\IdeaProjects\fatc-grocery-store`, 205 `.java` files) via the app's
own Archive import UI (ZIP of `src/`, 21.9 MB) while logged in as `user@vibegraph.com`.
The original `431ee9dc` was this exact repo (per AUDIT-REPORT: owner
`thinhtran09177@gmail.com`, workspace name `ThinhChauTran263-fatc-Grocery-Store-*`),
so the baseline is source-equivalent. **Keep this project — T15 (after-fix QA) must
measure the same project ID for a valid comparison.**

> Note: the original measurement was on a project with 1,512 rendered nodes
> (filters: Field type OFF by default). This project has the same source; rendered
> node counts may differ slightly due to filter defaults at import time — compare
> against screenshots, not just counts.

## Measurements

### Settle time

**32 seconds** from navigation start until the `"Finalizing graph layout"` text
disappeared (DOM polled every 1 s via `document.body.innerText`).

- Text first appeared at page age ≈ 9 s
- Text gone at page age = **32 s**
- Reference range (`01` §6.1): 21–32 s → **matches** (consistent with
  `LAYOUT_AUTO_STOP_MS` 8 s + `NOVERLAP_AUTO_STOP_MS` 22 s ≈ 30 s)

### Screenshots

| File | Content |
|---|---|
| `2026-08-18-before-fit.png` | Full page at fit view, immediately after layout settled |
| `2026-08-18-before-zoom11.png` | Fit view + 11 zoom-in wheel ticks (viewport screenshot) |

### Overlap observation (fit view)

**Yes — nodes visually overlap at fit view.** The dense central cluster shows many
small node circles (≈10–15 px) touching and overlapping each other, matching the
prior finding in `01` §6.5 ("≈14 px, already visibly touching/overlapping").
Nodes remain overlapping in the zoom11 screenshot as well.

## Method notes (for reproducibility in T14/T15)

1. Login: `user@vibegraph.com` (the `/projects/:id/graph` route has
   `requiresUser: true` — ADMIN accounts are redirected away from it).
2. Settle measurement: after `navigate_page`, run a polling script checking
   `document.body.innerText.includes('Finalizing graph layout')` every 1 s, using
   `performance.now()` (page age from navigation start). Record age at disappearance.
   Cap 90 s.
3. **Wheel events must be dispatched on the `canvas.sigma-mouse` element.**
   Sigma's MouseCaptor binds to that specific canvas
   (`sigma.esm.js:1360`, `new MouseCaptor(_this.elements.mouse, ...)`); events on
   sibling canvases bubble to the wrong container and are ignored (verified: no
   `preventDefault` when dispatched on `.sigma-nodes`; `defaultPrevented === true`
   on `.sigma-mouse`).
4. Zoom ticks used: 11 × `WheelEvent('wheel', { deltaY: -100 })` at canvas center,
   300 ms gaps, then 1 s settle. Sigma default `zoomingRatio = 1.7` per tick.
5. No console errors or network failures were observed during the capture.
6. No renderer freeze encountered during this session (A5 remains unverified — T17).
