# Sprint 2 Demo Script

This script demonstrates the Sprint 2 VibeGraph flow: import a Java project, analyze it, inspect the graph, and query MCP context from an AI coding assistant.

## Demo prerequisites

- Docker Desktop or another Docker daemon is running, or Neo4j is available locally.
- `.env` exists with `NEO4J_USERNAME` and `NEO4J_PASSWORD`.
- VibeGraph is running:

```bash
docker compose up --build
```

Expected local surfaces:

| Surface | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080` |
| MCP server | `http://localhost:8080/mcp` |
| Neo4j Browser | `http://localhost:7474` |

If Docker is not available, run Neo4j separately and start the backend with `mvn spring-boot:run`, then start the frontend from `vibegraph-web`.

## Demo flow

### 1. Open VibeGraph

Open the frontend and confirm the project list loads.

Talking point:

> VibeGraph turns Java source code into a graph so developers and AI assistants can inspect architecture, dependencies, and impact before changing code.

### 2. Import a project

Use one of three import paths:

- **Local folder** (dev/self-host): register a Java project directory that already exists on the backend host. Enables realtime graph updates as you edit files.
- **Archive import**: upload a `.zip`/`.tar`/`.tar.gz` Java project archive.
- **GitHub import**: enter a public GitHub repository URL.

Expected result:

- The project appears in the project list.
- The project progresses through analysis and ends in an analyzed state.

Talking point:

> Three import paths cover the developer journey: **local folder gives realtime feedback** as you edit; archive works when you just want a snapshot; GitHub imports a public repo without cloning.

### 2b. Demo local realtime (recommended)

After importing a local folder:

1. Open the folder in your IDE (IntelliJ / VS Code).
2. Modify a `.java` file — add a new class or method.
3. Watch the graph update **in place** on Sigma (no page reload, no camera reset).
4. Delete the file — the corresponding node disappears from the graph.

Talking point:

> Realtime is the differentiating feature vs. archive/GitHub imports. `FileChangeBroadcaster` re-parses only the changed file, computes a delta, and broadcasts `INCREMENTAL` over WebSocket/STOMP.

### 3. View graph and diagram

Open the analyzed project.

Show:

- Graph view with nodes and relationships.
- Search and filters for narrowing the graph.
- Node detail panel for class or method metadata.
- Diagram panel if diagrams are available for the selected view.

Talking point:

> The graph is useful for human exploration, while the same indexed context also powers MCP tools for AI assistants.

### 3b. Open source from Node Detail

Click a class or method node, then open the source viewer:

- The `CodeViewerModal` shows the file with **project-relative path** (no absolute server path leak).
- Sensitive properties (`password`, `secret`, `token`) are redacted at render time.
- The window is bounded (line-cap) — safe for large files.
- Access is restricted to files under the project's registered source root.

Talking point:

> Source access uses the same guard as MCP source tools (`SourceGraphSupport` + `SourceFileService`). Path traversal, symlink escape, and workspace-root violations are rejected before any read.

### 4. Connect an MCP client

Use this MCP client configuration:

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "http://localhost:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

Confirm the client can list the **15 available tools** (see `docs/mcp-integration.md` for full list). Highlights to demo:

- `get_project_architecture`, `get_class_context`, `get_impact_analysis`, `get_layer_pattern` (4 core context tools)
- `trace_endpoint`, `find_references`, `get_method_cpg_context` (deeper graph navigation)
- `get_source_file`, `search_source`, `get_method_source` (source access with redaction)
- `find_related_tests`, `suggest_test_plan`, `plan_code_change`, `explain_failure_path` (senior-agent tools)
- `get_project_conventions` (durable repo conventions from `ai-memory.md`)

Talking point:

> MCP lets an AI assistant ask VibeGraph for repository-specific context instead of guessing architecture from a prompt alone. The tool surface grew from 4 planned tools (Sprint 2) to 15 shipped tools (Sprint 3), covering context lookup, source access, tests, and change planning.

### 5. Query project architecture

Prompt for the AI assistant:

> Use `get_project_architecture` for project `<projectId>` and summarize the layers, naming conventions, detected patterns, and warnings.

Expected result:

- Layer counts are returned.
- Architecture patterns and naming conventions are summarized.
- Warnings are shown if the analyzer found them.

Talking point:

> This is the first context call an AI assistant should make before proposing new code.

### 6. Query class context

Prompt for the AI assistant:

> Use `get_class_context` for class `<ClassName>` in project `<projectId>`. Explain its methods, fields, incoming relations, and outgoing relations.

Expected result:

- The matched class is identified.
- Methods and fields are listed.
- Direct incoming and outgoing relations are shown.

Talking point:

> Class context helps the assistant understand local dependencies before editing or extending a class.

### 7. Query impact analysis

Prompt for the AI assistant:

> Use `get_impact_analysis` for node `<fully.qualified.ClassName>` in project `<projectId>` with depth `3`. Identify direct dependents, transitive dependents, and the risk level.

Expected result:

- Direct impact list is returned.
- Transitive impact list is returned where applicable.
- Risk level and notes guide what should be tested.

Talking point:

> Impact analysis is the safety step before refactoring, renaming, or changing shared code.

### 7b. Switch impact profile

`get_impact_analysis` (and the FE `ImpactAnalysisPanel`) support three profiles that answer different questions:

