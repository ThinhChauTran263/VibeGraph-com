# Bugfix Design: Project Archive Upload

> Naming note: the historical Kiro spec slug is `project-folder-upload`, but the implemented scope in this spec is archive upload (`.zip`, `.tar`, `.tar.gz`), not a browser folder picker.

## Overview

Proposed for Sprint 2. This design follows `bugfix.md` and the product decision from 2026-05-31: archive upload is the primary onboarding flow; server-side `rootPath` registration remains a dev/internal fallback.

The fix adds a browser-driven archive upload path for Java projects while preserving the existing server-side local-path registration behavior. The first implementation should reuse the current disk-based parser/analyze pipeline by safely materializing uploaded archive entries into a backend workspace.

## Glossary

| Term | Definition |
|------|------------|
| **Archive upload** | Browser-driven onboarding where a user picks one project archive file and uploads it via `POST /api/projects/import-archive`. |
| **Workspace / workspace-root** | Server-owned writable directory tree under `vibegraph.import.archive.workspace-root` where uploaded archive entries are materialized for parsing. |
| **allowed-root** | `vibegraph.projects.allowed-root`, the configured directory that a user-supplied `rootPath` must resolve inside. |
| **ArchiveExtractor** | Helper that detects archive kind (ZIP/TAR/TAR.GZ) and safely iterates and extracts entries, rejecting unsafe ones. |
| **ArchiveImportService** | Service that validates the upload, allocates a workspace, drives extraction, registers the project, and triggers analysis. |
| **ArchiveImportProperties** | `@ConfigurationProperties` holding max archive size, workspace root, and ignored paths for archive import. |
| **multipart** | `multipart/form-data` request encoding that carries `name` plus the binary `file`; size-limited by Spring servlet multipart config. |
| **path traversal** | An archive entry name that escapes the workspace via `../` segments; must be rejected before any write. |
| **archive bomb** | A maliciously crafted archive that expands to excessive entries or bytes; mitigated by entry-count and uncompressed-byte caps. |
| **symlink entry** | An archive entry that is a symbolic link; rejected for MVP to avoid escape via link resolution. |
| **local-path fallback** | The existing `POST /api/projects` server-side `rootPath` registration flow, retained for dev/internal use. |

## Bug Details

The current onboarding flow only supports `POST /api/projects` with `CreateProjectRequest { name, rootPath, autoWatch }`. That `rootPath` must already exist on the backend host. In Docker Compose, the backend sees only mounted container paths such as `/projects`, not arbitrary folders on the user's machine. As a result, a browser user cannot add a project from their computer unless they manually copy source code into a backend-visible folder and paste a server-side path.

This violates the finalized product direction: users should add projects through the web UI by selecting one archive file (`.zip`, `.tar`, or `.tar.gz`) and clicking Add.

## Expected Behavior

The web UI should expose an Add Project archive upload flow. The backend should accept `POST /api/projects/import-archive` as multipart `name` + `file`, safely read the uploaded archive, parse `.java` files, store the graph in Neo4j, and return project metadata/status. The user should not provide a `rootPath`.

Existing local-path registration must continue to work unchanged for dev/internal fallback and self-host debugging.

## Hypothesized Root Cause

The product originally shipped a Sprint 1 vertical slice optimized for local development: backend reads a filesystem path it can already access, then `AnalyzeService.analyzeProject(projectId, rootPath)` calls `ParserService.parseProject(Path)`. No browser upload endpoint, writable upload workspace, archive extraction layer, or frontend upload form was implemented. The parser API also currently reads from disk, so uploaded archives need a safe materialization step before analysis.

## Correctness Properties

Property 1: Bug Condition - Archive upload onboarding is safe and complete

_For any_ request where the bug condition holds (the user onboards a project by uploading a `.zip`/`.tar`/`.tar.gz` archive from their own machine), the fixed system SHALL uphold all of the following invariants:

