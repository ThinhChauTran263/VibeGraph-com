# VibeGraph AI Memory

Durable, human-curated facts an AI agent should know before editing this repo. Read this via
the `get_project_conventions` MCP tool. Keep entries factual; never record secrets or paths
that contain credentials.

## Architecture Decisions

- Backend is Spring Boot (Java 21); frontend is Vue 3 + Vite (`vibegraph-web/`); graph store is Neo4j.
- Persistence goes through the `GraphRepository` interface. Only `graph.repository.impl.neo4j` may import Neo4j APIs (enforced by ArchUnit in `StorageAbstractionTest`).
- Services (`com.vibegraph..service..`) must depend on the `GraphRepository` interface, never on `graph.repository.impl`.
- MCP tools are Spring AI tools registered in `common/config/McpServerConfig` and exposed over streamable HTTP at `/mcp`.
- Graph node identity is `{projectId, fullName}`; the stable id scheme is shared by every node, including the `Project` node (`fullName = projectId`).

## Coding Conventions

- Keep MCP responses bounded: cap line windows, bytes, and result counts, and set explicit truncation metadata.
- Return project-relative paths only. Never leak absolute server paths or secrets in any MCP response.
- Validate every MCP input: non-blank, length-capped, reject control characters (allow newlines only for free-form text fields like change requests and stack traces).
- Resolve ambiguous symbol queries to candidate lists; never silently guess one match.
- Reuse `GraphView` / `SourceGraphSupport` / `SourceFileService` for graph and source access in MCP analyzers.

## Current Limitations

- STEP_IN_FLOW is inferred from the resolved CALLS graph and de-duplicated by relation key. It is NOT a literal copy of CALLS and may merge metadata. Do not present it as exact runtime tracing.
- Deep CPG (LocalVariable nodes + READS/WRITES/CATCHES edges) is opt-in via `VIBEGRAPH_PARSER_DEEP_CPG=true`. With it off, method-level data-flow groups are legitimately empty (report this as a limitation, not a bug).
- Realtime file watching wires the DELETE `.java` path end to end; CREATE/MODIFY currently only emit events/logs (incremental re-parse is not yet wired).
- The in-memory project registry is recovered from the persisted `Project` node after a restart, but only when the recorded source root still exists on disk and lives under the allowed workspace/root.

## Testing Commands

- Focused backend tests (Windows): `.\mvnw.cmd -q "-Dtest=SomeTest" test`
- Focused backend tests (unix): `./mvnw -q -Dtest=SomeTest test`
- Full backend unit suite: `.\mvnw.cmd -q -DskipITs test` (Windows) / `./mvnw -q -DskipITs test` (unix)
- Integration tests use Testcontainers Neo4j and require Docker (`*IT` classes via `verify`).
- Frontend: `npm --prefix vibegraph-web run test:unit`, plus `type-check`, `lint`, `build-only`.

## Known Traps

- Node `filePath` is an absolute server path; always relativize against the project source root before returning it.
- The MCP source tools refuse `.env`, keys, archives, build output, binaries, and any path that escapes the project source root.
- `InvalidPathException` extends `IllegalArgumentException`; do not list both in a multi-catch.
- Adapting MCP `McpServerConfig` changes the bean method signature; keep `McpToolsTest` tool-name assertions in sync.
- Cypher relationship/label tokens cannot be parameterized — validate them against the schema/enum allow-list before interpolation.

## Realtime Status

- DELETE `.java`: removes the file's nodes/edges from the graph in realtime.
- CREATE/MODIFY `.java`: detected and logged; incremental re-parse pending.

## MCP Tool Limitations

- `trace_endpoint` prefers STEP_IN_FLOW and falls back to CALLS (flagged lower confidence) when no flow exists.
- `find_related_tests` / `plan_code_change` use naming heuristics and literal source search; matches can include false positives — confirm before acting.
- `explain_failure_path` only maps in-project stack frames; external-only traces are reported without invented mappings.
- `get_method_cpg_context` returns empty data-flow groups when deep CPG is disabled, with an explicit limitation note.
