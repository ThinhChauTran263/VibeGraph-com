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

Use either import path:

- Archive import: upload a Java project archive.
- GitHub import: enter a public GitHub repository URL.

Expected result:

- The project appears in the project list.
- The project progresses through analysis and ends in an analyzed state.

Talking point:

> The import flow stores source files, parses Java code, and builds graph nodes and relationships for classes, methods, fields, and dependencies.

### 3. View graph and diagram

Open the analyzed project.

Show:

- Graph view with nodes and relationships.
- Search and filters for narrowing the graph.
- Node detail panel for class or method metadata.
- Diagram panel if diagrams are available for the selected view.

Talking point:

> The graph is useful for human exploration, while the same indexed context also powers MCP tools for AI assistants.

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

Confirm the client can list these tools:

- `get_project_architecture`
- `get_class_context`
- `get_impact_analysis`
- `get_layer_pattern`

Talking point:

> MCP lets an AI assistant ask VibeGraph for repository-specific context instead of guessing architecture from a prompt alone.

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

- [ ] A Java project is imported successfully.
- [ ] The project reaches analyzed status.
- [ ] Graph or diagram exploration works for the imported project.
- [ ] MCP client connects to `http://localhost:8080/mcp` using streamable HTTP.
- [ ] `get_project_architecture` returns layer and pattern context.
- [ ] `get_class_context` returns class details and relations.
- [ ] `get_impact_analysis` returns direct/transitive impact and risk level.
- [ ] `get_layer_pattern` returns examples and conventions for a layer.

## Known limitations to mention during demo

- Realtime DELETE handling is verified, but CREATE/MODIFY incremental re-parse is still pending.
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
