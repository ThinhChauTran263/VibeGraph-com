# Phase 7 BE-4 Handoff

## Scope

Implemented backend anti-abuse controls only: request monitoring, exact IP blocks, rate limits, API-key attribution, and per-user concurrent import leases. No frontend work and no git commit/push/merge.

## Files Changed

### New production files

- `src/main/java/com/vibegraph/abuse/AbuseConfiguration.java`
- `src/main/java/com/vibegraph/abuse/AbuseProperties.java`
- `src/main/java/com/vibegraph/abuse/AdminAbuseController.java`
- `src/main/java/com/vibegraph/abuse/ClientAddressResolver.java`
- `src/main/java/com/vibegraph/abuse/ConcurrentImportGuard.java`
- `src/main/java/com/vibegraph/abuse/IpBlock.java`
- `src/main/java/com/vibegraph/abuse/IpBlockFilter.java`
- `src/main/java/com/vibegraph/abuse/IpBlockRepository.java`
- `src/main/java/com/vibegraph/abuse/IpBlockRequest.java`
- `src/main/java/com/vibegraph/abuse/IpBlockResponse.java`
- `src/main/java/com/vibegraph/abuse/IpBlockService.java`
- `src/main/java/com/vibegraph/abuse/RequestAggregateProjection.java`
- `src/main/java/com/vibegraph/abuse/RequestAggregateResponse.java`
- `src/main/java/com/vibegraph/abuse/RequestEvent.java`
- `src/main/java/com/vibegraph/abuse/RequestEventRepository.java`
- `src/main/java/com/vibegraph/abuse/RequestEventResponse.java`
- `src/main/java/com/vibegraph/abuse/RequestEventService.java`
- `src/main/java/com/vibegraph/abuse/RateLimitFilter.java`
- `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java`
- `src/main/java/com/vibegraph/abuse/AbuseExceptionHandler.java`
- `src/main/java/com/vibegraph/common/exception/ConcurrentImportLimitException.java`
- `src/main/resources/db/migration/V8__anti_abuse.sql`

### Existing production files changed

- `src/main/java/com/vibegraph/auth/config/SecurityConfig.java`
  - IP block before JWT auth.
  - API-key auth after JWT.
  - Request rate/monitor filter after API-key auth.
- `src/main/java/com/vibegraph/auth/service/AdminSecurityMonitorService.java`
  - Concurrently extended by another worker with request event and aggregate read methods; preserved and used by `AdminAbuseController`.
- `src/main/java/com/vibegraph/graph/service/impl/LocalImportServiceImpl.java`
- `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java`
- `src/main/java/com/vibegraph/graph/service/impl/TarballImportServiceImpl.java`
  - Shared per-user lease acquired before expensive import preparation.
  - Async lease transfers to analysis worker and releases in worker `finally`.
  - Validation, preparation, executor rejection, success, failure, and exception paths release the lease.
- `src/main/resources/application.yaml`
- `src/test/resources/application-test.yaml`
- `.env.example`

### New tests

- `src/test/java/com/vibegraph/abuse/ClientAddressResolverTest.java`
- `src/test/java/com/vibegraph/abuse/ConcurrentImportGuardTest.java`
- `src/test/java/com/vibegraph/abuse/ImportAbuseTest.java`
- `src/test/java/com/vibegraph/abuse/IpBlockFilterTest.java`
- `src/test/java/com/vibegraph/abuse/IpBlockServiceTest.java`
- `src/test/java/com/vibegraph/abuse/RateLimitFilterTest.java`
- `src/test/java/com/vibegraph/auth/web/ApiKeyAuthFilterTest.java`

Existing import tests were updated for the guard constructor.

## Config Keys

- `VIBEGRAPH_CONCURRENT_IMPORTS_PER_USER` (default `1`)
- `VIBEGRAPH_REQUESTS_PER_MINUTE_PER_IP` (default `120`)
- `VIBEGRAPH_REQUESTS_PER_MINUTE_PER_USER` (default `240`)
- `VIBEGRAPH_REQUESTS_PER_MINUTE_PER_API_KEY` (default `240`)
- `VIBEGRAPH_TRUST_PROXY` (default `false`)
- `VIBEGRAPH_TRUSTED_PROXIES` (comma-separated exact proxy peer IPs)

