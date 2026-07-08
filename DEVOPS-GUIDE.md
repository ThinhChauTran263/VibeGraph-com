# VibeGraph Docker Run Guide

This guide covers the current Docker Compose path for running VibeGraph locally with Neo4j, the Spring Boot backend, and the Vue frontend.

## Prerequisites

- Docker Engine 24 or newer
- Docker Compose v2.20 or newer
- Git

Optional for local checks outside Docker:

- Java 21
- Node.js 22

Verify Docker is available:

```bash
docker --version
docker compose version
```

## Services and ports

| Service | Compose service | Host URL / port | Purpose |
| --- | --- | --- | --- |
| Frontend | `frontend` | `http://localhost:3000` | Vue app served by Nginx |
| Backend API | `backend` | `http://localhost:8080` | Spring Boot REST API |
| Neo4j Browser | `neo4j` | `http://localhost:7474` | Neo4j web UI |
| Neo4j Bolt | `neo4j` | `localhost:7687` | Backend database connection |

Inside Docker, the backend connects to Neo4j through `bolt://neo4j:7687` using the `docker` Spring profile.

## Environment setup

Copy the example environment file before starting Compose:

```bash
cp .env.example .env
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Set at least the Neo4j credentials in `.env`:

| Variable | Required | Meaning |
| --- | --- | --- |
| `NEO4J_USERNAME` | Yes | Neo4j username used by Compose and the backend. Default example value is `neo4j`. |
| `NEO4J_PASSWORD` | Yes | Neo4j password used by Compose and the backend. Change the example value before shared or production use. |
| `VIBEGRAPH_UPLOAD_WORKSPACE` | No | Backend workspace path for uploaded/imported projects. Compose sets this to `/uploads` in the container. |
| `VITE_API_URL` | No | Frontend API URL for local non-Docker Vite usage. The current Docker frontend is a static Nginx build, so do not rely on Compose runtime env to rewrite built assets. |
| `VITE_WS_URL` | No | Frontend WebSocket URL for local non-Docker Vite usage. Same build-time caveat as `VITE_API_URL`. |
| `FRONTEND_URL` | No | CORS origin used by backend profiles when supplied. |

Do not commit `.env` or real secrets.

## Run the stack

Build images and start all services in the foreground:

```bash
docker compose up --build
```

Build images and start all services in the background:

```bash
docker compose up -d --build
```

Start again later without forcing a rebuild:

```bash
docker compose up -d
```

Stop containers while keeping Neo4j data volumes:

```bash
docker compose down
```

Stop containers and remove persisted Neo4j/upload volumes:

```bash
docker compose down -v
```

Use `docker compose down -v` only when you intentionally want to reset local data.

## Health checks and smoke checks

List container status:

```bash
docker compose ps
```

Check backend health:

```bash
curl http://localhost:8080/actuator/health
```

Expected backend health response includes:

```json
{"status":"UP"}
```

Open the running services:

- Frontend: `http://localhost:3000`
- Backend API base: `http://localhost:8080`
- Neo4j Browser: `http://localhost:7474`

For Neo4j Browser, use the credentials from `.env`.

## Logs

Follow all logs:

```bash
docker compose logs -f
```

Follow one service:

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f neo4j
```

Show recent logs without following:

```bash
docker compose logs --tail=100 backend
```

## Rebuild common cases

Rebuild only the backend image after backend dependency or source changes:

```bash
docker compose build backend
docker compose up -d backend
```

Rebuild only the frontend image after frontend dependency or source changes:

```bash
docker compose build frontend
docker compose up -d frontend
```

Recreate the full stack from a clean local image build:

```bash
docker compose down
docker compose up -d --build
```

## Troubleshooting

### Port conflict

Symptoms:

- Compose fails with `port is already allocated`.
- One of `3000`, `8080`, `7474`, or `7687` is already in use.

Fix:

1. Stop the process using the port, or stop the older Compose stack.
2. Run `docker compose ps` to confirm no stale VibeGraph containers are still running.
3. If needed, change the host-side port in `docker-compose.yml` for local-only usage.

### Neo4j auth or password mismatch

Symptoms:

- Backend cannot authenticate to Neo4j.
- Neo4j Browser rejects the expected password.

Fix:

1. Confirm `.env` has matching `NEO4J_USERNAME` and `NEO4J_PASSWORD` values.
2. If Neo4j was already initialized with an old password, recreate the volume:

   ```bash
   docker compose down -v
   docker compose up -d --build
   ```

This deletes local Neo4j data.

### Neo4j is not healthy yet

Symptoms:

- `docker compose ps` shows `neo4j` as starting or unhealthy.
- Backend waits for Neo4j or fails early during startup.

Fix:

1. Wait 30-60 seconds on first startup.
2. Inspect Neo4j logs:

   ```bash
   docker compose logs -f neo4j
   ```

3. Ensure Docker has enough memory for Neo4j and the backend.

### Frontend cannot reach backend

Symptoms:

- Frontend loads, but API calls fail in browser DevTools.
- Requests go to the wrong backend URL.

Fix:

1. Confirm the backend is reachable at `http://localhost:8080/actuator/health`.
2. Rebuild the frontend image after changing any Vite API/WebSocket values, because Vite values are embedded at build time:

   ```bash
   docker compose build frontend
   docker compose up -d frontend
   ```

3. Check frontend logs:

   ```bash
   docker compose logs -f frontend
   ```

### Large archive upload fails

Symptoms:

- Archive import fails for large ZIP/TAR/TAR.GZ files.
- Backend returns a payload-size or validation error.

Fix:

1. Keep uploads within the configured limit. The example configuration uses `VIBEGRAPH_IMPORT_ARCHIVE_MAX_SIZE=100MB` and `VIBEGRAPH_IMPORT_ARCHIVE_MAX_REQUEST_SIZE=105MB`.
2. If changing limits for local testing, update `.env` and recreate the backend container:

   ```bash
   docker compose up -d --force-recreate backend
   ```

3. Confirm the archive type is supported: `.zip`, `.tar`, or `.tar.gz`.

### Reset all local Docker state

Use this when local volumes or credentials are stale and data loss is acceptable:

```bash
docker compose down -v
docker compose up -d --build
```

This removes Neo4j data, Neo4j logs, and upload workspace volumes.

## CI note

Backend and frontend GitHub Actions workflows already exist:

- `.github/workflows/backend.yml` runs backend unit tests with Java 21 and Maven cache.
- `.github/workflows/frontend.yml` runs frontend install, type-check, unit tests, lint, build, and high-severity audit with Node 22.

The Docker run guide does not require changing those CI workflows.
