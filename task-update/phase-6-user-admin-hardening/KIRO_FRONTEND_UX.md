# Kiro Prompt - Phase 6 Frontend UX

You are Kiro, assigned to VibeGraph Phase 6 frontend UX work.

Repository: `D:\Users\User\IdeaProjects\VibeGraph`  
Frontend root: `vibegraph-web`  
Branch: create/use your own branch from `DanhTest-intergration` if possible.  
Do not commit, push, or merge unless explicitly instructed.  
Write a handoff report to `task-update/phase-6-user-admin-hardening/KIRO_HANDOFF.md`.

## Mission

Redesign the user/admin UX according to the user's confirmed requirements, using Grapuco only as a visual/layout reference. Do not copy unapproved Grapuco features.

## Non-Negotiable Product Rules

- Do not add Workspaces, Spec Designer, Community, or Referral.
- Keep VibeGraph import form content and logic. Do not replace it with Grapuco's upload prompt/copy.
- Repositories page should show imported projects by default. The import form appears only after `New Repository`.
- Remove Tutorial button from Repositories header.
- User Overview is summary + quick actions, not import/list management.
- Use real icons, not letter badges like `OV`, `R`, `AD`, `SO`.
- Disabled feature flags must visibly disable controls and explain why.
- Avoid native browser alerts/prompts/confirms. Use app dialogs/toasts.
- Use Chrome DevTools to verify desktop and collapsed sidebar.

## Slice 1 - User Sidebar Redesign

Requirements:
- Expanded sidebar:
  - logo/brand
  - real icons + labels
  - VibeGraph menu only: Overview, Repositories, API Keys, Usage, Subscription, Reports, Notification, Tutorial, Settings, Sign Out.
  - compact account card: plan badge, credits, email.
- Collapsed sidebar:
  - hamburger/menu button at top.
  - icon rail only.
  - no text, no abbreviation badges.
  - account summary becomes a compact wallet/account icon.
  - sign out icon red.
  - no overlap with logo/toggle.
- Toggle behavior:
  - expanded uses collapse button.
  - collapsed uses hamburger to expand.
  - persist state in localStorage if already consistent with code style.

Acceptance:
- No overlap in collapsed mode.
- No letter badges remain.
- Browser verified at desktop width and narrow width.

## Slice 2 - User Overview Redesign

Requirements:
- Header:
  - `Welcome back, {displayName/userName}`
  - concise subtitle for VibeGraph workspace.
- Summary cards:
  - Repositories: number of imported projects.
  - Credits: remaining credits, with used-this-month if data exists.
  - Plan: current plan.
- Quick Actions:
  - Repo -> Repositories page/import entry.
  - API Key -> API Keys.
  - Reports -> Reports.
- Do not show import form on Overview.

Acceptance:
- Data comes from existing account/projects/usage APIs where available.
- Empty/missing values have clean fallback, not broken layout.

## Slice 3 - Repositories Page Redesign

Requirements:
- Default view:
  - Header `Repositories`
  - Subtitle
  - right action: `New Repository` only.
  - imported projects displayed as cards/grid.
- Each project card:
  - status icon/chip
  - project name
  - short id
  - created/last analyzed relative time if available
  - status: Analyzing/Analyzed/Failed/Ready
  - `Explore Graph` button
  - delete action with confirm dialog
- `New Repository`:
  - reveals current VibeGraph import form (Local folder, Archive, GitHub).
  - may be inline panel/drawer/modal, but should not be visible by default.
- On successful import:
  - navigate to graph/loading graph view for the new project.

Acceptance:
- Existing import functionality remains intact.
- Empty state is compact and encourages `New Repository`.
- Browser verified.

## Slice 4 - API Keys UX With Project Selection

Requirements:
- Create key flow should be modal/dialog, not a crude inline single input.
- Form fields:
  - Key name
  - Project/Repository selection
- For MVP, require selecting a project once backend supports it.
- List view shows project/repository associated with each key.
- If key creation is disabled by system/admin/plan:
  - disable entire create flow.
  - show direct message, not silent failure.
- One-time secret display remains clear and non-persistent.

Acceptance:
- Create button disabled until valid fields.
- Project list populated from user's imported projects.
- No raw secret shown in key list.

## Slice 5 - Notifications From Announcements

Requirements:
- Add user sidebar item: `Notification`.
- When user logs in and an active announcement targets them:
  - show highlighted popup/banner.
  - buttons: `Read`, `Close`.
- `Read` navigates to Notification page and opens detail.
- `Close` dismisses that popup for the user. Prefer persistent dismiss if backend supports it; otherwise session/local fallback with clear TODO.
- Notification page:
  - default list newest to oldest.
  - detail view includes creator name, date/time, title/content.

Acceptance:
- No announcement popup loops after dismiss.
- Notification page handles empty state.

## Slice 6 - Disabled Feature UX

Requirements:
- If System disables a feature, user UI disables every related action upfront.
- Applies at least to:
  - API key creation
  - local/archive/GitHub import
  - CLI push related messaging if present
  - MCP/usecase generation entries if present
- Disabled controls:
  - dimmed
  - non-clickable
  - short reason text
  - no silent button with no response.
- Use session-state/feature flag API from Claude when available.

Acceptance:
- Toggling backend flag and refreshing/session-state poll updates UI.
- Controls do not allow action attempts when disabled.

## Slice 7 - Admin System/Settings/Security UX

System:
- Add collapse/expand caret to sections such as Platform access, Import methods, CLI and API, MCP tool controls.
- Collapsed section shows only title/header.
- Store collapsed state locally if straightforward.
- Add `usecase.generate` flag.
- MCP tool controls show global MCP flag and child tool flags.

Admin Settings:
- Admin profile like user profile.
- Change password with old password, new password, confirm new password.
- Audit log retention setting.

Admin Security:
- Add UI surfaces for:
  - request monitor
  - rate-limit/security events
  - exact IP block/watchlist
  - audit log list
- These may be read-only/connected once backend contracts exist, but no fake mock data in app space.

Acceptance:
- No "API unavailable" placeholder if a real API exists.
- If backend not ready, show honest disabled/coming-soon state without pretending controls work.

## Browser Reference Notes

Grapuco reference observed:
- Sidebar uses material-style icons, active green state, compact plan/credit card.
- Collapsed sidebar becomes icon rail with hamburger at top.
- Overview uses three summary cards and quick actions.
- Repositories default shows cards; New Repository reveals import panel.
- API key modal includes default repository selector.

Do not copy:
- Grapuco Workspaces/Spec Designer/Community/Referral.
- Grapuco import copy/dropzone/warnings.
- Grapuco OTP password flow.

## Required Verification

Run:
- `npm run type-check`
- `npm run test:unit -- --run`
- `npm run build`

Browser verify with Chrome DevTools:
- user sidebar expanded/collapsed
- user overview
- repositories default and New Repository open state
- API key create dialog
- disabled feature state
- notification popup/page if implemented
- admin system collapsed sections
- admin settings/security pages

## Handoff Format

Write:
- exact files changed
- pages/screens verified with screenshots/snapshots
- APIs consumed or blocked waiting for backend
- test commands/results
- unresolved UX gaps

