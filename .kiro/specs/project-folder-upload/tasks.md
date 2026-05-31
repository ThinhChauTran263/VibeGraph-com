# Implementation Plan: Project Archive Upload

## Overview

This is the implementation plan for the Project Archive Upload bugfix (FR-NEW-2). Archive upload (`.zip`/`.tar`/`.tar.gz` via `POST /api/projects/import-archive`) is the primary Sprint 2 onboarding flow for getting a project into VibeGraph. Server-side local-path registration is retained as a dev/internal fallback and its existing behavior must stay unchanged. The plan is sequenced by the wave dependency graph below.

## Task Dependency Graph

```json
{
  "waves": [
    {
      "wave": 1,
      "description": "Backend configuration and error contracts",
      "tasks": ["Task 1", "Task 2"]
    },
    {
      "wave": 2,
      "description": "Archive detection and safe extraction primitives",
      "tasks": ["Task 3", "Task 4", "Task 5"]
    },
    {
      "wave": 3,
      "description": "Archive import service and preservation checks",
      "tasks": ["Task 6", "Task 10"]
    },
    {
      "wave": 4,
      "description": "Project creation, analysis wiring, and cleanup",
      "tasks": ["Task 7", "Task 8"]
    },
    {
      "wave": 5,
      "description": "Backend HTTP surface",
      "tasks": ["Task 9"]
    },
    {
      "wave": 6,
      "description": "Frontend upload API and state",
      "tasks": ["Task 11", "Task 12"]
    },
    {
      "wave": 7,
      "description": "Frontend Add Project upload UX",
      "tasks": ["Task 13", "Task 14", "Task 15"]
    },
    {
      "wave": 8,
      "description": "Optional progress and verification assets",
      "tasks": ["Task 16", "Task 17"]
    },
    {
      "wave": 9,
      "description": "Full verification and documentation status cleanup",
      "tasks": ["Task 18", "Task 19"]
    }
  ]
}
```

## Implementation Order

Work top-down. Each task should leave the repo buildable and should avoid changing more than a small focused set of files.

## Tasks

The task list is grouped by implementation area; checkboxes are the source of truth for per-task completion status.

## Backend Foundation

- [x] Task 1: Add archive import configuration
  - Acceptance: `vibegraph.import.archive.max-size`, `workspace-root`, and `ignored-paths` bind from config with safe defaults; Spring multipart `max-file-size` and `max-request-size` are configured to accept the intended 100MB archive limit; Docker Compose backend gets a writable `upload-workspaces:/uploads` volume and `VIBEGRAPH_UPLOAD_WORKSPACE=/uploads` while keeping `./projects:/projects:ro` unchanged.
  - Verify: `./mvnw test -Dtest=ArchiveImportPropertiesTest` or include property binding coverage in service tests.
  - Files: `src/main/java/com/vibegraph/graph/importer/config/ArchiveImportProperties.java`, `src/main/resources/application.yaml`, `src/main/resources/application-docker.yaml`, `docker-compose.yml`.

- [ ] Task 2: Add archive import domain exceptions
  - Acceptance: user-correctable archive errors map to clear API errors and do not look like server crashes; Spring multipart oversize errors map to a clear `413 Payload Too Large` or equivalent upload-too-large response.
  - Verify: exception handler tests pass.
  - Files: `src/main/java/com/vibegraph/common/exception/ArchiveImportException.java`, `GlobalExceptionHandler.java`, tests.

- [ ] Task 3: Implement safe archive type detection
  - Acceptance: `.zip`, `.tar`, `.tar.gz` accepted; unsupported extensions rejected; original filename is never used as a filesystem path.
  - Verify: unit tests for supported/unsupported names including uppercase variants.
  - Files: `src/main/java/com/vibegraph/graph/importer/ArchiveType.java`, `ArchiveTypeDetector.java`, tests.

- [ ] Task 4: Implement ZIP extraction safety
  - Acceptance: safe `.java` files extract into workspace; ignored dirs/non-Java files skipped; `..`, absolute paths, Windows drive paths, and escaping normalized paths rejected.
  - Verify: `ArchiveExtractorTest` ZIP cases pass.
  - Files: `src/main/java/com/vibegraph/graph/importer/ArchiveExtractor.java`, tests/fixtures.

- [ ] Task 5: Implement TAR/TAR.GZ extraction safety
  - Acceptance: TAR and TAR.GZ follow the same safety rules as ZIP; symlink entries are rejected for MVP.
  - Verify: `ArchiveExtractorTest` TAR/TAR.GZ cases pass.
  - Files: `ArchiveExtractor.java`, tests/fixtures.

## Backend Import Flow

- [ ] Task 6: Add `ArchiveImportService` contract and implementation skeleton
  - Acceptance: service validates name/file/size/type and returns clear errors before extraction.
  - Verify: `ArchiveImportServiceTest` validation cases pass.
  - Files: `src/main/java/com/vibegraph/graph/service/ArchiveImportService.java`, `src/main/java/com/vibegraph/graph/service/impl/ArchiveImportServiceImpl.java`, tests.

