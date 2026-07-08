# Run / demo scripts

Two ways to run VibeGraph. Pick based on the situation.

## Option 1 - Dev workflow (fast iteration)

Neo4j in Docker; backend and frontend run natively for hot reload.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
# ... work ...
powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1        # keeps Neo4j warm
powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1 -StopNeo4j
```

`dev-up.ps1` starts things in the correct order and blocks until each is healthy:
Neo4j (:7474) -> backend (`/api/projects` = 200) -> frontend (:5173). It fast-paths
anything already running, so it is safe to re-run.

## Option 2 - Full Docker (demo / defense - most reliable)

Whole stack in Docker with health-gated ordering and `restart: unless-stopped`, so a
container that dies is brought back automatically (this is what prevents the "Neo4j
stopped overnight -> backend crashes on startup" failure during a demo).

```powershell
docker compose up -d --build
# Frontend: http://localhost:3000   Backend: http://localhost:8080   Neo4j: http://localhost:7474
docker compose down                 # stop (add -v to also drop the graph volume)
```

First `--build` is slow (compiles backend + builds frontend); subsequent starts are fast.

## Why restart policies matter

`docker-compose.yml` sets `restart: unless-stopped` on neo4j, backend, and frontend.
Combined with the existing `depends_on: condition: service_healthy` ordering, the stack
self-heals: if any container exits, Docker restarts it, and dependents only start once
their dependency reports healthy. For a live demo, Option 2 is the safe choice.

## Troubleshooting

- Backend logs `Unable to connect to localhost:7687` -> Neo4j is down. In dev mode run
  `docker compose up -d neo4j` (or just re-run `dev-up.ps1`).
- Backend first run compiles ~230 files (~2 min). `dev-up.ps1` waits up to 210s.