- Only `.zip`, `.tar`, and `.tar.gz` archives are accepted; every other archive type is rejected before any disk write.
- No archive entry is ever written outside the workspace root — path traversal (`../`), absolute paths, Windows drive paths (`C:\...`), and unsafe symlink entries are all rejected.
- An archive whose size exceeds the configured limit (default 100MB) is rejected, including at the Spring multipart level before `ImportController`/`ArchiveImportService` runs.
- Each extracted `.java` file's relative path inside the archive is preserved as the basis for its `filePath`.
- An import that extracts zero `.java` files does not create a project.
- A failed extraction or import leaves no partial project and no orphaned workspace.

**Validates: Requirements 2.4, 2.5, 2.6, 2.8**

Property 2: Preservation - Existing local-path flow and allowed-root enforcement unchanged

_For any_ request where the bug condition does NOT hold (server-side `rootPath` registration, listing, get, analyze, delete), the fixed system SHALL produce the same result as the original system, preserving the following invariants:

- The server-generated archive workspace path bypasses the user-input `allowed-root` check, while a user-supplied `POST /api/projects` `rootPath` still enforces `allowed-root`.
- Existing local-path registration, list, get, analyze, and delete behavior is unchanged.

**Validates: Requirements 3.1, 3.3, 3.4, 3.5**

## Fix Implementation

### Assumptions

1. The MVP accepts a single archive per request: `.zip`, `.tar`, or `.tar.gz`.
2. The default archive size limit is 100 MB and must be configurable in both application import config and Spring multipart request limits.
3. Backend may write uploaded content to a dedicated writable workspace because `ParserService` currently parses files from disk (`parseFile`/`parseProject`) and does not parse from stream yet.
4. Docker Compose currently mounts `./projects:/projects:ro`; archive upload must not write into `/projects`. It needs a separate writable path/volume, for example `/uploads` or `/tmp/vibegraph/uploads`.
5. Archive upload should return `202 Accepted` with project metadata/status. Parsing can be synchronous initially if needed for delivery, but the design should keep an async/progress path open.
6. Existing `POST /api/projects` local-path flow must keep its current behavior.

### Goals

- Add `POST /api/projects/import-archive` with `multipart/form-data` fields `name` and `file`.
- Let the user upload a project archive from the browser without typing `rootPath`.
- Safely unpack or materialize `.java` files into a project workspace, then reuse existing parser/analyze/Neo4j pipeline.
- Preserve each archive entry's relative path as the parsed file path basis.
- Report clear errors for unsupported archive type, oversize archive, unsafe entries, empty archive, and no `.java` files.
- Keep GitHub tarball import able to reuse the same archive ingestion pipeline later.

### Non-Goals

- No browser folder picker for MVP.
- No native desktop/Tauri/Electron local folder access.
- No private GitHub repository import.
- No long-term source-code retention policy beyond the local dev/MVP workspace; production retention should be decided before public launch.

### API Design

### Endpoint

```http
POST /api/projects/import-archive
Content-Type: multipart/form-data

name: string
file: project.zip | project.tar | project.tar.gz
```

### Response

Use the existing response envelope:

```json
{
  "success": true,
  "data": {
    "id": "project-id",
    "name": "sample-java",
    "rootPath": "/uploads/project-id/source",
    "status": "ANALYZING",
    "totalFiles": 0,
    "totalNodes": 0,
    "totalEdges": 0
  }
}
```

Initial implementation may return `READY` if parsing is still synchronous. If async is implemented in the same slice, return `ANALYZING` and push progress via WebSocket.

### Error Mapping

Return `400 Bad Request` for user-correctable archive errors:

- missing file
- blank project name
- unsupported extension
- archive size exceeds configured limit
- unsafe path entry
- unsafe symlink entry
- no parseable `.java` files

Return `413 Payload Too Large` if request-size handling is available through Spring multipart config; otherwise map to `400` with a clear message for MVP.

Return `501 Not Implemented` only while the endpoint is intentionally stubbed.

### Backend Components

### New Config

`ArchiveImportProperties`

```java
@ConfigurationProperties(prefix = "vibegraph.import.archive")
public record ArchiveImportProperties(
    DataSize maxSize,
    Path workspaceRoot,
    List<String> ignoredPaths
) {}
```

