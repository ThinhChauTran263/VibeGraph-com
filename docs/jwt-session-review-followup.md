# JWT / refresh-session review — findings and follow-up fixes

Handoff note for the agent that implemented the refresh-session work. Reviewed against the working
tree on 2026-08-10; Supabase, project-trash and rate-limit changes were excluded from scope.

**Nothing here is committed.** All changes below sit in the working tree alongside the original
implementation.

---

## Summary

The design is sound: no missing authentication, no token leakage, correct primitives. Three issues
were fixed. Two more are recorded as open questions for you to confirm rather than changed, because
they look like deliberate product decisions.

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | Concurrent refresh from two tabs was treated as replay → user signed out everywhere | High | Fixed |
| 2 | `refresh_sessions` had no retention sweep and grew without bound | High | Fixed |
| 3 | `isAccountUsable` caught every `RuntimeException` → a transient DB error permanently revoked all sessions | Medium | Fixed |
| 4 | Refresh window is absolute (7d from login), not sliding | — | Confirm intent |
| 5 | `JwtAuthFilter` has a test constructor passing `null` for `RefreshSessionService` | Low | Fixed (see §6) |
| 6 | Four database round trips per authenticated request | Medium | Fixed |
| 7 | Purging a project never deleted its extracted sources from disk | High | Fixed (see §7) |
| 8 | Frontend lint permanently red, hiding real findings | Medium | Fixed (see §8) |
| 9 | Orphan `system_control_settings` table nothing reads | Low | Fixed (see §9) |

---

## 1. Concurrent refresh looked identical to a replay (fixed)

### What happened

`rotate()` revoked the whole token family whenever it saw a token already marked `ROTATED`:

```java
if (current.getRevokedAt() != null) {
    if (ROTATED.equals(current.getRevokeReason())) {
        repository.revokeFamily(current.getFamilyId(), now, REUSE_DETECTED);
    }
    throw invalidToken();
}
```

`UserLayout.vue` runs `setInterval(refreshAccountState, 10000)`, so **every open tab polls on the
same 10-second timer**. When the 30-minute access token expires, all open tabs get a 401 inside the
same 10-second window and each calls `POST /api/auth/refresh` with the same cookie.
`findByTokenHashForUpdate` uses `PESSIMISTIC_WRITE`, so one tab wins; the loser blocks, then reads
the row after commit, sees `ROTATED`, and revokes the family. The user is signed out of every
device.

The single-flight guard in `authRefresh.ts` does not help: `refreshPromise` is module scope, and
each tab is a separate JS context.

This is not an edge case. Two tabs left open across a lunch break hit it reliably.

### Fix

A grace window in which a just-rotated token is served rather than punished — but only while the
replacement the winner received is still live:

```java
private RotatedSession rotateAlreadyRevoked(RefreshSession current, Instant now) {
    if (!ROTATED.equals(current.getRevokeReason())) {
        throw invalidToken();                      // logout / expiry / earlier replay
    }
    if (!isWithinGrace(current, now) || !hasLiveReplacement(current, now)) {
        repository.revokeFamily(current.getFamilyId(), now, REUSE_DETECTED);
        throw invalidToken();
    }
    User user = requireUsableAccount(current, now);
    return new RotatedSession(user, issueSibling(current));
}
```

The loser gets its **own sibling token** in the same family. The winner's replacement is not
revoked, so both tabs stay signed in, and from that point they hold distinct tokens and can never
collide again — the situation converges instead of repeating.

Deliberate properties:

- Replay outside the window still burns the family. `rotate_replayAfterGraceWindow_stillRevokesFamily`
  covers this.
- Replay inside the window with a dead family still burns it — the grace path requires a live
  replacement, so a token replayed after logout is not laundered through it.
- The pre-existing test `rotate_replayedRotatedToken_revokesFamilyAndRejects` has
  `revokedAt = NOW - 1s`, which is inside the window. It still passes because that fixture has no
  `replacedById`, which is exactly the fail-safe: no live replacement, no grace.

**Accepted trade-off:** a genuinely stolen token replayed within the window yields a valid sibling
instead of being detected. The window is therefore configurable and short.

