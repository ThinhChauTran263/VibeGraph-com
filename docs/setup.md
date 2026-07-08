# Setup

This page points to the canonical setup instructions so they live in one place.

## Run with Docker (recommended)

See **[../DEVOPS-GUIDE.md](../DEVOPS-GUIDE.md)** — full prerequisites, `.env` setup,
build, health checks, and troubleshooting.

```bash
cp .env.example .env          # set NEO4J_USERNAME / NEO4J_PASSWORD
docker compose up -d --build
```

Frontend: http://localhost:3000 · Backend: http://localhost:8080 · Neo4j: http://localhost:7474

## Local development (without Docker)

Backend (Java 21 + Maven):

```bash
./mvnw spring-boot:run        # needs a reachable Neo4j (bolt://localhost:7687)
```

Frontend (Node 22):

```bash
cd vibegraph-web
npm install
npm run dev                   # Vite dev server on http://localhost:5173
```

## Related

- [../README.md](../README.md) — project overview
- [../MCP_INTEGRATION.md](../MCP_INTEGRATION.md) — connecting an MCP client
- [mcp-integration.md](mcp-integration.md) — MCP integration details
