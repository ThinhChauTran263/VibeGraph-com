# Audit request: find code that looks meaningful and does nothing

Hunt one specific defect class across the recent work on this branch: **code whose
comment or name claims an effect it does not have.** Not style, not coverage, not
architecture. Only claims that fail when tested.

## Why this audit exists

A `Content-Security-Policy` was added to the Spring backend with the comment "what this
policy protects is the HTML Spring itself can emit (error pages, OAuth redirect stops)".

Measured instead of assumed:

| Probe | Result |
|---|---|
| `GET /error` | `application/json`, **even with `Accept: text/html`** |
| `GET /khong-ton-tai` | `application/json` |
| `GET /oauth2/authorization/google` | 302, **empty body** |
| `GET /actuator/health` | JSON |
| springdoc/swagger in `pom.xml` | absent |

The service never returns a document, and CSP only applies when the browser builds one.
The header was inert on every response it was attached to, and the comment justifying it
was invented, not verified. It has been removed.

Two more of the same shape were then found by self-review and fixed:

- `Permissions-Policy` on the same JSON responses — same argument, same verdict.
- A CSS custom property `--detail-side-by-side-min: 40rem` declared in
  `GraphCanvas.vue` and **used nowhere**, because container and media queries cannot
  read `var()`. Worse than inert: it reads like it drives the breakpoint, so anyone
  changing it would expect the layout to move and nothing would happen.

Assume more of these exist. The author has already been wrong three times here.

## The method — this is the whole point

**Do not read a comment and grade it.** For each candidate, state the claim, then design
a probe that would fail if the claim were false, run it, and report the output.

A finding is only a finding with evidence attached. "This looks unnecessary" is not a
finding. "I sent this request and the header had no effect on the response" is.

Order of preference for probes:

1. Run it — curl the endpoint, load the page, execute the query.
2. Search for the consumer — if nothing reads a value, it cannot be doing anything.
3. Reason from the specification, and label it as reasoning rather than measurement.

## Scope

Commits `7138f4b..HEAD` on `backup-full-fixed-20260728`. Roughly: Supabase realtime
storage, rate-limit and telemetry, project trash, refresh sessions, login and
registration throttles, security headers, the graph panel layout, and the lint cleanup.

## Candidates worth probing first

These are guesses from the author, not findings. Confirm or clear each with evidence.

**Configuration nobody reads.** Every property added under `vibegraph.abuse.*`,
`vibegraph.auth.jwt.*`, `vibegraph.projects.*`. For each, grep for the reader. A setting
that is bound and never consumed is a lever connected to nothing —
`ProjectsProperties.trashRetentionDays` and the various `maximumTrackedKeys` are the
obvious places to start.

**Headers that do not apply to the response carrying them.** The backend still sends
`X-Frame-Options` and `X-Content-Type-Options` on JSON. `nosniff` genuinely matters;
check whether the other does anything here. Note these are Spring defaults rather than
hand-added, which changes whether removing them is worth the risk.

**Frontend CSP directives that match nothing.** `vibegraph-web/nginx.conf.template`
carries `worker-src 'self' blob:` and `form-action 'self'`. Does the app use a Web
Worker? Does it ever submit a real `<form action>`? A directive covering a feature the
app does not use is not harmful, but it is noise pretending to be protection.

**CSS that no longer has an effect.** `scrollbar-gutter: stable` on
`.graph-canvas__detail` was added when that element was a bordered card; it is now a
transparent layout column. Check whether it still changes anything. Same question for
the `transition: right` on `.graph-top-controls` in states where `right` never changes.

**Defensive branches that cannot be reached.** `LoginThrottleGuard.lockoutDuration` caps
an escalating value, `RefreshSessionService.nonNegative` validates configuration,
`ProjectDeletionOrchestrator.managedWorkspaceOf` has several guard clauses. For each,
work out whether real inputs can reach it. An unreachable guard is dead weight; a
reachable one that is untested is a different and more serious finding — say which.

**Tests that assert nothing.** Any test whose assertions would still pass if the
behaviour under test were deleted. The trash and throttle suites are new and the most
likely place for this.

**Duplicated helpers.** A private `MutableClock` was written inside a test while an
identical shared one already existed in the same package. Look for the same pattern
elsewhere, especially in the newer test files.

## What not to report

- Style, naming and formatting.
- Missing features or coverage gaps.
- Anything where the claim holds and the code simply looks verbose.
- Framework defaults, unless you can show removing them is both safe and worthwhile.

## Report format

For each finding:

- **Claim** — what the comment, name or config says it does
- **Probe** — the exact command or search run
- **Output** — what actually came back
- **Verdict** — inert / misleading / correct-but-unused / claim holds
- **Suggested action** — remove, fix the comment, or wire it up

Rank by how misleading each one is, not by size. A variable that looks like a control
knob and is not is worse than a header that merely wastes bytes: the first sends the
next person down a wrong path, the second only costs bandwidth.

Report findings only. Do not change code in this pass.