- [ ] Task 7: Wire archive extraction to project creation and analysis
  - Acceptance: service creates a project using generated workspace `rootPath`, extracts archive, calls `AnalyzeService.analyzeProject`, updates stats, and returns `ProjectResponse`; archive-generated workspace creation does not fail because of user-input `vibegraph.projects.allowed-root` validation.
  - Verify: service test with mocked `ProjectService`, `AnalyzeService`, and `ArchiveExtractor` passes; regression test proves local-path allowed-root validation is still enforced for `POST /api/projects`.
  - Files: `ArchiveImportServiceImpl.java`, `ProjectService` contract/impl if an internal archive-project creation method is needed, tests.

- [ ] Task 8: Add cleanup on failed extraction/import
  - Acceptance: workspace is removed if extraction fails before a valid project is created; no arbitrary path deletion is possible.
  - Verify: failure tests assert workspace cleanup for paths under configured workspace root only.
  - Files: `ArchiveImportServiceImpl.java`, optional `WorkspaceCleaner.java`, tests.

- [ ] Task 9: Add `POST /api/projects/import-archive`
  - Acceptance: multipart endpoint accepts `name` + `file`, delegates to `ArchiveImportService`, returns `202 Accepted` with `ApiResponse<ProjectResponse>`.
  - Verify: `ImportControllerTest` multipart success and error cases pass.
  - Files: `src/main/java/com/vibegraph/graph/controller/ImportController.java`, tests.

- [ ] Task 10: Keep existing local-path registration behavior unchanged
  - Acceptance: `POST /api/projects` tests still pass; blank rootPath and allowed-root behavior unchanged.
  - Verify: existing `ProjectControllerTest` and `ProjectServiceImplTest` pass.
  - Files: tests only unless regression is found.

## Frontend Upload UX

- [ ] Task 11: Add API client method for archive import
  - Acceptance: `importArchive(name, file)` sends `FormData` to `/api/projects/import-archive` and returns typed `ProjectResponse` envelope; multipart requests do not use the existing JSON-only `api.post`, do not `JSON.stringify` the body, and do not manually set `Content-Type`.
  - Verify: unit test/mocked fetch request asserts form fields, endpoint, and absence of manual multipart `Content-Type` header.
  - Files: `vibegraph-web/src/lib/api.ts`, tests.

- [ ] Task 12: Add `useArchiveImport` composable
  - Acceptance: composable exposes `loading`, `error`, `result`, and `importArchive`; handles API errors consistently with existing graph/project calls.
  - Verify: composable unit test passes.
  - Files: `vibegraph-web/src/composables/useArchiveImport.ts`, tests.

- [ ] Task 13: Build `ArchiveImportForm.vue`
  - Acceptance: user can choose one `.zip`/`.tar`/`.tar.gz`, project name auto-fills from filename but is editable, Add disabled until valid, unsupported file shows inline error.
  - Verify: component tests pass for valid/invalid file selection and submit.
  - Files: `vibegraph-web/src/components/import/ArchiveImportForm.vue`, tests.

- [ ] Task 14: Integrate upload form into Add Project/Home flow
  - Acceptance: primary Add Project UX uses archive upload; legacy local-path flow is hidden behind dev/internal fallback or not exposed in primary UI.
  - Verify: manual browser check and unit tests for routing/state.
  - Files: `HomeView.vue`, import component wiring, router/store if needed.

- [ ] Task 15: Redirect to graph after successful import
  - Acceptance: after API returns project id, frontend navigates to the graph view for that project; user sees clear loading/error states.
  - Verify: component/router test or manual check.
  - Files: `ArchiveImportForm.vue`, `HomeView.vue`, router if needed.

## Optional Progress Path

- [ ] Task 16: Add progress events for archive import
  - Acceptance: backend sends project status/progress over `/topic/projects/{projectId}/status`; frontend subscribes when WebSocket composable is ready.
  - Verify: backend WebSocket test or manual STOMP client check; frontend displays status.
  - Files: `GraphUpdateController.java`, `ArchiveImportServiceImpl.java`, `useWebSocket.ts`, upload UI.

## End-to-End Verification

- [ ] Task 17: Add sample archive fixture for dev verification
  - Acceptance: a tiny sample Java project archive is available for tests or documented manual setup without committing large artifacts.
  - Verify: test fixture generates/uses archive and import succeeds.
  - Files: `src/test/resources/fixtures/archives/` or test helper code.

- [ ] Task 18: Run full verification
  - Acceptance: backend tests, frontend type-check/tests/build pass; Docker Compose still starts.
  - Verify:
    ```powershell
    .\mvnw.cmd test
    .\mvnw.cmd verify
    cd vibegraph-web
    npm run type-check
    npm run test:unit -- --run
    npm run build
    ```
  - Files: no code files unless fixes are needed.

## Documentation Updates

- [ ] Task 19: Update Sprint docs after implementation
  - Acceptance: `FR-NEW-2` status changes from target to implemented where appropriate; setup guide no longer says endpoint is missing.
  - Verify: `rg "import-archive.*chưa implement|target Sprint 2" VibeGraph-specs-2month` has no stale statements after code lands.
  - Files: `VibeGraph-specs-2month/*.md`, `team-setup-guide.html`.

## Notes

- Task 1 has been implemented; Tasks 2-19 remain pending unless their checkboxes are updated later.
- Archive upload is the primary Sprint 2 flow; local-path registration stays as a dev/internal fallback.
- WebSocket progress (Task 16) is optional and may slip past the first slice without blocking the core upload → analyze → graph flow.
- The Kiro spec slug is `project-folder-upload`, but the implemented scope is archive upload (`.zip`/`.tar`/`.tar.gz`), not a browser folder picker.
