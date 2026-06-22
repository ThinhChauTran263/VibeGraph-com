# Deployment

VibeGraph is deployed as three Docker containers — Neo4j, the Spring Boot backend,
and the Nginx-served Vue frontend — orchestrated by `docker-compose.yml`.

The full, authoritative run/deploy procedure (prerequisites, environment setup,
build, health checks, logs, rebuilds, and troubleshooting) lives in
**[DEVOPS-GUIDE.md](DEVOPS-GUIDE.md)**. This file is a short index so the steps are
not duplicated and cannot drift.

## Quick reference

```bash
cp .env.example .env          # set NEO4J_USERNAME / NEO4J_PASSWORD at minimum
docker compose up -d --build
```

| Service | URL | Notes |
| --- | --- | --- |
| Frontend | http://localhost:3000 | Vue app (Nginx, static build) |
| Backend API | http://localhost:8080 | Spring Boot REST API |
| Backend health | http://localhost:8080/actuator/health | Expect `{"status":"UP"}` |
| Neo4j Browser | http://localhost:7474 | Use `.env` credentials |
| Neo4j Bolt | bolt://localhost:7687 | Backend DB connection |

## Notes

- Vite values (`VITE_API_URL`, `VITE_WS_URL`) are baked in at **build time** — rebuild
  the frontend image after changing them (`docker compose build frontend`).
- Persisted Neo4j data and the upload workspace live in named volumes. `docker compose
  down` keeps them; `docker compose down -v` deletes them.

See [DEVOPS-GUIDE.md](DEVOPS-GUIDE.md) for everything else, and the longer-term plan in
[VibeGraph-specs-2month/deployment-plan.md](VibeGraph-specs-2month/deployment-plan.md).
