# Gemini Prompt - Phase 6 Strict Review Gate

You are Gemini, assigned as strict reviewer for Phase 6.

Do not implement features unless supervisor explicitly asks. Review Claude and Kiro outputs and request changes if requirements are not met.

## Review Inputs

Read:
- `task-update/phase-6-user-admin-hardening/README.md`
- `task-update/phase-6-user-admin-hardening/CLAUDE_BACKEND_CLI_MCP.md`
- `task-update/phase-6-user-admin-hardening/KIRO_FRONTEND_UX.md`
- Claude handoff when available
- Kiro handoff when available
- Git diff on their branches/worktree

## Required Review Areas

### Product Fit
- Only confirmed VibeGraph features were implemented.
- Grapuco was used as visual reference only.
- No Workspaces, Spec Designer, Community, or Referral added.
- VibeGraph import form content was preserved.

### Backend Security
- Blocked users cannot access product flows.
- Safe block reason only; internal admin reason not leaked.
- Feature flags are enforced server-side.
- API key project binding enforces ownership and project restriction.
- MCP global and child tool flags work.
- Concurrent import guard cannot be bypassed by multi-tab requests.
- Rate limiting/IP block cannot be trivially bypassed by spoofed headers.
- Audit logs do not store secrets.

### Frontend UX
- Disabled feature flags visibly disable controls and explain why.
- User sidebar expanded/collapsed matches requirements: icon rail, hamburger, no abbreviation badges.
- User Overview has repository count, credits, plan, quick actions Repo/API Key/Reports.
- Repositories default view is project cards; import form only after New Repository.
- Import success navigates to graph/loading graph.
- API key create dialog requires project selection when backend contract requires it.
- Announcement popup and Notification page work if implemented.

### CLI/MCP
- API key project context works.
- CLI/MCP do not allow key for project A to operate project B.
- Raw API keys are not logged or printed.

## Commands To Run

Backend:
- `./mvnw clean test`
- focused backend tests relevant to changed files

Frontend:
- `cd vibegraph-web`
- `npm run type-check`
- `npm run test:unit -- --run`
- `npm run build`

Review:
- `git diff --check`
- `npx gitnexus detect-changes --repo VibeGraph-com`

## Output Format

Start with verdict:
- PASS
- REQUEST CHANGES
- BLOCKED

Then list findings by severity:
- Critical
- High
- Medium
- Low

Each finding must include:
- file/path and line if possible
- why it violates requirements/security
- exact required fix

End with:
- tests run
- residual risk
- whether supervisor can integrate

