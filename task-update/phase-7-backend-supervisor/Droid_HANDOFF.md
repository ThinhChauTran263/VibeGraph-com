# Droid Phase 7 Credit / Quota Handoff

## Implementation

- Credit balance mutations remain database-atomic:
  - debit uses a conditional SQL update and cannot overspend;
  - admin adjustments and current-period limit updates mutate only their own columns;
  - ledger rows are persisted in the same transaction after a successful mutation;
  - exhausted credit now returns `CREDIT_EXHAUSTED`.
- Source quota reservation locks account settings, recalculates aggregate source usage, and rechecks the effective quota in the authoritative persistence transaction.
- Local patches additionally lock the project usage row before reading old file sizes or mutating the filesystem. Missing usage rows fail closed. Regular project creation initializes a zero-byte usage row and compensates by deleting the created project if initialization fails.
- Plan and per-user quota changes use the same account-settings lock, serializing admin changes with imports and patches.
- Source measurement is fail closed for local, archive, and GitHub imports. Symlinks, unsupported file types, I/O failures, and arithmetic overflow are rejected.
- Failed imports clean up project ownership and workspaces.
- User/admin quota contracts use MB while persistence and quota arithmetic remain in bytes:
  - usage displays with ceiling MiB rounding;
  - limits and remaining capacity display with floor MiB rounding;
  - MB-to-byte input conversion uses `Math.multiplyExact`.
- Web project analysis and GitHub/archive imports do not debit credits.

## Exact Credit Charge Points

1. MCP: `MeteredToolCallback` calculates `MCP_TOOL_CALL` and atomically debits before invoking the delegated tool.
2. CLI: `LocalPatchServiceImpl` calculates `CLI_PUSH` and atomically debits before filesystem mutation.

No other production `deductCredits(...)` call sites remain.

## Pricing

`CreditPricingService` implements:

```text
ceil(base + perFile * fileCount + perMb * (sourceBytes / 1,048,576))
```

It uses `BigDecimal`, preserves fractional MiB, applies one final ceiling, and throws on unsupported numeric overflow. Node and minimum pricing fields are intentionally excluded from the product formula.

## Migration

`src/main/resources/db/migration/V9__credit_quota_product_defaults.sql` upserts:

| Plan | Storage | Monthly credits |
|---|---:|---:|
| FREE | 100 MB | 100 |
| PRO | 500 MB | 500 |
| PRO_PLUS | 1024 MB | 1000 |
| MAX | 2048 MB | 2000 |
| ENTERPRISE | Admin override/contact sales | Admin override |

It also upserts the supplied pricing defaults for MCP, CLI, analyze, archive import, and GitHub import. Pricing rows for non-charge-point operations remain configurable but are not currently debited.

## Main Credit / Quota Files

- `auth/service`: `CreditBalanceService`, `CreditPricingService`, `AccountSettingsService`, `ProjectUsageService`, `StorageUnitConverter`, `AccountService`, `AdminService`, `AdminPlanManagementService`
- `auth/repository`: `UserCreditBalanceRepository`, `UserAccountSettingsRepository`, `ProjectUsageRepository`
- `auth/dto`: account usage, admin user, admin plan, and admin credit overview responses/requests
- `common/exception`: credit exhaustion, quota exceeded/below-usage API details
- `graph`: `ProjectController`, local/archive/GitHub import services
- `mcp/MeteredToolCallback`
- `patch/service/impl/LocalPatchServiceImpl`
- `common/ownership/ProjectOwnershipRegistrar`
- `src/main/resources/db/migration/V9__credit_quota_product_defaults.sql`
- PostgreSQL concurrency coverage in `CreditDebitConcurrencyTest` and `QuotaReservationConcurrencyTest`

## Verification

- Required command:
  - `./mvnw "-Dtest=*Credit*,*Quota*,*Plan*,*Pricing*,*LocalImport*,*LocalPatch*" test`
  - **111 tests, 0 failures, 0 errors**
- PostgreSQL concurrency is included in the required command:
  - credit debit/debit, debit/admin adjustment, limit update, and monthly re-anchor tests pass;
  - concurrent quota reservations allow only one reservation when both would exceed the limit.
- Archive/GitHub import tests after a clean rebuild:
  - **14 tests, 0 failures, 0 errors**
- Account/admin MB contract tests:
  - **35 tests, 0 failures, 0 errors**
- Project creation/cleanup tests:
  - **15 tests, 0 failures, 0 errors**
- Main-source compile passed.
- `git diff --check` passed; only existing LF-to-CRLF warnings were reported.
- Java and general code reviews were run. Actionable quota-locking, missing-usage, project-cleanup, and JSON-control-character findings were fixed. Frontend contract migration remains outside BE-3 scope.

## Coordination Notes

- The worktree contains extensive concurrent Phase 7 changes from other agents. No unrelated changes were reverted.
- `V8__anti_abuse.sql` is still untracked but no longer conflicts with another V8 migration in the current tree.
- GitNexus MCP impact/detect tools were unavailable in this session, so impact was reviewed manually and disclosed before implementation.
- No commit, push, or merge was performed.
