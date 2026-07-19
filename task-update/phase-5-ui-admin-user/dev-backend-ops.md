# P5-B1 - Backend Admin Ops Support

Owner: Backend ops worker

## Scope

Allowed:

- backend DTO/entity/repository/service/controller/migration/test files needed for:
  - system storage overview
  - feature flags
  - announcements
  - plans/credits CRUD
  - security/abuse monitor read endpoints
  - password change endpoint if missing
- frontend API type additions only if needed to unblock UI workers

Avoid:

- large user/admin UI rewrites
- changing existing auth/credit behavior without tests
- reverting 5.6-A changes

## Acceptance

- Add or verify endpoints needed by admin UI:
  - admin overview includes storage/system metrics or a dedicated storage endpoint exists.
  - plans CRUD and credit pricing CRUD exist or are explicitly documented as already existing.
  - feature flags CRUD exists, including MCP tool-level keys.
  - announcements CRUD exists.
  - security/abuse monitor endpoint exists or an MVP event table/query is added.
  - user password change endpoint exists for old/new/confirm flow.
- Admin-only endpoints require ADMIN.
- User password change requires current password and authenticated user.
- No hardcoded storage capacity; read from mount/config.
- No raw API keys/JWT/password logging.
- Add tests for new endpoints and services.

## Required Evidence

- targeted Maven tests
- `./mvnw clean test` if feasible
- final list of files changed
