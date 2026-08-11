# JWT Refresh Session TDD Report

Date: 2026-08-11

## Production Policy

- Access JWT lifetime: 30 minutes.
- Refresh-session absolute lifetime: 7 days.
- Refresh tokens are 32-byte opaque values; PostgreSQL stores only SHA-256 hashes.
- Every successful refresh rotates the token and preserves the original family expiry.
- Reuse of a rotated token revokes every active session in that token family.
- Browser auth uses HttpOnly `vg_session` and `vg_refresh` cookies plus the
  `X-VibeGraph-Client: web` CSRF boundary.

## Regression

The replay path updated the refresh-session family and then threw
`UnauthorizedException`. Both `RefreshSessionService.rotate` and its outer
`AuthService.refreshSession` transaction used the default rollback policy, so the
security revocation was rolled back before the `401` response was returned.

## Red

`RefreshSessionServiceIT` rotates a token against a real PostgreSQL container,
replays the original token through the Spring-proxied `AuthService`, and checks the
replacement session. Before the fix it failed because the replacement remained active:

```text
Expecting value to be false but was true
```

## Green

Both transactional boundaries now declare
`noRollbackFor = UnauthorizedException.class`. Successful rotations remain atomic,
while replay, expiry, and account-security revocations commit before the request is
rejected.

Focused integration result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Verification

- Backend unit tests: 1008, 0 failures, 0 errors.
- Backend integration tests: 61, 0 failures, 0 errors.
- JaCoCo bundle line coverage: 73.07% (repository gate: 70%).
- Auth package line coverage: 74.29%.
- The optional 80% TDD target is not reached, but the enforced repository gate passes.
- Docker backend rebuilt and restarted healthy on port 8080.
- Frontend remains available on port 5173.

Runtime replay smoke test:

```text
RegisterStatus          = 200
MeBeforeRefreshStatus   = 200
RefreshStatus           = 200
RefreshTokenRotated     = True
ReplayStatus            = 401
AccessAfterReplayStatus = 401
LogoutStatus            = 200
ProbeUserRemaining      = 0
```

Flyway migration `V18__refresh_sessions.sql` is active. Migration V16 was not restored;
the configured missing-migration policy remains in place for existing database history.
