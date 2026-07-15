# P5-R1 - Final Review

Owner: Supervisor/reviewer

## Scope

- Wait for P5-U1, P5-A1, P5-B1 handoffs.
- Review changed files for correctness, security, and contract mismatch.
- Run full gates.

## Acceptance

- Findings first, severity ordered.
- Verify:
  - no Grapuco copy
  - no fake runtime data where real API exists
  - no hardcoded secrets
  - admin endpoints require ADMIN
  - user actions require auth/ownership
  - sidebar collapse is accessible and persistent
  - storage totals are not hardcoded
  - feature flags are operational switches, not auth replacements
  - announcements do not allow unsafe HTML/script injection
- Run:
  - `./mvnw clean verify`
  - `cd vibegraph-web && npm run type-check`
  - `cd vibegraph-web && npm run test:unit -- --run`
  - `cd vibegraph-web && npm run build`
  - `git diff --check poc..DanhTest-intergration`