```
vibegraph.auth.jwt.refresh-grace-ms   # default 30000; 0 disables the window entirely
```

### Alternative considered and rejected

Returning the winner's replacement token to the loser would be the tighter fix, but it is
impossible here by design — only the SHA-256 is stored, so the raw replacement value no longer
exists server-side. A client-side `BroadcastChannel` lock was also rejected: it fixes one browser
only and leaves the server exploitable by any other client.

---

## 2. `refresh_sessions` grew without bound (fixed)

Rotation only ever `INSERT`s. With a 30-minute access token an active user adds roughly 48 rows a
day, and nothing ever deleted them. There was no `@Scheduled` job touching the table anywhere in
`src/main` — verified by grep, not assumed. At 1000 users that is on the order of 17M rows a year.

The project already runs retention sweeps for `request_events`, `security_events` and project
trash, so this was an omission rather than a policy choice.

Added:

```java
@Scheduled(cron = "${vibegraph.auth.jwt.refresh-sweep-cron:0 15 3 * * ?}")
@Transactional
public void purgeExpiredSessions() {
    Instant cutoff = clock.instant().minus(refreshRetentionDays, ChronoUnit.DAYS);
    int removed = repository.deleteExpiredBefore(cutoff);
    ...
}
```

Matching on `expiresAt` alone is safe: a revoked row keeps its original expiry, and no row is usable
once that has passed.

`V19__refresh_session_retention.sql` adds `idx_refresh_sessions_expires_at`. Without it the sweep
full-scans exactly the table it exists to keep large.

```
vibegraph.auth.jwt.refresh-retention-days   # default 30
vibegraph.auth.jwt.refresh-sweep-cron       # default 03:15 daily
```

Note the sweep runs in-process, so it only fires while the app is up — same caveat as the other
sweeps in this codebase.

---

## 3. A transient failure revoked every session (fixed)

```java
try {
    accountSettingsService.assertNotBlocked(user.getId());
    return true;
} catch (RuntimeException ex) {     // ← swallowed everything
    return false;
}
```

`false` here leads to `revokeFamily(..., SECURITY_EVENT)`. A connection reset inside the settings
lookup was therefore indistinguishable from an administrative block, and cost the user every active
session permanently.

Fail-closed is right for auth; fail-closed *and destructive* on a transient error is not. Now only
`AccountBlockedException` returns `false`. Anything unexpected propagates, the request fails, and
the session family is left intact — covered by
`rotate_transientBlockLookupFailure_propagatesWithoutRevokingFamily`.

---

## 4. Absolute vs sliding refresh window (needs your confirmation)

`rotate()` copies the expiry forward:

```java
.expiresAt(current.getExpiresAt())   // inherited, never extended
```

A continuously active user is signed out exactly 7 days after login regardless of activity. That is
a defensible security posture, but the original summary said "refresh session 7 ngày" without
distinguishing absolute from sliding. Confirm this is intended — it is a visible product behaviour,
not an implementation detail.

Related: because every open tab polls every 10 seconds, an idle-but-open dashboard refreshes
indefinitely until that absolute cap. The 30-minute access token therefore shortens the revocation
window, not the practical session length.

---

## 5 & 6. Four round trips per request, and a filter that could skip its own check (fixed)

### What the count actually was

The original note said two queries per request. Re-counting `JwtAuthFilter.authenticate()` against
the code, it was **four**:

| # | Query | Origin |
|---|-------|--------|
| 1 | `users.findById` | `currentRestriction` → `assertProductAccess` |
| 2 | `user_account_settings.findById` | `assertProductAccess` → `assertNotBlocked` |
| 3 | `refresh_sessions.isActive` | the new session check |
| 4 | `users.findById` | **a second, identical load** at the end of the filter |

`open-in-view: false` and a separate `@Transactional` per guard method mean the persistence context
closes after each call, so #4 was a genuine extra round trip rather than a first-level cache hit.

Measured on this machine: the local Postgres container answers in well under a millisecond, but the
Supabase pooler in `ap-southeast-1` is **62ms per round trip** (TCP connect, averaged over 5). Four
round trips is ~250ms of pure authentication overhead per request if the primary database ever
moves there.

### Fix

