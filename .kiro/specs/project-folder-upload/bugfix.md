# Bugfix Requirements Document

> Naming note: the historical Kiro spec slug is `project-folder-upload`, but the actual Sprint 2 scope is archive upload (`.zip`, `.tar`, `.tar.gz`), not browser folder-picker upload.

## Introduction

VibeGraph users expect to bring a project into the product the way they add files in any modern web app: open the "Add Project" UI, pick a single project archive file (`.zip`, `.tar`, or `.tar.gz`) from the operating system file explorer, click "Add", and have the backend ingest it for analysis. This is the finalized product direction (product decision 2026-05-31): **archive upload** is the primary onboarding flow, documented as FR-NEW-2 "Project Archive Upload" in `VibeGraph-specs-2month/requirements-trimmed.md` and as data-flow use case #1 in `VibeGraph-specs-2month/architecture.md`.

Today the product cannot do this. Project registration only accepts a server-side `rootPath` string (`POST /api/projects` with `CreateProjectRequest { name, rootPath, autoWatch }`) that must point to a directory that already exists on the machine running the backend. There is no archive upload control in the web UI and no upload endpoint on the backend. When VibeGraph runs under Docker Compose, the backend only sees the host `./projects` directory mounted read-only at `/projects`, so a user must manually copy their project into that folder on the host and then type a server-side path like `/projects/<name>`.

From the user's perspective this is broken: they have no way to bring a project from their own computer into VibeGraph through the browser. This document defines that gap as the bug and specifies the corrected archive-upload onboarding behavior exactly as the team finalized it, while preserving the existing server-side path registration flow as a dev/internal fallback so current setups keep working.

## Bug Analysis

### Current Behavior (Defect)

When a user tries to bring their own project into VibeGraph from the browser:

1.1 WHEN a user opens the VibeGraph web UI and wants to add a project from their own computer THEN the system provides no upload control in the "Add Project" UI for choosing a local project archive file.

1.2 WHEN a user wants to upload a project archive THEN the system exposes no endpoint that accepts an uploaded archive; project creation only accepts a JSON `rootPath` string referencing a directory that must already exist on the backend host.

1.3 WHEN a user submits a `rootPath` that points to a folder on their own machine (not present on the backend host) THEN the system rejects the request with "rootPath must be an existing directory".

1.4 WHEN VibeGraph runs under Docker Compose THEN the system requires the user to manually copy their project into the host `./projects` directory (mounted read-only at `/projects`) and type a server-side path, because there is no in-browser path to get the project onto the backend.

### Expected Behavior (Correct)

For the same conditions, the system should let the user onboard a project entirely through the browser by uploading a project archive, per FR-NEW-2:

2.1 WHEN a user opens the VibeGraph web UI and wants to add a project from their own computer THEN the system SHALL present an Upload control in the "Add Project" UI that lets the user pick a single project archive file (`.zip`, `.tar`, or `.tar.gz`) from the operating system file explorer and click "Add".

2.2 WHEN a user picks a project archive and clicks "Add" THEN the system SHALL accept it at `POST /api/projects/import-archive` as `multipart/form-data` containing `name` and `file`, read the archive, parse the `.java` files, store the resulting graph in Neo4j, and make the project available for analysis — without the user supplying a `rootPath`.

2.3 WHEN a user uploads a project archive from their own machine (a project that does not exist on the backend host) THEN the system SHALL accept and ingest it for that project instead of rejecting it as a non-existent directory.

2.4 WHEN the system reads an uploaded archive THEN the system SHALL accept only `.zip`, `.tar`, and `.tar.gz` archives and SHALL reject an archive that exceeds the configured size limit (default 100 MB) with a clear error.

2.5 WHEN the system extracts or streams archive entries THEN the system SHALL guard against unsafe entries — path traversal (`../`), absolute paths, and unsafe symlinks (archive-bomb guard) — and SHALL NOT write any entry outside the project's intended workspace.

2.6 WHEN the system processes archive entries THEN the system SHALL skip `target`, `build`, `.git`, `.idea`, `node_modules`, and any non-`.java` file, and SHALL preserve each `.java` file's relative path inside the archive as that node's `filePath`.

2.7 WHEN archive processing finishes THEN the system SHALL return a response containing `projectId` and the import/analyze status, and the frontend SHALL redirect to the project's graph once processing is complete.

2.8 WHEN an upload fails or is rejected (for example: empty selection, unsupported archive type, exceeds the size limit, unsafe archive entry, or no parseable `.java` files) THEN the system SHALL report a clear error to the user and SHALL NOT create a partially-registered or corrupt project.

### Unchanged Behavior (Regression Prevention)

The existing server-side path registration flow must keep working unchanged as a dev/internal fallback:

3.1 WHEN a client calls `POST /api/projects` with a `rootPath` that points to an existing directory on the backend host THEN the system SHALL CONTINUE TO create the project and store the canonical resolved path as it does today.

3.2 WHEN a client submits a blank or missing `rootPath` through the existing JSON path flow THEN the system SHALL CONTINUE TO reject the request with the current validation error ("rootPath is required").

3.3 WHEN `vibegraph.projects.allowed-root` is configured and a server-side `rootPath` resolves outside it THEN the system SHALL CONTINUE TO reject the request with "rootPath must be inside the configured allowed root".

3.4 WHEN a project has been registered (by either archive upload or server-side path) THEN the system SHALL CONTINUE TO support listing, retrieving, analyzing (`POST /api/projects/{id}/analyze`), and deleting projects exactly as before.

3.5 WHEN existing API clients call the current endpoints with their current request shapes THEN the system SHALL CONTINUE TO honor those contracts without breaking changes.

## Bug Condition Methodology

The following derives the bug condition and the properties that validate the fix. Definitions: **F** is the original (unfixed) onboarding behavior; **F'** is the fixed behavior; an input **X** is a project-onboarding request.

### Bug Condition

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type OnboardingRequest
  OUTPUT: boolean

  // The bug is triggered when the user wants to bring a project into VibeGraph
  // by uploading a project archive (.zip/.tar/.tar.gz) from their own machine,
  // rather than referencing a folder that already exists on the backend host.
  RETURN X.source = ARCHIVE_UPLOAD
END FUNCTION
```

### Fix Checking

```pascal
// Property: Fix Checking - Project archive upload onboarding (FR-NEW-2)
FOR ALL X WHERE isBugCondition(X) DO
  result ← onboardProject'(X)
  ASSERT archiveUploadControlWasOffered(X)
     AND uploadAcceptedAtImportArchive(result)        // POST /api/projects/import-archive, multipart {name, file}
     AND archiveTypeAllowed(X)                         // .zip / .tar / .tar.gz only
     AND archiveWithinSizeLimit(X)                     // default 100MB
     AND archiveSafelyExtractedOrStreamed(result)      // no ../, absolute path, or unsafe symlink escapes
     AND ignoredEntriesSkipped(result)                 // target/build/.git/.idea/node_modules, non-.java
     AND relativePathPreservedAsFilePath(result)
     AND projectRegisteredAndAnalyzable(result)        // projectId + import/analyze status, graph opens when done
END FOR
```

### Preservation Checking

```pascal
// Property: Preservation Checking - existing server-side path flow unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT onboardProject(X) = onboardProject'(X)
END FOR
```

For every non-archive request (server-side `rootPath` registration, blank-`rootPath` rejection, allowed-root validation, listing, get, analyze, and delete), the fixed system must behave identically to the original.
