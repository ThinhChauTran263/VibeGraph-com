# Phase 6 Frontend UX Handoff

## Scope completed

- User sidebar uses inline SVG icons, includes Notification, persists collapse state, and presents compact account/credit context.
- Overview is summary-only with repository, credit, and plan data plus route-based quick actions.
- Repositories owns project cards, graph navigation, app confirmation delete, and a `New Repository` control that reveals the existing import panel.
- Import feature availability disables each local, archive, and GitHub method directly with a visible reason.
- API key creation is a project-selection dialog using a typed project-binding seam; it is explicitly disabled when the capability contract is unavailable.
- Notification list/detail and announcement banner use the real endpoint. Local storage dismissal is the fallback because no dismiss endpoint is assumed; no fake messages are rendered.
- Admin System groups persist collapse state and include `usecase.generate`.
- Admin Settings uses existing account profile/password APIs; audit retention is explicitly disabled pending a backend contract.
- Admin Security keeps real security events and explicitly disables request monitor, IP controls, and audit log surfaces pending contracts.

## Contract assumptions / follow-up

- `GET /api/session-state` is expected to expose `features`; 404/405/501 marks the capability contract unavailable.
- API key creation sends `{ name, projectId }`; list/create DTOs optionally expose `projectId` and `projectName`.
- User announcements are expected at `GET /api/account/announcements`.

## Verification

- `npm run type-check`: passed.
- `npm run build`: passed with the existing large-chunk warning.
- Unit tests were not run per instruction.
- No packages, native dialogs, commits, or pushes were used.