One query. `UserRepository.findAuthSnapshot(userId, sessionId, now)` returns an `AuthSnapshot`
projection built from a `LEFT JOIN` on settings plus a correlated `EXISTS` on the session, and
`AccountAccessGuard.authenticate(...)` turns it into a verdict:

```java
public record AccountAccessDecision(
        AuthenticatedUser principal,
        AccountBlockedException restriction,   // null when the account is fine
        boolean sessionUsable) {}
```

The restriction is **returned rather than thrown** because several routes stay open to blocked and
deactivated accounts (logout, session state, support reports) — the filter, which knows the route,
decides.

`assertProductAccess` is untouched; the WebSocket interceptor and everything else still use it.

### Behaviour preserved deliberately

- Blocked outranks deactivated, matching the original order inside `assertProductAccess`.
- A missing account still raises `UnauthorizedException` → 401.
- A token with no `sid` is still accepted, so JWTs minted before sessions existed keep working
  until they expire. The query reports `sessionActive = false` for a null `sid` and the guard
  decides — the special case is visible rather than hidden in SQL.
- Default reason strings are unchanged. `AccountSettingsService.DEFAULT_BLOCKED_REASON` was widened
  from `private` to package-private so the guard reuses the same literal instead of copying it.

### Side effect: the nullable-service footgun is gone

The filter now takes exactly two collaborators:

```java
public JwtAuthFilter(JwtService jwtService, AccountAccessGuard accountAccessGuard)
```

The three-argument test constructor that passed `null` for `RefreshSessionService` — and with it the
`refreshSessionService != null` guard in the hot path that let a test silently skip revocation
checking — no longer exists. It cannot be constructed in a state that skips the check.

`JwtAuthFilterTest` was rewritten against the new seam: each test now states an outcome
(restricted / session dead / fine) instead of stubbing three collaborators. Two cases that were
previously unreachable were added — a legacy token without `sid`, and an account deleted after its
token was issued.

### Verified against a real database

`AuthSnapshotQueryIT` (Testcontainers PostgreSQL, 8 tests) executes the query rather than mocking
it, because a constructor projection over `LEFT JOIN` + `EXISTS` either returns the right row or
quietly returns the wrong one:

- live / revoked / expired session
- **a session belonging to a different account never counts as active**
- user with no settings row at all (the `LEFT JOIN` must not drop them)
- settings row present but not blocked
- blocked and deactivated reasons carried through
- unknown user yields empty

### Still not changed

The WebSocket path still checks the session on **every STOMP message** —
`isAuthSessionActive` is called from `handleProjectMessage` and `handleReportMessage`, not only on
CONNECT. That is the higher-frequency path of the two. A short (~5s) per-connection cache would be
defensible there, since the socket was already authenticated at CONNECT, but it trades instant
revocation for throughput and was left for you to decide.

---

## 7. Purging a project leaked its extracted sources forever (fixed)

This one is outside the JWT work but was found while auditing the leftovers, and it matters more
than anything else in this document.

`ProjectServiceImpl.deleteProject` removes the in-memory entry, the Neo4j graph and the file
watcher — and nothing else:

```java
public void deleteProject(String id) {
    ProjectResponse removed = projects.remove(id);
    ...
    graphRepository.deleteProject(id);
    fileWatcherService.stopWatching(id);
    log.info("Deleted project {}", id);      // the extracted sources are never touched
}
```

So **every** permanent delete left its workspace directory on disk forever, including the new trash
retention sweep. Worse, `project_usage` is removed by `ON DELETE CASCADE`, so the *accounted* quota
was freed while the actual bytes stayed: storage accounting and reality drifted apart with every
purge. The trash UI states that purging frees the storage — that was not true.

### Where the fix went, and why not the obvious place

`gitnexus impact` on `deleteProject` reports **CRITICAL**: 29 symbols, 9 direct callers, 5 execution
flows. Crucially, four of those callers are import-rollback paths, one of them
`LocalImportServiceImpl.cleanupCreatedProject` — and a LOCAL import's root path points at a
directory **the user owns**. Deleting inside `deleteProject` would have put user source code one
bug away from destruction.

