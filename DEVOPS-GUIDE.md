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

## Running behind a reverse proxy

Two variables decide how the backend derives a client IP. Every per-IP control depends on that
value: the per-IP rate limit (`VIBEGRAPH_REQUESTS_PER_MINUTE_PER_IP`, default 120/min), the admin
IP-block list, and the guard that stops a wrong API key from burning BCrypt rounds before the
limiter sees it.

| Variable | Default | Meaning |
| --- | --- | --- |
| `VIBEGRAPH_TRUST_PROXY` | `false` | When `false` the backend uses the socket peer address and ignores `X-Forwarded-For` entirely. Safe default — leave it off unless a reverse proxy actually fronts the backend. |
| `VIBEGRAPH_TRUSTED_PROXIES` | empty | Exact IPs and/or CIDRs of the proxies allowed to supply `X-Forwarded-For`, e.g. `127.0.0.1,172.18.0.0/16`. Only consulted when `VIBEGRAPH_TRUST_PROXY=true`. |

`X-Forwarded-For` is read only when **both** hold: the flag is `true`, and the socket peer is itself
in `VIBEGRAPH_TRUSTED_PROXIES`. A direct caller that reaches the backend port without going through
a listed proxy can never influence the resolved address, whatever headers it sends. Keep the list as
narrow as the real topology allows — widening it to a whole bridge network (`172.18.0.0/16`) means
any container on that network can supply the header.

### Requirement before enabling trust-proxy

`VIBEGRAPH_TRUST_PROXY=true` is only safe if the proxy in front **appends** the connecting client's
address to `X-Forwarded-For`. The backend walks the header right-to-left and takes the right-most
entry that is not a trusted proxy. When the proxy appends, the address it observed sits to the right
of anything the client sent, so client-supplied entries are ignored.

If the proxy passes `X-Forwarded-For` through unchanged — or does not set it at all — then a
single-entry header sent by the client becomes the right-most untrusted entry, and the client picks
its own IP. Consequences, all silent:

- Per-IP rate limiting stops working: a new value per request means a fresh bucket per request.
- Admin IP blocks stop working for anyone who sends the header.
- The wrong-API-key BCrypt cost is paid on every request again, because the limiter never reaches
  its threshold.

Nginx appends correctly with `$proxy_add_x_forwarded_for`:

```nginx
location / {
    proxy_pass http://backend:8080;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Host $host;
}
```

Do **not** use `proxy_set_header X-Forwarded-For $http_x_forwarded_for` — that forwards the client's
header verbatim and is exactly the misconfiguration described above.

### Verify it before trusting it

The resolved address is not written to the backend log, so `docker compose logs` will not show it.
It is recorded in request telemetry and surfaced to admins, so check it there.

**Attribution check.** Send a forged header from outside the proxy, then read back what the backend
recorded (requires an admin session):

```bash
curl -s -o /dev/null -H 'X-Forwarded-For: 1.2.3.4' https://your-host/api/projects
curl -s -b admin-cookies.txt 'https://your-host/api/admin/security/request-events?limit=5'
```

The newest entry's `ipAddress` must be the real client address, never `1.2.3.4`. Use
`/request-events` and not `/events` — the latter returns security events, which carry no client
address.

**Functional check** — faster, and needs no admin session. Lower
`VIBEGRAPH_REQUESTS_PER_MINUTE_PER_IP` temporarily, then send more requests than the limit while
rotating `X-Forwarded-For` on each one:

```bash
for i in $(seq 1 40); do
  curl -s -o /dev/null -w '%{http_code} ' -H "X-Forwarded-For: 203.0.113.$i" https://your-host/api/projects
done
```

A correctly configured deployment still reaches `429`. If every request returns the same non-429
status, the header is being trusted verbatim — set `VIBEGRAPH_TRUST_PROXY=false` until the proxy is
fixed, then restore the original limit.

Use a public range such as `203.0.113.0/24` (reserved for documentation) rather than `10.x` or
`192.168.x`. Private and loopback addresses are skipped while resolving, so a private forged value
falls back to the peer address and the test would pass even on a misconfigured deployment.

Note that enforcement is per instance: N replicas allow up to N times the configured rate. Per-user
and per-API-key limits are unaffected by this setting, since they key on the authenticated identity
rather than the address.

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

 1. The account's remaining storage quota is the effective business limit. The example configuration keeps a separate `VIBEGRAPH_IMPORT_ARCHIVE_HARD_MAX_SIZE=2048MB` and `VIBEGRAPH_IMPORT_ARCHIVE_HARD_MAX_REQUEST_SIZE=2050MB` safety ceiling for the host.
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
