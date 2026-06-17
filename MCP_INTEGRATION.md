# MCP Integration Guide

VibeGraph exposes a Spring AI MCP server so AI coding assistants can ask for architecture context from an analyzed Java project before generating code.

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

## Available tools

All tools require a `projectId` from an imported and analyzed project.

### `get_project_architecture`

Returns architecture layers, summary counts, detected patterns, naming conventions, and warnings.

Input:

| Parameter | Type | Description |
| --- | --- | --- |
| `projectId` | string | Project identifier to inspect |

High-level response shape:

```json
{
  "projectId": "...",
  "summaryCounts": { "...": 0 },
  "layers": [{ "name": "SERVICE", "count": 0 }],
  "patterns": { "...": "..." },
  "namingConventions": { "...": "..." },
  "warnings": []
}
```

Example agent prompt:

> Use `get_project_architecture` for project `<projectId>` and summarize the controller, service, repository, config, and route layers before suggesting code changes.

### `get_class_context`

Returns a class match, its methods and fields, incoming and outgoing graph relations, and warnings.

Input:

| Parameter | Type | Description |
| --- | --- | --- |
| `projectId` | string | Project identifier to inspect |
| `classQuery` | string | Class id, full name, or simple name |

High-level response shape:

```json
{
  "projectId": "...",
  "query": "UserService",
  "classInfo": {
    "id": "...",
    "type": "CLASS",
    "name": "UserService",
    "fullName": "com.example.UserService",
    "layer": "SERVICE",
    "lineNumber": 42
  },
  "methods": [],
  "fields": [],
  "incomingRelations": [],
  "outgoingRelations": [],
  "warnings": []
}
```

Example agent prompt:

> Use `get_class_context` for `UserService` in project `<projectId>`, then explain its direct callers, callees, and public methods.

### `get_impact_analysis`

Returns blast radius for a graph node: direct impact, transitive impact, risk level, notes, and warnings.

Input:

| Parameter | Type | Description |
| --- | --- | --- |
| `projectId` | string | Project identifier to inspect |
| `nodeQuery` | string | Target node full name or identifier |
| `depth` | integer | Impact traversal depth; allowed values are `1`, `2`, `3`, `5` |

High-level response shape:

```json
{
  "projectId": "...",
  "nodeQuery": "com.example.UserService",
  "depth": 3,
  "summary": {
    "targetId": "...",
    "targetType": "CLASS",
    "targetName": "UserService",
    "targetFullName": "com.example.UserService",
    "directDependents": 2,
    "totalDependents": 5
  },
  "directImpact": [],
  "transitiveImpact": [],
  "riskLevel": "LOW",
  "notes": [],
  "warnings": []
}
```

Example agent prompt:

> Before changing `com.example.UserService`, use `get_impact_analysis` with depth `3` and list the direct dependents that must be tested.

### `get_layer_pattern`

Returns examples, dependency patterns, naming conventions, rules, notes, and warnings for an architecture layer.

Input:

| Parameter | Type | Description |
| --- | --- | --- |
| `projectId` | string | Project identifier to inspect |
| `layer` | string | Layer name such as `CONTROLLER`, `SERVICE`, `REPOSITORY`, `CONFIG`, or `ROUTE` |

High-level response shape:

```json
{
  "projectId": "...",
  "requestedLayer": "SERVICE",
  "normalizedLayer": "SERVICE",
  "description": "...",
  "examples": [],
  "commonDependencies": [{ "relationType": "CALLS", "targetLayer": "REPOSITORY", "count": 3 }],
  "namingConventions": { "...": "..." },
  "doRules": [],
  "dontRules": [],
  "patternNotes": [],
  "warnings": []
}
```

Example agent prompt:

> Use `get_layer_pattern` for the `CONTROLLER` layer in project `<projectId>` and follow the existing naming and dependency conventions in the new endpoint.

## Suggested AI workflow

1. Import and analyze a project in VibeGraph.
2. Ask the AI assistant to call `get_project_architecture` before code generation.
3. Use `get_layer_pattern` for the target layer.
4. Use `get_class_context` for the class being edited or extended.
5. Use `get_impact_analysis` before renaming, moving, or changing shared classes.
6. Test the direct dependents reported by impact analysis.

## Known limitations

- Realtime DELETE handling is verified, but CREATE/MODIFY incremental re-parse is still pending and must not be presented as complete.
- Live Docker Compose and Testcontainers verification depends on Docker Desktop or another Docker daemon being available.
- Production auth and rate-limit hardening remain deployment concerns unless explicitly enabled by the environment.
- MCP responses are only useful after the target project has been imported and analyzed; empty graphs produce empty or warning-heavy results.
- **CPG relations vs MCP DTOs:** the graph now carries CPG-lite and deep relations
  (`TYPE_OF`, `PARAMETER_TYPE`, `RETURNS`, `HAS_FIELD`, `INJECTS`, `INSTANTIATES`,
  `THROWS`, `ANNOTATED_BY`, `READS`, `WRITES`, `CATCHES`, `STEP_IN_FLOW`). The MCP
  tools accept and traverse these without error, but their response DTOs are not yet
  extended to surface per-relation CPG detail (e.g. `STEP_IN_FLOW` step ordering or
  `READS`/`WRITES` targets). This is a known scope limitation, not a regression —
  the existing tool contracts never promised these fields. `get_class_context`
  incoming/outgoing relations and `get_impact_analysis` traversal include the new
  edge types generically; `get_layer_pattern` `commonDependencies` is computed from a
  fixed dependency edge subset (`CALLS`, `IMPORTS`, `EXTENDS`, `IMPLEMENTS`,
  `INJECTS`, `HANDLES_ROUTE`) and ignores deep CPG edges by design.
- **Deep CPG (`READS`/`WRITES`/`CATCHES` + `LocalVariable`) is opt-in** via
  `VIBEGRAPH_PARSER_DEEP_CPG` (default `false`); with it off those relations are absent
  from the graph and therefore from MCP results.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| MCP server is not reachable | Confirm backend is running on port `8080`, then check `http://localhost:8080/actuator/health`. |
| MCP client cannot connect | Verify the client uses `http://localhost:8080/mcp` and `streamable-http`. |
| Tools return empty data | Confirm the project import completed and the project status is analyzed. |
| Wrong or missing class context | Use the class full name instead of a simple name when multiple classes share a name. |
| Impact analysis seems too small | Increase `depth` to `3` or `5` and verify the target node exists in the analyzed graph. |
| Docker run fails | See `DEVOPS-GUIDE.md` for Neo4j credentials, port conflicts, and Compose troubleshooting. |