The deletion therefore lives in `ProjectDeletionOrchestrator.purge()`, the single place that means
"destroy this permanently". Blast radius drops from 29 symbols to the purge path alone, and the four
import cleanups (which already remove their own workspace) are untouched.

Three ordering details that matter:

- The path is resolved **before** `deleteProject` runs, because that call drops the in-memory entry
  holding it.
- The directory is deleted **after** both planes succeed — a failure there leaves a sweepable orphan
  rather than a project the caller believes still exists.
- Deletion is best-effort and logged; it never turns a completed purge into an error.

### The guard is the whole point

```java
if (!candidate.startsWith(workspaceRoot) || candidate.equals(workspaceRoot)) {
    return null;   // user-owned directory, or the root itself — never delete
}
```

Only paths inside the configured import workspace were created by us. Everything else — LOCAL
imports, unresolvable paths, the workspace root itself — is left alone. The whole per-import
directory is removed (`…/uploads/github-<uuid>`), not just the `source` subfolder, so tarballs and
scratch files go too.

Six tests cover this, including the two that matter most: *purge never touches a local project's own
source directory*, and *purge refuses to delete the workspace root itself*. There is also one
asserting `moveToTrash` **keeps** the sources, since that is what makes a restore free.

### Historical cleanup

82 orphan directories (30 MB) had accumulated on the host. I cross-referenced every one against the
paths of all 40 live Neo4j projects — in both host (`D:\…`) and container (`/uploads/…`) form — and
deleted the 76 with no referent. 30 MB → 2.7 MB.

Worth knowing: the host `.vibegraph/uploads` is **not** what the running container uses.
`docker-compose.yml` mounts a named volume `upload-workspaces:/uploads`, so those host folders were
all legacy from non-Docker local runs.

### The two planes had also drifted, and now match

Neo4j held **40** `:Project` nodes against 13 Postgres ownership rows. The 27 extras had no
ownership row at all, which means no endpoint could reach them — every owner-scoped query filters on
ownership, so they were invisible and unusable, just occupying storage. Mostly test residue
(`leakcheck`, `cpgfix`, `lowverify`, `rt-test`, `ImpactSample`) plus a few old imports that lost
their ownership rows.

The owner ran the deletion — 11,990 nodes across 27 projects. Verified afterwards:

- Neo4j 13, Postgres 13, and the two id sets are **identical** (`diff` empty)
- zero nodes remain for any of the deleted project ids
- the one live repository still has all 2,496 of its nodes

The reverse direction was checked first and was already clean: every ownership row had its graph, so
nothing was missing on the control plane.

A note on process: I derived the 27 ids but did not execute the deletion — the permission layer
correctly refused a mass `DETACH DELETE` whose targets the agent chose rather than the user. That
seems like the right boundary for irreversible data loss, and worth keeping in mind if you script
anything similar.

## 8. Frontend lint was permanently red (fixed)

`npm run lint` reported 143 errors, so nobody could use it as a gate. The breakdown:

| Count | Rule | Verdict |
|-------|------|---------|
| 122 | `vitest/require-mock-type-parameters` | noise — fires on every plain `vi.fn()` |
| 17 | `no-irregular-whitespace` | real: a UTF-8 BOM at the head of 17 files |
| 2 | `jest/valid-expect` | **false positive** — `expect(value, message)` is legal Vitest, the jest plugin does not know that |
| 2 | `no-unused-vars` | real |

The two stylistic/incorrect rules are now off in `.oxlintrc.json`, the BOMs are stripped, and the
dead code is gone: `nextIsolateHiddenSet` in `src/stores/filter.ts` (plus `setEquals` and
`intersection`, which existed only to serve it) and an unused import in a spec.

Silencing the noise immediately paid for itself. `lint` runs `run-s lint:*`, so oxlint failing meant
**`lint:eslint` had never executed at all**. With oxlint green, eslint ran for the first time and
found another dead function, `userInitials` in `src/views/admin/UserDetailDrawer.vue`. That is the
argument for keeping the gate green: a permanently red lint hides exactly what it exists to find.

`npm run lint` is now clean end to end.

## 9. Orphan `system_control_settings` dropped (fixed)

