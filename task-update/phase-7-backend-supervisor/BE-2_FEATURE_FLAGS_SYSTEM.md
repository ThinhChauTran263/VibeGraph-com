# BE-2 Prompt - Feature Flags / System Controls

```text
You are BE-2 for VibeGraph Phase 7 backend.

Scope: Feature Flags / System Controls only.
Base branch: latest poc.
Do not commit, push, or merge.

Read first:
- AGENTS.md
- RULES.md
- task-update/phase-7-backend-supervisor/README.md

Goal:
Make feature flags real enforcement controls, not only admin CRUD.

Must implement or verify:
- Admin CRUD for feature flags exists and is protected by ROLE_ADMIN.
- Disabled features are enforced in backend before expensive work starts.
- Disabled feature returns structured `FEATURE_DISABLED` with safe message.
- User-facing operations must be disabled logically, not silently no-op.

Required flags:
- `registration`
- `api_keys.create.global`
- `cli.push`
- `import.local`
- `import.archive`
- `import.github`
- `project.analyze`
- `mcp.enabled`
- each MCP child tool flag, for example `mcp.tool.<toolName>`
- `usecase.generate`

MCP child tool behavior:
- Check `mcp.enabled` first.
- Then check `mcp.tool.<toolName>`.
- If either disabled, reject before credit metering or resource-heavy work.

Do not work on:
- Credit pricing/debit logic except ordering around flag checks.
- Request rate/IP block.
- Frontend UI.

Suggested files to inspect:
- src/main/java/com/vibegraph/auth/service/AdminFeatureFlagService.java
- src/main/java/com/vibegraph/auth/web/AdminFeatureFlagController.java
- src/main/java/com/vibegraph/graph/**
- src/main/java/com/vibegraph/mcp/**
- src/main/java/com/vibegraph/auth/service/ApiKeyService.java
- project import/analyze controllers/services

Acceptance criteria:
- Turning off each flag blocks the real backend operation.
- Disabled operation returns deterministic structured error.
- MCP global and child flags are both tested.
- Flag checks happen before credit debit and before heavy filesystem/network work.
- Existing enabled behavior remains unchanged.

Required tests:
- `./mvnw "-Dtest=*FeatureFlag*,*Import*,*Analyze*,*ApiKey*,*Mcp*" test`
- Tests for every controlled flag or grouped parameterized coverage.
- Tests proving disabled feature does not debit credit.

Handoff:
Write `task-update/phase-7-backend-supervisor/BE-2_HANDOFF.md` with:
- feature flag keys added/used
- files changed
- tests run
- enabled/disabled behavior matrix
- frontend contract notes
```
