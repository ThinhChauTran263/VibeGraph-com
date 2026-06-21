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
- UML diagrams: **Use Case** (SVG UML 2.5 chuẩn) & **Class** (Mermaid), API Map
- MCP Server tools cho AI tools (Cursor, Kiro, Claude Code)
- REST API: register project, run analysis, fetch full graph

**Đang phát triển:**
- Impact Analysis: blast radius khả dụng qua `GET /api/projects/{id}/graph/impact`; controller/endpoint chuyên biệt và neighborhood query vẫn là scaffold (xem `graph/MODULE-GUIDE.md`)

**Hoãn sau MVP (post-MVP):**
- Sequence diagram
- Steering-file generation for AI tools
- Multi-language parsing, authentication/billing

## Tech Stack

- Backend: Spring Boot 4.0.6 / Java 21
- Parser: JavaParser 3.28.0 (+ Symbol Solver)
- Database: Neo4j 5.x (raw Java Driver — no Spring Data Neo4j OGM)
- Frontend: Vue 3.5 + Vite 8 + TypeScript 6 + Sigma.js 3 + Mermaid 11
- MCP: Spring AI MCP Server (`spring-ai-starter-mcp-server`)
- Build: Maven
- Container: Docker Compose

## Quick Start

### With Docker (recommended)

```bash
docker compose up -d
```

Then open: http://localhost:3000

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
- `file-checklist.md` — File creation checklist
- `deployment-plan.md` — Docker / domain / SSL / CI notes
- `presentation.html` — Customer-facing overview (generated)

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