`V20__drop_orphan_system_control_settings.sql`. The table came from the V16 whose script no longer
exists, and nothing in `src/main` reads it — the `import.concurrent.*` values it held are actually
served by `AbuseProperties`. A config table nobody reads is a trap: the next operator edits a limit
there, restarts, and nothing changes. The migration explains how to bring it back if runtime-tunable
limits are ever wanted.

---

## 10. Browser verification found a real defect in the layout fix (now fixed)

The graph panel work was verified in Chrome and **failed**: between roughly 1024 px and 1150 px the
detail panel still covered the search box. Four of five areas passed; this was the one.

### What I got wrong

The first fix reserved the panel's width on the toolbar:

```css
.graph-canvas__stage--detail-open .graph-top-controls {
  right: calc(1rem + var(--detail-width) + 0.75rem);   /* 24.75rem */
}
```

At 1050 px the app nav (268 px) and the resizable Explorer sidebar (288 px) leave a **493 px** stage.
Reserving 396 px plus a 16 px left margin left the toolbar about **81 px** wide, while the search box
is floored at `min-width: 12rem` (192 px). A flex item never shrinks below its min-width — it
**overflows its container**, and the overflow landed underneath the panel. The `flex-wrap` I had
added as an escape valve cannot help: wrapping moves an item to a new line, it does not shrink it.

The deeper error was choosing the wrong thing to measure. I gated on **viewport** width, but the
space that has to hold both is the **stage**, and the Explorer sidebar between them is
user-resizable. No viewport breakpoint can be correct for a layout whose available width the user
can drag.

### The fix

The stage is now a container query context, and the reservation applies only when the stage itself
can afford it:

```css
.graph-canvas__stage { container-type: inline-size; }

@container (min-width: 40rem) { /* reserve the panel width */ }
@container (max-width: 39.99rem) { /* dock the panel to the bottom instead */ }
```

The threshold is derived, not guessed: `1rem + 12rem search floor + 0.75rem gap + 23rem panel + 1rem`
= 37.75 rem, rounded to 40 rem. Below it the panel docks to the bottom — the same treatment the
narrow-viewport breakpoint already used and which was verified working at 1000 px.

### Measured, not assumed

Because the app requires a login I cannot drive, I reproduced the exact markup and CSS in a
standalone page, served it through the dev server, and measured `getBoundingClientRect()` overlap at
seven widths:

| Viewport | Stage | Search box | Vertical overlap with panel |
|---|---|---|---|
| 1440 | 883 px (55.2 rem) | 471 px | none — side by side |
| 1280 | 723 px (45.2 rem) | 311 px | none — side by side |
| 1150 | 593 px (37.1 rem) | 290 px | none — panel docked |
| 1100 | 543 px (33.9 rem) | 511 px | none — panel docked |
| 1050 | 493 px (30.8 rem) | 461 px | none — panel docked |
| 1000 | 443 px (27.7 rem) | 411 px | none — panel docked |
| 900 | 343 px (21.4 rem) | 311 px | none — panel docked |

The harness also caught my first attempt: I initially set the threshold to 60 rem, which would have
docked the panel at 1440 px too and regressed the layout that had just been verified as correct.
The measurements are what showed it.

The harness was deleted afterwards; it is not part of the tree.

### Re-verified in Chrome, and it exposed one more thing

Round 2 passed all three tests. The decisive one was dragging the Explorer sidebar wider at a
**fixed** 1440 px viewport: stage 888 → 536 px, and the panel moved to the bottom on its own with no
window resize. A viewport-based fix could never do that; it is the proof the layout now keys on the
right element.

The measured stage widths also corrected one of my assumptions. At 1000 px I predicted a ~443 px
stage; the real figure is 736 px, because below 64 rem the Explorer becomes a full-width row above
the stage rather than a column beside it. Harmless for the outcome, but it revealed a genuine flaw:

At 1000 px, `@media (max-width: 64rem)` docks the panel to the bottom **and** resets the toolbar to
`right: 1rem`, while `@container (min-width: 40rem)` still matches (the stage is 46 rem) and sets
`right: calc(1rem + 23rem + 0.75rem)`. Both target the same selector at specificity (0,2,0), and the
container block sits later in the file — so it won, and the toolbar reserved 23 rem for a panel that
was no longer beside it. Not a visible break (there was no overlap and the search box worked), just
~380 px of width thrown away between 900 px and 1024 px.