| Profile | Question it answers | Edges traversed |
| --- | --- | --- |
| `dependency` (default) | "What breaks if I change this?" | reverse `CALLS`/`IMPORTS`/`EXTENDS`/`IMPLEMENTS`/`INJECTS` |
| `structural` | "What contains / is contained by this?" | `CONTAINS`/`DEFINES`/`HAS_METHOD`/`HAS_FIELD`/`HANDLES_ROUTE` |
| `type-data-flow` | "How does data flow through this?" | type edges + deep CPG (`READS`/`WRITES`/`CATCHES`/`STEP_IN_FLOW`) |

Prompt:

> Re-run `get_impact_analysis` for the same node with `profile=structural`, then again with `profile=type-data-flow`. Compare the blast-radius shape across profiles.

Talking point:

> Different refactors need different lenses — renaming a class is a dependency question; moving a package is a structural question; changing a shared field type is a data-flow question.

### 8. Query layer pattern

Prompt for the AI assistant:

> Use `get_layer_pattern` for layer `SERVICE` in project `<projectId>`. Summarize examples, common dependencies, naming conventions, do rules, and don't rules.

Expected result:

- Representative classes in the layer are shown.
- Common dependencies and conventions are returned.
- The assistant can follow project patterns when generating code.

Talking point:

> Layer pattern context turns existing code into practical generation rules for new code.

### 9. Explore the Code Property Graph (optional, advanced)

VibeGraph indexes more than the architecture graph. The default canvas shows only
the readable **structural** layer; deeper relationships are hidden until requested.

In the graph view's Filter panel:

1. Note the default canvas shows structural edges only — `DEFINES`, `CONTAINS`,
   `HAS_METHOD`, `HAS_INNER`, `EXTENDS`, `IMPLEMENTS`, `OVERRIDES`, `IMPORTS`,
   `CALLS`, `HANDLES_ROUTE` — so it stays readable.
2. Click **Edge types → Show all** to reveal the CPG-lite + deep layer:
   - CPG-lite: `TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `HAS_FIELD`, `INJECTS`,
     `INSTANTIATES`, `THROWS`, `ANNOTATED_BY`.
   - Deep CPG (only when enabled, see below): `READS`, `WRITES`, `CATCHES`.
   - Inferred flow: `STEP_IN_FLOW`.
3. Click **Node types → Show all** to reveal `LocalVariable` nodes (deep CPG).
4. Select a method node and inspect the **Node Detail** panel: incoming/outgoing
   relations now list CPG-lite and deep relations (e.g. a service method that
   `READS`/`WRITES` a field, `CATCHES` an exception, or is a `STEP_IN_FLOW` step).

Talking points:

> `STEP_IN_FLOW` is an **inferred** execution-flow view from each route handler
> through in-project calls — it is NOT a copy of `CALLS`. It is reachability-filtered
> and de-duplicated, so its count is strictly smaller than `CALLS`.

> Body-level data-flow (`LocalVariable` + `READS`/`WRITES`/`CATCHES`) is **opt-in**.
> It is OFF by default (`VIBEGRAPH_PARSER_DEEP_CPG=false`) because local variables
> can multiply graph size; enable it only when you need data-flow detail.

## Success checklist

- [ ] A Java project is imported successfully (local, archive, or GitHub).
- [ ] Local realtime demo: editing a `.java` file updates the graph in place without reload.
- [ ] The project reaches analyzed status.
- [ ] Graph or diagram exploration works for the imported project.
- [ ] Node Detail → source viewer opens a redacted, project-relative file.
- [ ] MCP client connects to `http://localhost:8080/mcp` using streamable HTTP and lists 15 tools.
- [ ] `get_project_architecture` returns layer and pattern context.
- [ ] `get_class_context` returns class details and relations.
- [ ] `get_impact_analysis` runs across all 3 profiles (`dependency`, `structural`, `type-data-flow`) with differing blast-radius shapes.
- [ ] `get_layer_pattern` returns examples and conventions for a layer.
- [ ] At least one senior-agent tool (`suggest_test_plan` / `plan_code_change` / `explain_failure_path`) returns a useful response.

## Known limitations to mention during demo

- Realtime updates handle CREATE/MODIFY/DELETE via incremental re-parse of the changed file. True realtime applies to local-folder imports (edited in place); GitHub/archive imports watch a server-side copy (snapshot).
- Live Docker Compose and Testcontainers checks require Docker availability.
- Production auth and rate-limit hardening should be reviewed before public deployment.
- MCP output depends on completed analysis; use the correct `projectId` and rerun import/analysis if the graph is empty.
- **Deep CPG is opt-in** (`VIBEGRAPH_PARSER_DEEP_CPG`, default `false`); with it off there are no `LocalVariable` nodes or `READS`/`WRITES`/`CATCHES` edges.
- **CPG data-flow is conservative** and intentionally does NOT capture: collection mutation (`list.add(...)`), setter calls (`obj.setX(...)`), `obj.field`/`arr[i]` write targets, cross-method (inter-procedural) data-flow, and bare access to inherited fields (only `this.field` is certain).
- **`STEP_IN_FLOW` metadata is first-flow-wins**: when one call participates in multiple route flows, the single persisted edge keeps the metadata of the first (sorted) entrypoint that reached it.

## Recovery notes

| Issue | Recovery |
| --- | --- |
| Backend health check fails | Check Neo4j credentials and port conflicts, then restart the backend. |
| Frontend cannot reach backend | Confirm `VITE_API_URL` points to `http://localhost:8080`. |
| MCP client cannot connect | Confirm backend is running and client transport is `streamable-http`. |
| Project has no graph data | Re-import or analyze the project, then verify the project status. |
| Class query returns the wrong class | Use the fully qualified class name. |
| Docker is unavailable | State the Docker dependency clearly and run local services manually if possible. |
