# UI verification request — browser testing of uncommitted changes

Hand this to the agent doing the browser session. Everything below is in the working tree and
**nothing is committed**.

## Why this is needed

A batch of frontend and backend changes landed without ever being seen in a running browser. Unit
tests pass (981 backend + 69 integration + 533 frontend, lint clean), but three categories cannot be
covered by those tests:

- **CSS/layout** — vitest asserts DOM structure, not rendered geometry. Overlap, dead space and
  misaligned controls are invisible to it.
- **A brand-new feature** (project trash) whose every test mocks the API it talks to.
- **A cross-tab timing bug** that by definition needs two real browser tabs.

Report what you observe, not what the code claims. If something differs from the expected result
below, say so plainly with a screenshot — a disagreement is the most useful outcome this exercise
can produce.

## Environment

| | |
|---|---|
| Frontend | `http://localhost:5173` (Vite dev server, already running) |
| Backend | `http://localhost:8080` (Docker container `vibegraph-backend`, healthy) |
| Login | required; ask the owner for credentials or reuse an existing browser session |
| Live data | exactly **one** repository: `ThinhChauTran263/fatc-Grocery-Store` (id `77d4b2e4`) |

Do not import new repositories unless a test step says to — a pending disk-cleanup step keys off
the current folder list.

---

## 1. Graph detail panel layout (CSS — highest value)

Open the repository → open the Graph view → click any node so the right-hand detail panel appears.

**Expected**

1. The search box at the top **shrinks** and stays fully visible and usable. It must not be covered
   by the panel. (Before the fix the panel sat on top of it — the panel is `z-index: 7`, the toolbar
   `6`, and both were pinned to `right: 1rem`.)
2. Each panel has **one** border. There is no outer frame wrapping the panels and no empty gutter
   between that frame and the cards inside.
3. In the *Impact Analysis* card, `Profile` + `Depth` together span exactly the width of the
   **Analyze** button — the right edges line up. Previously Analyze wrapped onto its own full-width
   row while the two selects kept their content widths, leaving a ragged edge.

**Also try**

- Narrow the window gradually. The search box should shrink to a floor and then **wrap to its own
  line** rather than being squeezed to nothing (`min-width: 12rem`, toolbar is `flex-wrap: wrap`).
- Close the panel — the toolbar should expand back smoothly (200 ms transition, no jump).
- Below 1024 px the panel docks to the bottom; the toolbar should then reclaim the full width.

## 2. Sidebar collapse control

**Expected**

- Collapse the sidebar on a desktop-width window: the header shows **only the hamburger**. No `✕`.
- Resize below 900 px and open the drawer: the `✕` **reappears** — that is the drawer's close
  button and is intentional.
- The `✕` must actually close the drawer when clicked.

Background: the `✕` used to render at every width and did nothing on desktop, because
`#user-sidebar header button` (specificity 1-0-1) beat `.sidebar__mobile-close { display: none }`
(0-1-0). It is now `v-if="mobile"`, so on desktop it is not in the DOM at all.

## 3. Project trash — a feature that has never run for real

Run these in order on the single existing repository.

| Step | Expected |
|------|----------|
| Delete the repository from `/projects` | Confirm dialog says it goes to trash and is restorable for 3 days |
| Confirm | Card disappears; an **undo bar** appears (`[data-test="undo-delete"]`) naming the repository |
| Click **Undo** | Repository returns to the list immediately; undo bar disappears |
| Delete again, then open `/trash` | Row shows name, deleted timestamp, size, and a countdown ("in 2 days" style) |
| Click **Restore** | Row disappears from trash; repository is back in `/projects` |
| Delete again → `/trash` → **Delete now** | Confirmation dialog appears first |
| Confirm | Row disappears permanently |

**Then verify the disk leak is actually fixed.** Run this before the final purge and again after:

```bash
docker exec vibegraph-backend sh -c 'ls /uploads | wc -l'
```

The count must **drop by exactly 1**. This is the single most important assertion in this document:
until this change, `deleteProject` removed the graph and the ownership row but never the extracted
sources, so this number could only ever grow. Meanwhile `project_usage` is deleted by
`ON DELETE CASCADE`, so quota accounting and real disk usage were drifting apart on every purge.

Note the repository must be re-imported afterwards if the owner still wants it — the final purge is
irreversible.

## 4. Repository count parity

Open the **Overview** page and the **Repositories** page, and reload each several times.

**Expected:** both consistently show **1**.

Previously Overview showed 5 and Repositories showed 1, and the number changed depending on which
page was visited first. Two different endpoints disagreed and `syncAccountProjects` overwrote the
store.

## 5. Cross-tab session refresh — the highest-risk fix

This is the one that would hit real users first, and it needs a config change to be observable
inside a few minutes instead of half an hour.

**Setup**

1. In `.env`, change line 42 from `JWT_ACCESS_EXPIRATION_MS=1800000` to `=60000` (1 minute).
2. `docker compose up -d --build backend`

The access cookie now expires every minute. **This does not log anyone out** — the browser silently
exchanges the 7-day `vg_refresh` cookie for a new access token. Shortening it only makes that
exchange happen every minute instead of every 30 minutes, which is what makes the race observable.

**Test:** sign in, open **two tabs** on the app, leave both idle for 3–5 minutes, then interact
with each.

| Outcome | Meaning |
|---------|---------|
| Both tabs still signed in and working | Correct — the grace window works |
| Either tab bounced to `/login` | **The bug is still present** — report immediately |

Why this happens at all: every tab polls `refreshAccountState` on a 10-second interval, so when the
access token expires all open tabs hit 401 within the same window and refresh concurrently. One wins
the rotation; the loser presents a token that was just marked `ROTATED`. That used to be classified
as token theft and revoked the entire family, signing the user out everywhere. A 30-second grace
window now issues the loser its own sibling token instead, but only while the winner's replacement
is still live — replay outside the window still burns the family.

**Teardown (do not skip):** restore `JWT_ACCESS_EXPIRATION_MS=1800000` and rebuild the backend.

---

## What to report back

For each of the five sections: **pass / fail / not tested**, plus

- a screenshot for every visual claim in sections 1 and 2
- the before/after `/uploads` counts for section 3
- exact behaviour observed for section 5, including how long the tabs sat idle
- console errors and failed network requests seen at any point
- anything that looked wrong but was not on this list

## Constraints

- **Do not commit anything.** The working tree deliberately holds ~10 independent change sets.
- Do not modify source files. If a defect is found, report it — do not fix it in this session.
- The only permitted config change is `JWT_ACCESS_EXPIRATION_MS`, and it must be reverted.
- Do not delete projects, database rows or files beyond the steps described above.