Fixed by giving the breakpoint rule a deliberate `.graph-canvas-wrapper` prefix (0,3,0) so it wins on
specificity rather than on source order, with a comment saying so — otherwise the next person tidies
away the "redundant" ancestor and silently reintroduces it.

Confirmed in the real app (round 3), measured at six widths:

| Viewport | `toolbarRight` | Toolbar width | Layout |
|---|---|---|---|
| 1440 | 396 px | — | panel right, no overlap |
| 1100 | 16 px | — | panel docked |
| 1024 | 16 px | 728 px | panel docked |
| 1000 | 16 px | 704 px | panel docked |
| 950 | 16 px | 654 px | panel docked |
| 900 | 16 px | 868 px | panel docked |

The three target widths return `16px`, so the toolbar no longer reserves space for a panel that has
already docked. 1440 still reserves 396 px with no overlap, so nothing regressed.

The 900 px row looks wrong at first glance — wider than at 950 px — but it is consistent: at
`max-width: 900px` `UserLayout` switches from a grid to `display: block` and the app nav becomes an
off-canvas drawer, handing the graph the full width. 900 − 32 = 868. Every other row matches
`viewport − 268 nav − 32 margins` to within a few pixels.

Still outstanding from that run: a screenshot at 1000 px and a final console/network sweep, lost when
the DevTools tooling dropped its connection. The numeric measurements are the substantive evidence
and they are complete; the console was clean on these same pages in round 2 and only CSS has changed
since, so the residual risk is low rather than zero.

### Test C settled the encoding question

An announcement containing `đường dẫn … ăn Ăn ệ Ệ ữ Ữ` rendered intact in both the toast and the
Notifications page. The earlier `âfffffff` record is junk test data, not an encoding fault between
Supabase and the UI.

---

## 11. Production hardening: login throttle, cookie default, security headers

Three gaps found while assessing the auth stack against ordinary production practice. None were
regressions — all three predate this work.

### 11a. The sign-in endpoint had no failure budget (highest impact)

The only protection was the general rate limiter: **120 requests per minute per address**, which is
120 password guesses per minute. There was no account lockout anywhere — `grep` for
`failedLoginAttempts` / `lockout` across `src/main` returned nothing.

`LoginThrottleGuard` now keeps two independent budgets and counts **failures only**, so a normal
sign-in never consumes any:

| Budget | Default | Stops |
|---|---|---|
| per address | 10 failures | one host spraying many accounts |
| per account | 5 failures | a botnet converging on one account from many addresses |

Either budget alone leaves the other attack open, which is why there are two.

Details that matter:

- The check runs **before** the password is verified, so a locked-out caller can neither keep
  guessing nor time how long verification takes.
- Only `InvalidCredentialsException` consumes budget. A blocked or deactivated account throws
  something else and must not count — otherwise an administrator action would stack a lockout on top
  of an existing restriction.
- A success clears both counters, so someone who mistypes twice and then signs in is not left one
  slip from a lockout.
- The address comes from `ClientAddressResolver`, which only honours `X-Forwarded-For` from a
  configured trusted proxy — a spoofed header cannot buy a fresh budget.
- The account key is lowercased and trimmed, so casing cannot either.
- The 429 body and message are identical whichever budget tripped, and whether or not the account
  exists. It must not become an account-existence oracle.
- The lockout log records the address but never the email, or the log becomes a list of valid-looking
  account names.

**Escalation applies to addresses only, and that asymmetry is the point.** Each further lockout from
the same address doubles the wait up to a 24-hour ceiling, because punishing a persistent source is
safe. The account lockout stays flat at 15 minutes: anyone who knows a victim's email can fail on
purpose, so an escalating account lockout would hand an attacker a way to lock a real user out for a
day. That would trade a slow brute-force risk for an easy denial-of-service — a bad trade.

Without escalation the arithmetic was: five guesses per fifteen minutes is 20 per hour per account,
so a 1,000-word password list takes about 50 hours. Slow, but not out of reach for someone patient
targeting one weak password. Doubling makes a persistent source uneconomic within a few rounds.

