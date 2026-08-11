# UI re-verification — round 2

Follow-up to `docs/ui-verification-request.md`. That round returned 4 PASS / 1 FAIL. This one
re-tests the failure and closes two loose ends. Much shorter than round 1.

Still uncommitted. Still do not commit.

## What changed since your last run

You reported: *"ở 1100 px và rõ nhất tại 1050 px, panel bên phải đè lên ô tìm kiếm"*. That was
correct, and the diagnosis went deeper than the symptom.

At 1050 px the app nav (268 px) plus the Explorer sidebar (288 px) leave a **493 px** stage. The
toolbar was reserving 396 px for the panel, leaving it ~81 px wide — while the search box is floored
at `min-width: 12rem` (192 px). A flex item never shrinks below its min-width; it **overflows its
container**, and the overflow landed under the panel. The `flex-wrap` that was supposed to be the
escape valve cannot help, because wrapping moves an item to a new line without shrinking it.

The real mistake was measuring the wrong thing: the reservation was gated on **viewport** width, but
the element that has to fit both toolbar and panel is the **stage** — and the Explorer sidebar
between them is user-resizable. No viewport breakpoint can be right for that.

The fix makes the stage a container query context and reserves the panel width only when the stage
can afford it:

```css
.graph-canvas__stage { container-type: inline-size; }
@container (min-width: 40rem)    { /* reserve panel width on the toolbar */ }
@container (max-width: 39.99rem) { /* dock the panel to the bottom instead */ }
```

40 rem is derived, not guessed: `1rem + 12rem search floor + 0.75rem gap + 23rem panel + 1rem`
= 37.75 rem, rounded up.

This was measured in a standalone harness reproducing the same markup and CSS (no overlap at 1440,
1280, 1150, 1100, 1050, 1000, 900). **The harness proves the arithmetic, not the integration** —
that is what you are checking.

## Precondition: there is no repository left

The final step of round 1 permanently purged the only repository, by design. Start by importing one:

```
https://github.com/ThinhChauTran263/fatc-Grocery-Store
```

Wait for analysis to finish. **Do not delete it afterwards** — the owner needs it, and a pending
volume-cleanup step depends on knowing which folders are live.

---

## Test A — the previously failing widths (main event)

Open the repository → Graph view → click any node so the detail panel appears.

Check at each width. The stage width is what decides the layout, so read it directly in DevTools:

```js
document.querySelector('.graph-canvas__stage').getBoundingClientRect().width
```

| Viewport | Expected stage | Expected layout |
|---|---|---|
| 1440 | ~883 px (55 rem) | panel on the right, search box visible and usable |
| 1280 | ~723 px (45 rem) | panel on the right, search box visible and usable |
| 1150 | ~593 px (37 rem) | **panel docked to the bottom**, toolbar full width |
| 1100 | ~543 px (34 rem) | **panel docked to the bottom**, toolbar full width |
| 1050 | ~493 px (31 rem) | **panel docked to the bottom**, toolbar full width |
| 1000 | ~443 px (28 rem) | panel docked to the bottom (unchanged from round 1) |

**Pass condition at every width:** the search box is fully visible and clickable — no part of it
underneath the detail panel. Type into it at 1050 px to confirm it is actually usable, not merely
visible.

Watch for a regression too: 1440 and 1280 passed last round and must still show the panel on the
right, not docked. If either now docks, the threshold is too aggressive — report it.

## Test B — the resizable sidebar (the case the old fix could never handle)

This is the sharpest test, because a viewport-based fix would fail it while a container-based one
passes.

At a **fixed 1440 px viewport** with the detail panel open, drag the Explorer sidebar divider to
make the sidebar as wide as possible (it maxes out around 640 px).

**Expected:** as the stage shrinks past ~640 px, the panel **switches to the bottom-docked layout on
its own**, without the browser window changing size at all. The search box stays fully visible
throughout the drag.

**Failure:** the panel stays on the right and starts covering the search box as the sidebar grows.
That would mean the layout is still keyed to viewport width.

Drag it back to roughly the default (~288 px) and confirm the panel returns to the right-hand
layout.

## Test C — settle the `âfffffff` notification

You flagged a notification rendering as `âfffffff` and could not tell whether it was corrupt data or
a rendering bug. It cannot be inspected from the primary database because announcements now live in
Supabase.

Hypothesis: it is junk test data, not an encoding fault. `â` is a valid Vietnamese character, and
UTF-8-read-as-Latin-1 mojibake normally produces sequences like `Ã¢` or `â€™`, not a lone `â`
followed by `fffffff`. It reads like keyboard mashing with a Vietnamese IME.

**To settle it:** create an announcement (admin UI) whose body contains Vietnamese diacritics, for
example:

```
Kiểm tra hiển thị tiếng Việt: đường dẫn, thông báo, cảnh báo — ăn Ăn ệ Ệ ữ Ữ
```

Then view it as a user notification.

- Renders correctly → the old record is junk data. Close the question.
- Renders mangled → there is a real encoding bug somewhere between Supabase and the UI. Report the
  exact bytes you see and where.

---

## Report back

- Test A: pass/fail per width, with the measured stage width, plus a screenshot at 1050 px and 1440 px
- Test B: pass/fail, with a screenshot mid-drag at the point the layout switches
- Test C: which of the two outcomes, with a screenshot
- Any console errors or failed requests
- Anything that looked wrong but is not on this list

## Constraints

- **Do not commit.** The tree holds ~10 independent change sets on purpose.
- Do not modify source files — report defects, do not fix them here.
- **Do not delete the imported repository**, and do not run any `/uploads` cleanup command from
  earlier notes: the keep-list in those is stale now that the old repository was purged.
- No config changes are needed this round. `JWT_ACCESS_EXPIRATION_MS` should already be back at
  `1800000`; leave it there.
