# Phase 6 - User/Admin Hardening and Grapuco-Inspired UX

Status: READY FOR PARALLEL AGENTS  
Branch target: `DanhTest-intergration` or one branch per agent from it  
Supervisor: Codex  
Implementers: Claude (backend/CLI/MCP), Kiro (frontend UX)  
Reviewer: Gemini  

## Source Of Truth

This phase implements only requirements explicitly confirmed by the user. Grapuco is a visual/reference product only; do not copy unapproved features.

Important exclusions:
- Do not add Workspaces, Spec Designer, Community, or Referral to VibeGraph.
- Do not replace the current VibeGraph import form/prompt. Keep Local folder, Archive, and GitHub import UX/copy/logic. Only change when the form appears.
- Do not hardcode secrets or test credentials.
- Do not weaken existing auth, ownership, path safety, secret filtering, binary filtering, credit, or quota checks.

## Product Decisions

- User Overview is a summary page, not an import form.
- Repositories is where imported projects are listed and where import starts.
- `New Repository` reveals the existing VibeGraph import form.
- Successful import should open the graph/loading graph view for the imported project.
- API keys are project identity. For MVP, new API keys should require selecting a repository/project.
- Feature flags must be enforced in backend and reflected immediately in frontend UI.
- Blocked accounts must lose product access quickly. If currently logged in, the user is kicked out of the product surface. If logging in while blocked, show the safe block reason.
- Admin may block exact IPs first. CIDR is out of MVP unless explicitly approved later.
- Settings/Admin profile follows the user profile model: profile details and password change with old password + new password + confirmation, no OTP.
- Audit retention policy lives in Admin Settings; audit log viewing lives in Admin Security.

## Agent Board

| ID | Owner | State | Scope | Handoff |
| --- | --- | --- | --- | --- |
| P6-BE | Claude | Ready | Backend, DB migrations, CLI, MCP | `task-update/phase-6-user-admin-hardening/CLAUDE_BACKEND_CLI_MCP.md` |
| P6-FE | Kiro | Ready | `vibegraph-web` UI and API integration | `task-update/phase-6-user-admin-hardening/KIRO_FRONTEND_UX.md` |
| P6-REV | Gemini | Ready after agents report | strict review | `task-update/phase-6-user-admin-hardening/GEMINI_REVIEW_GATE.md` |

## Coordination Rules

- Claude owns backend contracts first. If Kiro needs an endpoint before Claude lands it, Kiro must use typed integration seams and clear TODO markers, not mock business behavior in app code.
- Kiro may update `vibegraph-web/src/lib/api.ts` and `vibegraph-web/src/types/*`, but must align with Claude's final DTOs.
- Do not edit unrelated files. Worktree is dirty; assume unrelated changes belong to others.
- Each agent writes a final handoff report in this folder.
- No push/merge without supervisor review.

## Global Merge Gate

Backend:
- `./mvnw clean test` passes.
- Focused security tests for block/session-state, feature flags, API key project binding, MCP tool flags, rate limit/IP block, audit retention, concurrent import guard pass.

Frontend:
- `npm run type-check` passes in `vibegraph-web`.
- `npm run test:unit -- --run` passes or any failures are explained and scoped.
- `npm run build` passes.
- Browser verification on `http://localhost:5173` with Chrome DevTools screenshots/snapshots for user overview, repositories, API keys, sidebar collapsed/expanded, admin system, admin security/settings.

Review:
- Gemini verifies all user-confirmed requirements.
- No CRITICAL/HIGH unresolved findings.
- `npx gitnexus detect-changes --repo VibeGraph-com` run before final integration; risk explained if existing dirty branch makes global risk noisy.