Same caveat as the rate limiter, and it is a real one: **state is per instance and in memory**. N
replicas allow N times the budget and a restart forgets everything. A shared store is the fix and is
out of scope here.

```
vibegraph.abuse.login-throttle.max-failures-per-ip        # default 10
vibegraph.abuse.login-throttle.max-failures-per-account   # default 5
vibegraph.abuse.login-throttle.window-ms                  # default 900000
vibegraph.abuse.login-throttle.lockout-ms                 # default 900000  (base wait)
vibegraph.abuse.login-throttle.max-lockout-ms             # default 86400000 (address ceiling)
```

Verified against the running backend: five failed sign-ins returned 401, the sixth returned **429**
with `Retry-After: 887` and code `TOO_MANY_LOGIN_ATTEMPTS`; a different account from the same
address still returned 401, confirming the two budgets are independent.

### 11b. `AUTH_COOKIE_SECURE` defaulted to false

Forgetting it in production would send session cookies over plaintext. The default is now `true`, so
plain HTTP is the case that has to opt out rather than the reverse. Browsers accept `Secure` cookies
on `http://localhost`, so local development is unaffected, and `docker-compose.yml` already passes
`false` explicitly.

### 11c. No Content-Security-Policy

Spring's defaults already supplied `X-Frame-Options: DENY`, `nosniff` and HSTS-over-HTTPS, but there
was no CSP, no Referrer-Policy and no Permissions-Policy. Added, and confirmed on a live response:

```
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), interest-cohort=()
```

Be clear about the limit: this service answers JSON and does not serve the SPA, so the policy only
covers HTML Spring itself can emit — error pages, OAuth redirect stops. **The frontend still needs
its own CSP on whatever serves it in production.** That is not done and is not something this
repository controls.

### Deliberately not done: binding refresh tokens to device or IP

Raised and rejected on the evidence. Binding to User-Agent protects nothing: an attacker who stole
the cookie stole it from a request that also carried the UA. Binding to IP breaks real users
constantly — mobile handover, carrier NAT, VPN toggling, IPv6 privacy addresses — which is why large
consumer services record device and location for display and risk scoring but do not hard-fail on a
mismatch.

The protection that actually applies here already exists: rotation with replay detection. A stolen
token is detected the moment either party refreshes, and the family is revoked. If cryptographic
binding is ever wanted, DPoP (RFC 9449) is the real answer, not a fingerprint.

Higher value for the same effort, in order: store address and user-agent on the session row for
**display**, an "active sessions" page with revoke (the `revokeAllForUser` plumbing already exists),
then new-session notifications.

---

## Things the original implementation got right

Stated explicitly because several are easy to get wrong:

- **The custom-header CSRF boundary actually holds.** It only works if CORS cannot be talked into
  allowing a hostile origin, and `CorsConfig` throws at startup when `allowed-origins` contains
  `*` while `allowCredentials(true)` is set. Without that guard the whole scheme would be decorative.
- Cookies: `HttpOnly`, `Secure` derived from `X-Forwarded-Proto`, `SameSite=Lax`, and the refresh
  cookie scoped to `/api/auth`.
- **Plain SHA-256 is the correct choice** for a 256-bit random token — no slow KDF needed, unlike
  password hashing. Storing only the digest is right.
- `UNIQUE (token_hash)` plus `PESSIMISTIC_WRITE` on rotation is the right race guard.
- `@Transactional(noRollbackFor = UnauthorizedException.class)` is the correct fix for the
  revocation-rollback bug you found — the revocation has to outlive the 401.

---

## Verification

| Check | Result |
|-------|--------|
| `./mvnw verify` | **981 unit + 69 integration, 0 failures**; coverage gate met |
| `npm run lint` | **0 errors** (was 143; eslint step now runs at all) |
| `npm run test:unit` | 533 tests, 0 failures |
| `vue-tsc --build` | clean |
| Flyway | migrated to V20; `system_control_settings` gone |
| `RefreshSessionServiceTest` | 11 tests, 0 failures (6 new) |
| `AuthSnapshotQueryIT` | 8 tests, 0 failures (Testcontainers PostgreSQL) |
| `JwtAuthFilterTest` | rewritten, all green, 2 cases added |
| Flyway | `Successfully validated 18 migrations`, then migrated to V19 |
| `idx_refresh_sessions_expires_at` | present |
| Backend container | `Started VibeGraphApplication`, 0 query-resolution errors |
| `GET /api/projects/trash` unauthenticated | 401 |

