# Next Session Notes - 2026-07-20

## Agreed Follow-Up

- Add realtime Audit Logs similar to Request events.
- Preferred MVP path: publish audit events over the existing admin realtime channel/WebSocket/STOMP flow, then prepend matching rows in `AuditView`.
- Keep a manual Refresh fallback.
- Keep current filters stable: if an admin filters by action/user/outcome, realtime rows should only appear when they match the active filter.
- Avoid page jump: live prepend should apply only on page 1 or when the current view is explicitly in live mode.
- Fix Audit action mismatch: UI hint should use actual backend actions such as `USER_BLOCK`, not `USER_BLOCKED`.
- Expand audit coverage for admin mutations that do not currently emit audit rows, especially create user, API-key creation toggle, and plan/pricing catalog mutations.
- Fix audit transaction boundary by moving persistence into a separate Spring bean so `REQUIRES_NEW` is applied through the Spring proxy.

## Security UI Behavior

- `Request events` remains realtime and capped in the store for recent rows.
- `Top users` and `Suspicious Networks` should stay aggregate-based, not stream every row. Fetch top results, keep the panel height to about 5 rows, and allow internal scrolling.
- If needed, raise frontend aggregate fetch limits to backend max 100 while keeping UI height constrained.
