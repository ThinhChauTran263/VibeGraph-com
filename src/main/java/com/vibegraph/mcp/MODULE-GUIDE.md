# Module: mcp

## Purpose

The MCP module exposes VibeGraph's analyzed Java project context to AI coding
assistants over Spring AI MCP streamable HTTP at `/mcp`.

MCP tools read two controlled data sources:

1. Neo4j graph data produced by import/analyze.
2. Bounded source snippets from the project source root stored on the persisted
   `Project` node.

Tools do not scan arbitrary folders. Source access is restricted to imported and
analyzed projects whose stored source root still exists and passes the allowed
workspace/root guard.

## Current Tool Surface

The server currently registers 18 tools through `McpServerConfig`:

| Tool | Class | Purpose |
| --- | --- | --- |
| `get_project_architecture` | `ArchitectureTool` | Layers, counts, conventions, warnings. |
| `get_class_context` | `ClassContextTool` | Class match, methods, fields, incoming/outgoing relations. |
| `get_impact_analysis` | `ImpactAnalysisTool` | Blast radius by profile/depth. |
| `get_layer_pattern` | `LayerPatternTool` | Layer examples, conventions, dependency patterns. |
| `trace_endpoint` | `TraceEndpointTool` | Route handler downstream flow. |
| `find_references` | `FindReferencesTool` | Graph references to a symbol. |
| `get_source_file` | `SourceFileTool` | Bounded, redacted source file content. |
| `search_source` | `SearchSourceTool` | Source search mapped to graph symbols when possible. |
| `get_method_source` | `MethodSourceTool` | Method source by id/query/signature. |
| `get_method_cpg_context` | `MethodCpgTool` | Method signature, calls, flow, and deep CPG groups. |
| `find_related_tests` | `FindRelatedTestsTool` | Related tests from graph links and heuristics. |
| `suggest_test_plan` | `SuggestTestPlanTool` | Focused test plan for a described change. |
| `plan_code_change` | `PlanCodeChangeTool` | Conservative change plan with risks and tests. |
| `explain_failure_path` | `ExplainFailureTool` | Stacktrace-to-project mapping. |
| `get_project_conventions` | `ProjectConventionsTool` | Durable repo conventions from `ai-memory.md`. |
| `list_projects` | `ListProjectsTool` | Analyzed projects owned by the caller (id, name, analyzedAt, stats). |
| `verify_change` | `VerifyChangeTool` | Changed files → symbols, reachable API routes, related tests, commands. |
| `explain_compile_error` | `ExplainCompileErrorTool` | javac/Maven output → enclosing symbols, caller counts, fix hints. |

## Package Structure

```text
mcp/
  dto/response/        Tool response DTOs
  service/             Tool service interfaces
  service/impl/        Graph/source analyzers and senior-tool implementations
  source/              Shared source and graph access helpers
  tool/                Spring AI `@Tool` classes exposed to clients
```

## Core Helpers

| Helper | Responsibility |
| --- | --- |
| `GraphView` | Loads and resolves graph nodes/relations for a project. |
| `SourceFileService` | Reads bounded source text from the validated project root. |
| `SourceGraphSupport` | Shared source/graph helpers, path relativization, redaction. |

## Project Recovery After Restart

Project metadata is persisted in Neo4j. After backend restart:

- `ProjectServiceImpl.listProjects()` merges in-memory projects with persisted
  `Project` nodes from Neo4j.
- `ProjectServiceImpl.getProject()` can recover a project by id from persisted
  metadata.
- `ProjectServiceImpl.deleteProject()` can delete a recovered project and remove
  all persisted nodes/relationships for that `projectId`.
- Recovery is allowed only when the recorded root is under the archive workspace
  root or configured allowed root.

This lets MCP source tools keep working across restarts without trusting arbitrary
persisted paths.

## CPG Behavior

- CPG-lite relation types are available in the graph generically.
- Deep CPG (`LocalVariable`, `READS`, `WRITES`, `CATCHES`) is ON by default;
  set `VIBEGRAPH_PARSER_DEEP_CPG=false` to opt out on very large repositories.
  Projects analyzed before the default flipped need a re-analyze to gain the
  deep edges.
- `STEP_IN_FLOW` is inferred from resolved in-project `CALLS` reachable from
  route handlers. It is a deterministic flow view, not runtime tracing.
- If deep CPG was disabled at analyze time, method CPG tools should report empty
  data-flow groups as a limitation, not as a failure.

## Safety Rules

1. Return project-relative paths only. Do not leak absolute server paths or usernames.
2. Reject path traversal and refuse sensitive files (`.env`, keys, archives,
   binaries, build output).
3. Keep responses bounded with explicit truncation metadata.
4. Resolve ambiguity to candidate lists instead of guessing.
5. Validate all graph labels/relationship types before interpolating Cypher tokens.
6. Keep MCP tools stateless; source of truth is Neo4j + the validated source root.

## Testing

Focused backend commands:

```powershell
.\mvnw.cmd -q "-Dtest=SeniorMcpToolsTest,McpToolsTest,ProjectConventionsServiceTest,ProjectRestartSourceTest" test
.\mvnw.cmd -q "-Dtest=ProjectServicePersistenceTest" test
```

Full backend unit suite:

```powershell
.\mvnw.cmd -q -DskipITs test
```

## Known Limitations

- Realtime incremental re-parse is wired for CREATE/MODIFY/DELETE `.java`
  (FileChangeBroadcaster broadcasts an `INCREMENTAL` delta); true realtime
  applies to local-folder imports.
- Production auth/rate limiting are deployment concerns.
- Test and change-plan tools use graph evidence plus heuristics; agents should
  verify before editing.