### One of your tests had to change, and it is worth knowing why

`RefreshSessionServiceIT.refreshSession_replayedToken_commitsFamilyRevocation` failed after the
grace window landed. That is not a regression — the test replays a rotated token *immediately*,
which is byte-for-byte the two-tab scenario the grace window exists to stop punishing.

Rather than weaken the test, its `JwtProperties` bean now sets `refreshGraceMs = 0`. What this test
is genuinely about is that the revocation **commits** instead of rolling back with the 401, and
that still needs a real database. With the window disabled the original assertions hold unchanged,
and the grace path is covered by unit tests, which can control the clock.

If you keep this test, keep the `refreshGraceMs = 0` line with it — without it the test silently
becomes a test of the grace path instead.

One build note: `mvn test-compile` first failed with nonsense errors
(`AdminOverviewResponse cannot be converted to com.vibegraph.auth.dto.AdminOverviewResponse`). That
was stale output in `target/`, not a code problem — deleting `target/classes` and
`target/test-classes` cleared it. Worth knowing if you hit the same thing.

Two notes on numbers. Your report said 1008 unit tests; this run reports 973 — worth reconciling,
it may be a different profile or a different way of counting. Integration tests were not re-run
here, so the replay-returns-401 evidence in your report still stands on your run, not this one.

## Files changed by this follow-up

- `src/main/java/com/vibegraph/auth/service/RefreshSessionService.java` — grace window, retention
  sweep, narrowed catch, logger
- `src/main/java/com/vibegraph/auth/config/JwtProperties.java` — `refreshGraceMs`,
  `refreshRetentionDays`
- `src/main/java/com/vibegraph/auth/repository/RefreshSessionRepository.java` —
  `deleteExpiredBefore`
- `src/main/resources/db/migration/V19__refresh_session_retention.sql` — new
- `src/main/java/com/vibegraph/auth/repository/projection/AuthSnapshot.java` — new
- `src/main/java/com/vibegraph/auth/repository/UserRepository.java` — `findAuthSnapshot`
- `src/main/java/com/vibegraph/auth/service/AccountAccessGuard.java` — `authenticate` +
  `AccountAccessDecision`
- `src/main/java/com/vibegraph/auth/service/AccountSettingsService.java` — `DEFAULT_BLOCKED_REASON`
  widened to package-private
- `src/main/java/com/vibegraph/auth/web/JwtAuthFilter.java` — one collaborator, one query, nullable
  service removed
- `src/test/java/com/vibegraph/auth/service/RefreshSessionServiceTest.java` — 6 new tests
- `src/test/java/com/vibegraph/auth/web/JwtAuthFilterTest.java` — rewritten
- `src/test/java/com/vibegraph/auth/integration/AuthSnapshotQueryIT.java` — new
- `src/test/java/com/vibegraph/auth/integration/RefreshSessionServiceIT.java` — `refreshGraceMs = 0`
- `src/main/java/com/vibegraph/common/ownership/ProjectDeletionOrchestrator.java` — delete extracted
  sources on purge, guarded to the managed workspace
- `src/test/java/com/vibegraph/common/ownership/ProjectDeletionOrchestratorTest.java` — 6 new tests
- `src/main/resources/db/migration/V20__drop_orphan_system_control_settings.sql` — new
- `vibegraph-web/.oxlintrc.json` — two rules disabled with reasons
- `vibegraph-web/src/stores/filter.ts`, `src/views/admin/UserDetailDrawer.vue` — dead code removed
- 17 frontend files — UTF-8 BOM stripped (no content change)

## Configuration added

```
vibegraph.auth.jwt.refresh-grace-ms        # default 30000, 0 disables the grace window
vibegraph.auth.jwt.refresh-retention-days  # default 30
vibegraph.auth.jwt.refresh-sweep-cron      # default 0 15 3 * * ?
```
