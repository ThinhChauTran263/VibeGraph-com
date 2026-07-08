# MCP Integration Guide

VibeGraph exposes a Spring AI MCP server so AI coding assistants can ask for architecture, source, CPG, impact, and test context from an analyzed Java project before generating code.

## Run VibeGraph with MCP enabled

### Docker Compose

1. Copy `.env.example` to `.env` and set `NEO4J_USERNAME` and `NEO4J_PASSWORD`.
2. Start the stack:

```bash
docker compose up --build
```

3. Verify the backend is healthy:

```bash
curl http://localhost:8080/actuator/health
```

Default URLs:

| Surface | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| Backend API | `http://localhost:8080` |
| MCP server | `http://localhost:8080/mcp` |
| Neo4j Browser | `http://localhost:7474` |

### Local development

Run Neo4j, then start the Spring Boot backend on the default `8080` port. The dev profile reads Neo4j settings from `NEO4J_URI`, `NEO4J_USERNAME`, and `NEO4J_PASSWORD`.

## MCP client configuration

Use streamable HTTP transport:

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

The production profile configures the Spring AI MCP server as:

| Setting | Value |
| --- | --- |
| Server name | `VibeGraph` |
| Version | `1.0.0` |
| Protocol | `STREAMABLE` over Spring MVC at `/mcp` |

## How MCP reads a project

MCP tools work against projects that have already been imported and analyzed by VibeGraph.

1. Import a Java project from GitHub, archive upload, or an allowed local workspace.
2. VibeGraph parses the source and persists the graph in Neo4j.
3. The `Project` node stores the project id, name, and source root path.
4. MCP tools read the Neo4j graph first, then read bounded source snippets from the stored source root when a tool needs code text.
5. After a backend restart, `/api/projects` and MCP source tools recover persisted projects from Neo4j as long as the recorded source root still exists and passes the allowed workspace/root guard.

MCP does not scan arbitrary folders. It only reads source files that belong to an imported/analyzed project and pass the source-root safety checks. File paths returned by MCP are project-relative; absolute server paths and usernames must not be exposed.

## Available tools

All tools require a `projectId` from an imported and analyzed project.

| Tool | Purpose |
| --- | --- |
| `get_project_architecture` | Summarize layers, counts, patterns, naming conventions, and warnings. |
| `get_class_context` | Return a matched class, methods, fields, and incoming/outgoing relations. |
| `get_impact_analysis` | Return dependency/structural/type-data-flow blast radius by depth. |
| `get_layer_pattern` | Explain conventions and dependency patterns for a layer. |
| `trace_endpoint` | Trace route-handler flow using `STEP_IN_FLOW`, with `CALLS` fallback. |
| `find_references` | Find graph references to a symbol through structural, type, and dependency edges. |
| `get_source_file` | Read a bounded, redacted project-relative source file. |
| `search_source` | Search source text and map matches back to graph symbols when possible. |
| `get_method_source` | Resolve and return method source by id, class/method query, or signature. |
| `get_method_cpg_context` | Return method signature, calls, flow steps, and deep CPG groups when available. |
| `find_related_tests` | Suggest related tests using graph links, naming heuristics, and source search. |
| `suggest_test_plan` | Build a focused test plan for a described change. |
| `plan_code_change` | Produce a conservative code-change plan with candidate files, risks, and tests. |
| `explain_failure_path` | Map stacktrace frames to in-project graph/source context. |
| `get_project_conventions` | Return durable repo conventions from `VibeGraph-specs-2month/ai-memory.md`. |

## Recommended AI workflow

1. Import and analyze the target project in VibeGraph.
2. Call `get_project_conventions` and `get_project_architecture` before planning code changes.
3. Use `get_layer_pattern` for the target layer.
4. Use `get_class_context`, `get_method_source`, or `get_method_cpg_context` for the target symbol.
5. Use `get_impact_analysis` and `find_references` before renaming, moving, or changing shared code.
6. Use `find_related_tests` and `suggest_test_plan` before editing or committing.
7. Use `plan_code_change` for ambiguous changes; it should return candidates/questions instead of guessing.
8. Use `explain_failure_path` when a failing test or runtime stacktrace is available.

## Project recovery after restart

The in-memory project registry is not the source of truth. On restart:

- `ProjectServiceImpl.listProjects()` merges in-memory projects with persisted `Project` nodes from Neo4j.
- `ProjectServiceImpl.getProject()` can recover a project by id from persisted metadata.
- `ProjectServiceImpl.deleteProject()` can delete a recovered persisted project and remove its graph nodes/relationships.
- Recovery is allowed only when the persisted source root is still under the archive workspace root or configured allowed root.
- Tampered or out-of-workspace persisted roots are skipped/rejected.

## CPG and flow behavior

- CPG-lite edges such as `TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `HAS_FIELD`, `INJECTS`, `INSTANTIATES`, `THROWS`, and `ANNOTATED_BY` are emitted by default when present.
- Deep CPG (`LocalVariable`, `READS`, `WRITES`, `CATCHES`) is opt-in through `VIBEGRAPH_PARSER_DEEP_CPG=true`.
- `STEP_IN_FLOW` is inferred from resolved in-project `CALLS` reachable from route handlers. It is a deduplicated flow view, not exact runtime tracing.
- Java `java.lang` types such as `String`, `Long`, and `Object` are qualified as `java.lang.*` instead of being mis-qualified into the current package.

## Known limitations

- Realtime updates handle CREATE/MODIFY/DELETE via incremental re-parse of the changed file (broadcast as `INCREMENTAL`). True realtime applies to local-folder imports; GitHub/archive imports watch a server-side copy (snapshot).
- Live Docker Compose and Testcontainers verification depends on Docker Desktop or another Docker daemon being available.
- Production auth and rate-limit hardening remain deployment concerns unless explicitly enabled by the environment.
- MCP responses are only useful after the target project has been imported and analyzed; empty graphs produce empty or warning-heavy results.
- Deep CPG data-flow groups are legitimately empty when `VIBEGRAPH_PARSER_DEEP_CPG` is false.
- `find_related_tests`, `suggest_test_plan`, and `plan_code_change` use evidence plus heuristics; agents should verify before editing.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| MCP server is not reachable | Confirm backend is running on port `8080`, then check `http://localhost:8080/actuator/health`. |
| MCP client cannot connect | Verify the client uses `http://localhost:8080/mcp` and `streamable-http`. |
| Tools return empty data | Confirm the project import completed and the project status is analyzed. |
| A recovered project is missing after restart | Confirm Neo4j contains a `Project` node and its stored source root still exists under the allowed workspace/root. |
| Wrong or missing class context | Use the class full name instead of a simple name when multiple classes share a name. |
| Method CPG is empty | Confirm deep CPG is enabled if you expect `READS`/`WRITES`/`CATCHES`; method calls and flow can still appear without it. |
| Impact analysis seems too small | Try another profile (`dependency`, `structural`, `type-data-flow`) and increase depth to `3` or `5`. |
| Docker run fails | See `DEVOPS-GUIDE.md` for Neo4j credentials, port conflicts, and Compose troubleshooting. |
