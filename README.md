# VibeGraph

Realtime Java code analyzer with knowledge graph visualization and AI integration via MCP.

## Features

> Trạng thái phản ánh kế hoạch 8 tuần trong `VibeGraph-specs-2month/`
> (chi tiết: `file-checklist.md`, `requirements-trimmed.md`).

**Đã có (đã ship):**
- Parse Java source code with JavaParser → `NodeData` / `EdgeData`
- Build knowledge graph in Neo4j via `GraphRepository` (raw Neo4j Java Driver)
- Force-directed graph visualization (Sigma.js) with filtering, search, and click-driven node detail/highlight
- Project import qua 3 luồng: **local folder** (`POST /api/projects/import-local`), **archive** `.zip`/`.tar`/`.tar.gz` (`POST /api/projects/import-archive`), và **GitHub public repo** (`POST /api/projects/import-github`), có thanh tiến độ realtime
- Local-folder import với realtime thật: sửa file `.java` trong IDE và graph cập nhật tại chỗ (add/modify/delete) qua server-side File Watcher + WebSocket/STOMP. (GitHub/archive import theo dõi bản copy server-side = snapshot.)
- UML diagram: **Use Case** (SVG UML 2.5 standard)
- **Impact Analysis** với 3 profile (`dependency` / `structural` / `type-data-flow`) qua `GET /api/projects/{id}/graph/impact`
- **Source viewer**: đọc source file redacted, project-relative path qua `SourceController` + FE `CodeViewerModal` (+ MCP source tools)
- **AI-refined Use Case** qua Gemini failover client (`com.vibegraph.ai/*` + `LlmUseCaseRefiner`)
- **Deep CPG opt-in**: `LocalVariable` nodes + `READS`/`WRITES`/`CATCHES` edges qua env `VIBEGRAPH_PARSER_DEEP_CPG=true`
- **15 MCP tools** cho AI tools (Cursor, Kiro, Claude Code) — xem `docs/mcp-integration.md`
- REST API: register project, run analysis, fetch full graph, impact analysis, source viewer

**Đang phát triển:**
- Neighborhood N-hop query (`GET /api/projects/{id}/graph/neighbors/{nodeId}?hops=N`) — `Neo4jGraphRepository.getNeighborhood` hiện ném `UnsupportedOperationException` (Sprint 2/3)
- `ImpactController` chuyên biệt là scaffold rỗng — thực tế impact endpoint đang dùng `GraphController /graph/impact`

**Hoãn sau MVP (post-MVP):**
- Additional diagram types remain out of scope until explicitly reintroduced
- Steering-file generation for AI tools
- Multi-language parsing, authentication/billing (xem `VibeGraph-specs-2month/security-multiuser-roadmap.md`)

## Tech Stack

- Backend: Spring Boot 4.0.6 / Java 21
- Parser: JavaParser 3.28.0 (+ Symbol Solver)
- Database: Neo4j 5.x (raw Java Driver — no Spring Data Neo4j OGM)
- Frontend: Vue 3.5 + Vite 8 + TypeScript 6 + Sigma.js 3
- MCP: Spring AI MCP Server (`spring-ai-starter-mcp-server`)
- Build: Maven
- Container: Docker Compose

## Quick Start

### With Docker (recommended)

```bash
docker compose up -d
```

Then open: http://localhost:3000

### CLI first for local workflows

Install the local CLI before using VibeGraph as a local patch/watch tool:

```bash
npm install -g ./vibegraph-cli
vibegraph config set-url http://localhost:8080
vibegraph register --email you@example.com --password "change-me-123" --name "Your Name"
vibegraph projects list
```

When the backend runs in Docker Compose, pass backend-visible paths to local import. By default,
`./projects` on the host is mounted as `/projects` in the backend container:

```bash
vibegraph projects import-local --path /projects/demo --name demo
```

For the full Local Patch workflow (push, watch, analyze), see **[docs/local-patch.md](docs/local-patch.md)**.

### Local development

#### Backend
```bash
# Start Neo4j
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/vibegraph neo4j:5-community

# Start Spring Boot
mvn spring-boot:run
```

#### Frontend
```bash
cd vibegraph-web
npm install
npm run dev
```

Open: http://localhost:5173

## Project Structure

See `VibeGraph-specs-2month/` for detailed documentation:
- `requirements-trimmed.md` — Functional requirements (MVP scope)
- `architecture.md` — System design
- `file-checklist.md` — Sprint tasks và trạng thái theo file
- `neo4j-schema.md` — Node/edge schema và Cypher migrations
- `security-multiuser-roadmap.md` — Bảo mật hiện trạng + hướng multi-user
- `deployment-plan.md` — Docker / domain / SSL / CI notes
- `presentation.html` — Customer-facing overview (generated)

## Codex Workflow

Xem **[docs/codex/README.md](docs/codex/README.md)** để dùng Codex CLI theo workflow senior: cheat sheet, prompt copy-paste, multi-agent, session/context, debugging và verification riêng cho VibeGraph.

## MCP Configuration

Add to your AI tool's `mcp.json`:

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

## License

TBD