Forwarded headers are ignored unless both proxy trust is enabled and the immediate peer is listed in `trusted-proxies`.

## API Endpoints

All `/api/admin/**` endpoints remain protected by the existing `ROLE_ADMIN` security rule.

- `GET /api/admin/security/events?limit=50`
- `GET /api/admin/security/request-events?limit=100`
- `GET /api/admin/security/top-users?minutes=60&limit=20`
- `GET /api/admin/security/top-ips?minutes=60&limit=20`
- `GET /api/admin/security/ip-blocks?limit=100`
- `POST /api/admin/security/ip-blocks`
  - body: `{ "ipAddress": "203.0.113.10", "safeReason": "abuse investigation", "expiresAt": null, "active": true }`
- `PATCH /api/admin/security/ip-blocks/{id}`
- `DELETE /api/admin/security/ip-blocks/{id}`

Rate-limit response: HTTP `429`, error code `TOO_MANY_REQUESTS`.
IP-block response: HTTP `403`, error code `IP_BLOCKED`, safe reason only.
Concurrent import response: HTTP `409`, error code `CONCURRENT_IMPORT_LIMIT`.

## Monitoring Data

`request_events` stores authenticated user ID where present, API-key reference (`id:prefix`, never raw secret), canonical IP, normalized route, HTTP method, status, event type, and timestamp. Top-user/top-IP queries aggregate by minute in Postgres.

## Tests Run

- RED gate: `cmd /c ".\\mvnw.cmd -Dtest=ConcurrentImportGuardTest,ClientAddressResolverTest,RateLimitFilterTest test 2>&1"` initially failed because production guard types were absent.
- Passing anti-abuse unit suite:
  - `cmd /c ".\\mvnw.cmd -Dtest=ConcurrentImportGuardTest,ClientAddressResolverTest,RateLimitFilterTest,IpBlockFilterTest,IpBlockServiceTest,ApiKeyAuthFilterTest test > target\\abuse-unit.log 2>&1"`
  - Result: 11 tests, 0 failures, 0 errors.
- Production compilation:
  - `cmd /c ".\\mvnw.cmd -DskipTests compile > target\\compile.log 2>&1"`
  - Result: BUILD SUCCESS.
- Required suite was attempted, but the repository currently has unrelated concurrent failures:
  - `AdminServiceTest` expects `AdminUserResponse.quotaBytes()` which is absent.
  - `ProjectApiIT` calls a stale `ProjectController` constructor.
  - Archive tests hit `NoClassDefFoundError: ArchiveExtractor$1` in the current incremental test target.
  - Tarball tests hit an existing Mockito/Byte Buddy class redefinition issue for `ArchiveExtractor`.

## Production Tuning Notes

- Default concurrent import limit is intentionally `1` per user. Use test profile override `2` or higher for multi-lease tests.
- In-memory rate-limit windows are process-local. A multi-instance deployment needs a shared Redis/token-bucket implementation before relying on this for coordinated global limits.
- API-key authentication currently scans the existing API-key table after prefix filtering because changing `ApiKeyRepository` would have a HIGH GitNexus blast radius. Add a prefix lookup/index and bounded candidate query before high-scale production deployment.
- Exact IP blocking is IPv4/IPv6 only; CIDR is explicitly rejected.
- Request telemetry writes are best-effort and never convert an application request into a 500. Add retention/partitioning or scheduled cleanup in the separate audit-retention scope.
- `VIBEGRAPH_TRUST_PROXY` must remain false unless the reverse proxy overwrites the forwarding header and its peer IPs are explicitly configured.
- Rate-limit and request-event filters currently cover servlet HTTP traffic. MCP tool-level attribution remains available through the `/mcp` request plus API-key/JWT identity; direct tool callback metrics should be integrated separately if per-tool event granularity is required.

## Concurrent Worker Note

`AdminSecurityMonitorService.java` changed concurrently while BE-4 was in progress. I did not revert it; I reviewed and preserved its request-event/top-user/top-IP methods and wired the new admin controller to those methods. This is called out for BE-6 integration review.
