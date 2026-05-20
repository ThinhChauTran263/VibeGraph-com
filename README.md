# VibeGraph

Realtime Java code analyzer with knowledge graph visualization and AI integration via MCP.

## Features

- Parse Java source code with JavaParser
- Build knowledge graph in Neo4j
- Force-directed graph visualization (Sigma.js)
- Auto-generate UML diagrams (Use Case, Class, Sequence) with Mermaid
- Realtime updates via File Watcher + WebSocket
- MCP Server for AI tools (Cursor, Kiro, Claude Code)
- Auto-generate steering files for AI tools

## Tech Stack

- Backend: Spring Boot 3.x / Java 21
- Parser: JavaParser 3.28
- Database: Neo4j 5.x
- Frontend: Vue 3 + Vite + Sigma.js
- MCP: Spring AI MCP Boot Starter
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

See `VibeGraph-specs/` for detailed documentation:
- `requirements.md` — Functional requirements
- `architecture.md` — System design
- `task-breakdown.md` — Sprint tasks
- `presentation.md` — Customer-facing overview
- `file-checklist.md` — File creation checklist

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