Suggested defaults:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${VIBEGRAPH_ARCHIVE_MAX_SIZE:100MB}
      max-request-size: ${VIBEGRAPH_ARCHIVE_MAX_REQUEST_SIZE:110MB}

vibegraph:
  import:
    archive:
      max-size: ${VIBEGRAPH_ARCHIVE_MAX_SIZE:100MB}
      workspace-root: ${VIBEGRAPH_UPLOAD_WORKSPACE:${java.io.tmpdir}/vibegraph/uploads}
      ignored-paths:
        - target
        - build
        - .git
        - .idea
        - node_modules
```

Spring's multipart limits are required because the framework can reject oversized uploads before `ImportController` or `ArchiveImportService` runs. `GlobalExceptionHandler` should map `MaxUploadSizeExceededException` or equivalent multipart-size failures to a clear `413 Payload Too Large` response where available.

Docker Compose should mount a writable volume if we want upload workspaces to survive container restarts:

```yaml
backend:
  volumes:
    - ./projects:/projects:ro
    - upload-workspaces:/uploads
  environment:
    VIBEGRAPH_UPLOAD_WORKSPACE: /uploads
```

### New Service Interface

`ArchiveImportService`

```java
public interface ArchiveImportService {
    ProjectResponse importArchive(String name, MultipartFile file);
}
```

### New Service Implementation

`ArchiveImportServiceImpl`

Dependencies:

- `ProjectService` to register/update project metadata
- `AnalyzeService` to reuse the existing parse → Neo4j pipeline
- `ArchiveExtractor` to safely materialize archive entries
- `ArchiveImportProperties` for limits/workspace/ignored paths
- `GraphUpdateController` or `SimpMessagingTemplate` later for progress updates

Recommended first implementation:

1. Validate name and multipart file.
2. Validate extension and size.
3. Allocate workspace:
   - `${workspaceRoot}/{projectId}/source`
   - use UUID before project creation or create project first with workspace path
4. Extract only safe `.java` files into workspace.
5. Reject if zero `.java` files were extracted.
6. Register the project with an internal archive-import creation path using `rootPath = workspace/source`.
7. Run `AnalyzeService.analyzeProject(projectId, workspace/source)`.
8. Update project stats.
9. Return `ProjectResponse`.

This reuses current `ParserService.parseProject(Path)` and avoids adding `parseString` in the first slice.

Do not send the generated workspace path through the same user-input `allowed-root` validation used by `POST /api/projects`. `vibegraph.projects.allowed-root` protects server-side paths typed by users; archive workspaces are generated by the server under `vibegraph.import.archive.workspace-root`. The implementation should either add an internal `ProjectService` creation method for server-owned workspaces or otherwise explicitly bypass only the user-input allowed-root check for archive-generated paths. Add a regression test proving local-path allowed-root validation still rejects user-supplied paths outside the allow-list, while archive import succeeds when its workspace is outside that allow-list.

### New Helper

`ArchiveExtractor`

Responsibilities:

- Detect archive kind: ZIP, TAR, TAR_GZ.
- Iterate entries.
- Reject unsafe entry names:
  - blank name
  - absolute path
  - Windows drive path (`C:\...`)
  - `..` path segment
  - normalized output path escapes workspace root
- Reject or skip symlinks. For MVP, reject archive containing symlink entries because resolving symlink safety across ZIP/TAR variants is easy to get wrong.
- Skip directories and ignored root segments.
- Skip non-`.java` files.
- Count extracted Java files.
- Optionally cap entry count and uncompressed bytes to reduce archive-bomb risk.

Output:

```java
public record ArchiveExtractionResult(
    Path sourceRoot,
    int javaFilesExtracted,
    long bytesWritten,
    List<String> warnings
) {}
```

### Controller Changes

Update `ImportController`:

```java
@PostMapping(path = "/import-archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<ProjectResponse>> importArchive(
    @RequestPart("name") String name,
    @RequestPart("file") MultipartFile file
) {
    ProjectResponse response = archiveImportService.importArchive(name, file);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
}
```

Do not change `POST /api/projects/import-github` or `POST /api/projects` request shapes.

### Frontend Design

### New Component

`vibegraph-web/src/components/import/ArchiveImportForm.vue`

UI states:

- idle: file input/drop zone, project name field, Add button disabled until valid
- selected: show file name, size, inferred project name, warning if unsupported
- uploading/analyzing: progress indicator and disabled controls
- success: route to graph view for returned project id
- error: inline clear error message

### New Composable

`vibegraph-web/src/composables/useArchiveImport.ts`

Responsibilities:

- Build `FormData` with `name` and `file`.
- POST to `/api/projects/import-archive`.
- Track loading/error/result.
- Later subscribe to `/topic/projects/{projectId}/status` when WebSocket is implemented.

### API Client

Add to `vibegraph-web/src/lib/api.ts`:

```ts
importArchive(name: string, file: File): Promise<ApiResponse<ProjectResponse>>
```

The current `api.post` helper is JSON-only (`Content-Type: application/json` + `JSON.stringify`) and must not be reused for `FormData`. Add a dedicated `postForm` helper or implement `importArchive` with `fetch` directly:

```ts
async function postForm<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    body: formData,
  })
  return unwrap<T>(res)
}
```

Do not set `Content-Type` manually for multipart requests; the browser must add the boundary.

### Data and Workspace Lifecycle

For MVP, uploaded source is written to a backend workspace because the parser reads from disk. The workspace path becomes the project's `rootPath` internally, but users never type it.

Cleanup policy:

- On failed extraction before project creation: delete the allocated workspace.
- On failed analyze after project creation: mark project `ERROR` if status support exists; otherwise delete partial project/workspace to avoid corrupt state.
- On project delete: delete workspace if it is under `vibegraph.import.archive.workspace-root`.

If delete-workspace-on-project-delete is too broad for the first task, document it as follow-up and avoid deleting arbitrary paths.

### Security Design

Always validate before writing:

- Extension allow-list: `.zip`, `.tar`, `.tar.gz`.
- Multipart max size: default 100 MB at both Spring request level and archive service validation level.
- Entry path normalization must stay inside workspace root.
- Reject absolute paths and `..` segments.
- Reject symlinks for MVP.
- Skip ignored directories.
- Cap extracted uncompressed bytes and entry count.
- Never execute archive content.
- Never trust `MultipartFile.getOriginalFilename()` for filesystem paths; use it only for display/extension inference.

## Testing Strategy

### Backend Unit Tests

- `ArchiveExtractorTest`
  - extracts safe ZIP with `.java` files
  - extracts safe TAR/TAR.GZ with `.java` files
  - skips ignored directories and non-Java files
  - rejects `../evil.java`
  - rejects `/absolute/Evil.java`
  - rejects Windows drive path
  - rejects symlink entry
  - rejects archive with no `.java` files

- `ArchiveImportServiceTest`
  - validates file extension
  - validates size
  - succeeds when archive workspace is outside `vibegraph.projects.allowed-root` because the workspace is server-generated
  - preserves local-path allowed-root rejection for user-submitted `POST /api/projects`
  - creates project with generated workspace path
  - calls analyze service with workspace source path
  - cleans workspace on extraction failure

- `ImportControllerTest`
  - multipart success returns `202`
  - oversized multipart maps to a clear `413`/upload-too-large response
  - missing file/name returns validation error
  - unsupported file maps to clear `400`

### Frontend Unit Tests

- `ArchiveImportForm.spec.ts`
  - disabled Add button until valid file selected
  - rejects unsupported extension client-side
  - posts `FormData` with `name` and `file`
  - displays API error

### Verification Commands

Backend:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Frontend:

```powershell
cd vibegraph-web
npm run type-check
npm run test:unit -- --run
npm run build
```

## Open Questions

1. Should uploaded source workspaces persist after import for watcher support, or be deleted after graph creation?
2. Should archive import parse synchronously for first slice or return immediately with async progress?
3. Should the first release support `.tgz` alias in addition to `.tar.gz`?
4. Should `ProjectResponse.rootPath` expose the internal workspace path to frontend, or should a later DTO hide it for archive-imported projects?
