# CLI -> MCP Credit Flow Test Evidence

## Scope

This test closes the local cross-module gap between browser device authorization and MCP billing:

```text
device start -> browser approval -> owned API-key selection -> one-time exchange
    -> API-key project binding -> MCP API-key authentication -> project ownership
    -> credit debit request -> delegated tool call
```

The test is intentionally deterministic and does not require Docker. It uses MockMvc and the real
device controller/service, real `ApiKeyService` ownership checks, real `ApiKeyAuthFilter`, real
`ProjectOwnershipGuard`, and real `MeteredToolCallback`. The persistence ports and final credit
service are mocked to keep this contract test runnable on a developer workstation.

PostgreSQL debit/ledger atomicity is covered separately by
`src/test/java/com/vibegraph/auth/integration/CreditDebitConcurrencyTest.java` when Docker is
available.

## Test Guarantees

| # | Guarantee | Test | Result |
|---|---|---|---|
| 1 | Device start returns the configured browser authorization URL and short-lived request data | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` (via `startDevice`) | PASS |
| 2 | Browser approval can select an API key only through the owner-scoped `ApiKeyService` path | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 3 | An API key selected by a different user is rejected and the device remains `PENDING` | `foreignSelectedKey_isRejectedBeforeApproval` | PASS |
| 4 | Correct PKCE/device/poll credentials return the bound project and plaintext key once | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 5 | A second exchange returns `CONSUMED` without returning the plaintext key | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 6 | The exchanged key is accepted by the real API-key filter and carries its project context | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 7 | MCP verifies the key-bound project before pricing/debit and delegates only after authorization | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 8 | MCP debit receives the authenticated user, exact project, source and operation code | `ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject` | PASS |
| 9 | A project mismatch is rejected before pricing, credit debit, or delegate execution | `apiKeyProjectMismatch_isRejectedBeforeDebit` | PASS |

## Validation Evidence

Command run:

```powershell
.\mvnw.cmd -Dtest=CliMcpCreditFlowTest test
```

Observed Surefire result:

```text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The first draft used a PostgreSQL Testcontainer directly in this class. The local Docker daemon was
not available, so that draft was correctly skipped by Testcontainers and was not counted as proof.
It was replaced with the deterministic MockMvc contract above; no test is silently skipped in the
targeted command.

## Known Gaps

- This is not a deployed production-server or real-browser test; it does not exercise the network,
  JWT filter chain, Flyway/PostgreSQL rows, Neo4j, or the Node CLI process.
- API-key authentication and project binding are exercised with real production classes, but their
  repository reads are mocked. `ApiKeyAuthFilterTest` and `CreditDebitConcurrencyTest` remain the
  database/concurrency evidence.
- The final `CreditBalanceService` call is mocked here to assert exact metering arguments. Real
  balance/ledger atomicity must still be run with Docker using:

  ```powershell
  .\\mvnw.cmd -Dtest=CreditDebitConcurrencyTest test
  ```

- A staging smoke test is still required for `vibegraph login`, CLI push, MCP JSON-RPC, and actual
  credit ledger decrement after deployment.
