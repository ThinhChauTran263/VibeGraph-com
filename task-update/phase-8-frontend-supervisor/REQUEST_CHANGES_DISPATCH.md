# Phase 8 Request Changes Dispatch

Baseline branch: `poc`
Baseline commit: `d8a5fa3 feat: integrate phase 8 admin and user console`
Verdict source: `task-update/phase-8-frontend-supervisor/FE-6_FINAL_REVIEW.md`
Current verdict: REQUEST CHANGES, not merge-ready.

## Global Rules For Every Agent

- Start from latest `origin/poc`.
- Prefer a separate worktree or branch per agent.
- Do not commit, push, merge, delete files, or revert another agent's work.
- Do not stop to ask for `yes` for normal local edits, tests, QA, or non-conflicting overlap.
- If another agent edited a file you need, read current code, preserve their behavior, apply the smallest compatible edit, run focused tests, and record the overlap in your handoff.
- Keep real API integration. Do not add mock business behavior.
- Keep HttpOnly-cookie auth. Do not store JWT in localStorage.
- Disabled features must fail closed and be visibly disabled, not silently no-op.
- Every agent must write a `*_REQUEST_CHANGES_HANDOFF.md` file.

Suggested setup:

```powershell
git fetch origin
git checkout poc
git pull --ff-only origin poc
```

If using worktrees:

```powershell
git worktree add ..\VibeGraph-<agent-name> -b phase8-rc/<agent-name> origin/poc
```

## Dependency Order

1. `CladueCli` must land backend contract changes first:
   - capability DTO/endpoint
   - project-bound API keys

2. `Droid` runs after `CladueCli` handoff exists:
   - frontend API/types/stores consume the new backend contract

3. These can run in parallel with backend work because their scopes are mostly independent:
   - `ClaudeChat`: user error/retry states and user view hardening
   - `CodexCli`: STOMP reconnect, announcement banner, notifications nested main
   - `Kiro`: admin security/IP-block mutation and system flag UX

4. `gemini` runs last:
   - full re-review and Chrome QA after all handoffs exist

## Agent Board

| Agent | Card | State | Prompt |
| --- | --- | --- | --- |
| CladueCli | Backend contract blockers | Ready | `request-changes-prompts/01_BACKEND_CONTRACT_FIX_PROMPT.md` |
| Droid | FE contract wiring after backend | Ready after CladueCli | `request-changes-prompts/02_DROID_FE_CONTRACT_REVERIFY_PROMPT.md` |
| ClaudeChat | User error/retry and user capability UI | Ready | `request-changes-prompts/03_CLAUDECHAT_USER_ERROR_RETRY_PROMPT.md` |
| CodexCli | Reports reconnect and notifications | Ready | `request-changes-prompts/04_CODEXCLI_REALTIME_NOTIFICATIONS_PROMPT.md` |
| Kiro | Admin security/IP-block/system UX | Ready | `request-changes-prompts/05_KIRO_ADMIN_IP_BLOCK_PROMPT.md` |
| gemini | Final request-changes re-review | Ready after all handoffs | `request-changes-prompts/06_GEMINI_REREVIEW_PROMPT.md` |

## Merge Gate

Do not merge until `gemini` writes `FE-6_REQUEST_CHANGES_REREVIEW.md` with PASS.

Required final gates:

```powershell
npm --prefix vibegraph-web run type-check
npm --prefix vibegraph-web run test:unit -- --run
npm --prefix vibegraph-web run build
./mvnw test
git diff --check
git status --short --branch
```

Chrome QA required at:

- 320px
- 768px
- 1024px
- 1440px

Flows:

- user login
- user overview
- repositories import disabled/enabled states
- API keys project binding
- usage/subscription/profile/settings error and retry
- reports and notifications
- admin users/detail
- admin security/IP-blocks
- admin system feature flags
- admin announcements
- no console errors

## Copy Prompts In This Order

1. Send `01_BACKEND_CONTRACT_FIX_PROMPT.md` to CladueCli.
2. Send `03_CLAUDECHAT_USER_ERROR_RETRY_PROMPT.md` to ClaudeChat.
3. Send `04_CODEXCLI_REALTIME_NOTIFICATIONS_PROMPT.md` to CodexCli.
4. Send `05_KIRO_ADMIN_IP_BLOCK_PROMPT.md` to Kiro.
5. After CladueCli writes backend handoff, send `02_DROID_FE_CONTRACT_REVERIFY_PROMPT.md` to Droid.
6. After all request-change handoffs exist, send `06_GEMINI_REREVIEW_PROMPT.md` to gemini.
