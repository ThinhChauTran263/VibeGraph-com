# VibeGraph — DevOps Guide

> **Vai trò:** Hướng dẫn build, test, deploy VibeGraph qua Docker Compose; setup CI/CD với GitHub Actions; troubleshoot runtime issues.

> **Dev phụ trách:** Dev 5 (Integration / DevOps).

> **Sprint:** Sprint 1 (basic Docker), Sprint 3 (production config + CI).

> **Audience:** Dev mới onboard, DevOps engineer, người demo / deploy production.

---

## Mục lục

1. [Yêu cầu môi trường](#1-yêu-cầu-môi-trường)
2. [Quickstart — chạy local trong 5 phút](#2-quickstart)
3. [Cấu trúc DevOps files](#3-cấu-trúc-devops-files)
4. [Backend Dockerfile](#4-backend-dockerfile)
5. [Frontend Dockerfile + Nginx](#5-frontend-dockerfile--nginx)
6. [docker-compose.yml](#6-docker-composeyml)
7. [Environment variables](#7-environment-variables)
8. [GitHub Actions CI](#8-github-actions-ci)
9. [Production deployment](#9-production-deployment)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Yêu cầu môi trường

| Tool | Min version | Khuyến nghị |
|------|-------------|-------------|
| Docker Engine | 24.x | 27.x |
| Docker Compose | v2.20 | v2.30 |
| Java (chỉ khi dev local không docker) | 21 | 21.0.x LTS |
| Maven | 3.9 | 3.9.x |
| Node.js (chỉ khi dev frontend) | 20 | 22 LTS |
| Git | 2.40 | 2.45+ |

**RAM/Disk yêu cầu:**
- VPS production: tối thiểu **4GB RAM, 2 CPU, 20GB SSD** (xem CONTEXT-PROMPT.md)
- Dev local: 8GB RAM khuyến nghị (Neo4j chiếm ~1.5GB, IDE + browser chiếm phần còn lại)

**Đạt được khi:**
- [ ] `docker --version` ≥ 24
- [ ] `docker compose version` ≥ v2.20
- [ ] Network OK: pull được `neo4j:5-community`, `eclipse-temurin:21-jre-alpine`

---

## 2. Quickstart

```bash
# 1. Clone repo
git clone <vibegraph-repo>
cd VibeGraph

# 2. Copy env template
cp .env.example .env
# Chỉnh .env nếu muốn (default đủ chạy)

# 3. Build + start toàn stack
docker compose up -d --build

# 4. Đợi healthcheck
docker compose ps
# neo4j   → healthy (mất ~30s)
# backend → healthy
# frontend → up

# 5. Kiểm tra
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# 6. Mở browser
# Frontend:    http://localhost:3000
# Backend API: http://localhost:8080
# Neo4j UI:    http://localhost:7474 (user: neo4j / pass: vibegraph)

# 7. Stop
docker compose down

# 8. Reset hoàn toàn (xóa Neo4j data)
docker compose down -v
```

**Đạt được khi:**
- [ ] `http://localhost:3000` load Vue frontend
- [ ] `http://localhost:8080/actuator/health` trả `UP`
- [ ] Register 1 project qua UI → analyze thành công → graph hiển thị
- [ ] Tổng thời gian setup < 5 phút (NFR đầu tiên của README — task 5.7)

**Tham chiếu:** `task-breakdown.md` 5.7 (`README.md + quickstart guide`)

---

## 3. Cấu trúc DevOps files

```
VibeGraph/
├── Dockerfile                    # Backend Spring Boot (multi-stage)
├── docker-compose.yml            # Full stack: Neo4j + Backend + Frontend
├── .env.example                  # Template env vars
├── .env                          # Local secrets (gitignored)
├── .dockerignore                 # Loại trừ target/, node_modules/, .git/
├── vibegraph-web/
│   ├── Dockerfile                # Frontend Vue + Nginx
│   └── nginx.conf                # Nginx config (SPA routing + proxy /api)
└── .github/
    └── workflows/
        ├── ci.yml                # PR check: build + test
        ├── docker-publish.yml    # Build + push images on tag
        └── lint.yml              # ESLint, Prettier, Checkstyle
```

**Đạt được khi:**
- [ ] Mỗi file có comment header giải thích mục đích
- [ ] `.dockerignore` exclude tất cả thư mục build artifact (target/, dist/, node_modules/, .gradle/)
- [ ] `.env.example` không chứa secret thật, chỉ placeholder

---

## 4. Backend Dockerfile

**Hiện trạng:** `Dockerfile` ở root (multi-stage Maven + Temurin JRE 21).

**Spec yêu cầu cải thiện:**

### Layer caching — `dependency:go-offline` đã có ✓
```dockerfile
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
```

### Cải thiện cần làm trong Sprint 3:
- [ ] **Health check trong image:** thêm `HEALTHCHECK` instruction gọi `/actuator/health`
- [ ] **Non-root user:** chạy app dưới user `appuser` (security best practice)
- [ ] **JVM tuning cho container:** thêm `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`
- [ ] **Spring Boot layered jar:** dùng `BP_LAYERS_OFF=false` để tách app code / dependencies → faster rebuild
- [ ] **Image size target:** < 250MB (hiện tại Alpine + JRE-only, kiểm tra `docker images`)

**Dockerfile mục tiêu:**
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Extract layers
RUN java -Djarmode=tools -jar target/*.jar extract --destination /build/app

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder --chown=app:app /build/app/ ./
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q -O- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "vibegraph.jar"]
```

**Đạt được khi:**
- [ ] `docker build -t vibegraph-backend .` < 3 phút (lần đầu), < 30s (lần 2 nếu chỉ đổi src)
- [ ] Final image < 250MB
- [ ] Container chạy non-root
- [ ] `docker inspect` hiển thị `Health: healthy` sau ~40s

**Tham chiếu:** `requirements.md` NFR-04, `task-breakdown.md` 5.4, 5.14

---

## 5. Frontend Dockerfile + Nginx

**Hiện trạng:** `vibegraph-web/Dockerfile` + `nginx.conf` đã có (Vite build → Nginx serve).

**Spec yêu cầu:**

### Multi-stage build:
```dockerfile
# Build
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build

# Runtime
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK CMD wget -q -O- http://localhost/health || exit 1
```

### Nginx config requirements (`vibegraph-web/nginx.conf`):

- **SPA routing:** fallback `try_files $uri $uri/ /index.html` (Vue Router history mode)
- **API proxy:** route `/api/*` và `/ws/*` tới backend container
- **Gzip** cho JS/CSS/HTML
- **Cache headers:** static assets `Cache-Control: public, max-age=31536000, immutable`; HTML `no-cache`
- **Security headers:** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`
- **Health endpoint:** `location /health { return 200 "ok"; }`

**Đạt được khi:**
- [ ] Vue Router refresh không 404 (SPA fallback hoạt động)
- [ ] Frontend gọi `/api/projects` được proxy tới backend
- [ ] WebSocket `/ws/graph-updates` connect được qua proxy (cần `proxy_http_version 1.1` + `Upgrade` headers)
- [ ] Lighthouse performance score > 90 (NFR cho Web — `web/performance.md`)

**Tham chiếu:** `web/security.md`, `web/performance.md`, `task-breakdown.md` 5.4

---

## 6. docker-compose.yml

**Hiện trạng:** đã có `docker-compose.yml` với 3 services. Spec dưới đây là target cho Sprint 3 production.

### Services

#### `neo4j`
- Image: `neo4j:5-community`
- Plugin: `apoc` (đã có)
- Healthcheck: HTTP 7474 (đã có)
- Volume: `neo4j-data` (persist DB), `neo4j-logs` (debug)
- Memory limit (production): `mem_limit: 2g`, `NEO4J_server_memory_heap_max__size: 1G`
- **Cần thêm:** `restart: unless-stopped`

#### `backend`
- Build context root, `Dockerfile`
- Depends on `neo4j` healthy ✓
- Volume mount **read-only**: `./projects:/projects:ro` — đây là nơi user clone repo target để VibeGraph analyze
- **Cần thêm:**
  - `restart: unless-stopped`
  - `mem_limit: 1.5g`
  - Mount steering output write: `./projects:/projects:rw` (để write `.kiro/`, `.cursor/rules/`, `CLAUDE.md`) — **đổi `:ro` thành `:rw`** vì steering module cần ghi
  - Healthcheck: `wget /actuator/health`

#### `frontend`
- Build từ `./vibegraph-web/Dockerfile`
- Build args: `VITE_API_URL`, `VITE_WS_URL` (build-time, không phải runtime — Vite inline lúc build)
- **Lưu ý:** environment vars trong compose file hiện tại **không có hiệu lực** vì Vite cần build-time. Phải pass qua `args:` block

**docker-compose.yml mục tiêu:**
```yaml
services:
  frontend:
    build:
      context: ./vibegraph-web
      args:
        VITE_API_URL: ${VITE_API_URL:-/api}
        VITE_WS_URL: ${VITE_WS_URL:-/ws}
    # bỏ block environment cho frontend, không có effect
```

### Networks
- Default network OK cho Phase 1
- Production: tách `frontend-net` (public) + `backend-net` (internal) để Neo4j không expose port ra host

### Volumes
- `neo4j-data` — persist
- `neo4j-logs` — persist
- **Cần thêm cho Sprint 3:** `vibegraph-cache` cho parser symbol solver cache

**Đạt được khi:**
- [ ] `docker compose up -d` start full stack < 60s
- [ ] `docker compose down` không mất data Neo4j (volume persist)
- [ ] `docker compose down -v` xóa sạch
- [ ] `docker compose ps` hiển thị tất cả `healthy`
- [ ] Backend write được vào `./projects/` (steering files xuất hiện sau analyze)

**Tham chiếu:** `architecture.md` §7, `task-breakdown.md` 5.4, 5.14

---

## 7. Environment variables

### `.env.example` (commit vào repo)
```bash
# Neo4j
NEO4J_USER=neo4j
NEO4J_PASSWORD=vibegraph

# Backend
SPRING_PROFILES_ACTIVE=docker
SERVER_PORT=8080
LOG_LEVEL=INFO

# Frontend (build-time)
VITE_API_URL=/api
VITE_WS_URL=/ws

# Watcher
VIBEGRAPH_WATCHER_ENABLED=true
VIBEGRAPH_WATCHER_DEBOUNCE_MS=500

# MCP
VIBEGRAPH_MCP_ENABLED=true

# Project mount
PROJECTS_DIR=./projects
```

### `.env` (gitignored, local override)
- Copy từ `.env.example`, sửa password mạnh hơn cho production
- KHÔNG commit
- Production: dùng secret manager (Docker secrets, K8s secrets, AWS Secrets Manager)

**Đạt được khi:**
- [ ] `.env.example` có comment giải thích mỗi biến
- [ ] `.gitignore` có `.env` (verified)
- [ ] Production password ≠ dev password (security checklist — `common/security.md`)

**Tham chiếu:** `common/security.md`, `requirements.md` NFR-04

---

## 8. GitHub Actions CI

### Backend verify + Testcontainers note

Backend `mvn verify` now depends on Docker being available because `Neo4jGraphRepositoryIT` uses Testcontainers to run a real Neo4j container. Current expected state when Docker daemon is available:

- `Neo4jGraphRepositoryIT`: 5 tests run, 0 skipped.
- JaCoCo coverage gate 70% passes.
- `mvn verify` is green without relying on an externally managed Neo4j instance.

CI runners must expose a working Docker daemon. If Docker is unavailable, Testcontainers integration tests may skip and coverage can drop below the gate; that is not a valid merge signal for backend verify.

### Sprint 2 archive import verification note

Current archive import verification status:

- Backend verify passes when Docker daemon is available for Testcontainers.
- Sync browser E2E passes: `POST /api/projects/import-archive` returns `200 OK`, project reaches `ANALYZED` with progress `100`, and the frontend navigates to `/projects/{id}/graph`.
- Async browser E2E passes through fallback path: `POST /api/projects/import-archive?async=true` returns `202 Accepted`, SockJS handshake `/ws/graph-updates/info` returns `200`, polling fallback `GET /api/projects/{id}` observes `ANALYZED`, and the frontend navigates to graph.
- The async E2E currently verifies WebSocket availability plus poll fallback. It does not yet prove the push event path wins over polling because analysis completes too quickly.
- Dev CORS still needs either `http://127.0.0.1:5173` in allowed origins or a Vite proxy standard path.

### `.github/workflows/ci.yml`
**Mục tiêu:** Run on every PR + push to `main`.

**Jobs:**

#### `backend-test`
```yaml
runs-on: ubuntu-latest
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      java-version: 21
      distribution: temurin
      cache: maven
  - run: docker info
  - run: mvn -B verify
  - uses: codecov/codecov-action@v4
```

Testcontainers starts Neo4j itself, so the workflow does not need a separate `services.neo4j` block unless a future test explicitly targets an external database.

#### `frontend-test`
```yaml
runs-on: ubuntu-latest
defaults:
  run:
    working-directory: vibegraph-web
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-node@v4
    with:
      node-version: 22
      cache: npm
      cache-dependency-path: vibegraph-web/package-lock.json
  - run: npm ci
  - run: npm run lint
  - run: npm run type-check
  - run: npm run test:unit
  - run: npm run build
```

#### `docker-build` (chỉ chạy trên `main`)
- Build cả 2 image, không push
- Verify Dockerfile vẫn build được

**Acceptance criteria:**
- [ ] PR vào `main` chạy đủ 3 jobs
- [ ] Coverage report tự upload Codecov
- [ ] Job fail → block merge
- [ ] Cache hit Maven + npm > 80% (job lặp < 3 phút)

### `.github/workflows/lint.yml`
- Checkstyle / Spotless cho Java
- ESLint + Prettier cho Vue/TS
- Markdown lint cho `*.md`
- Run on every PR

### `.github/workflows/docker-publish.yml`
**Mục tiêu:** Build + push images khi tag version.

**Trigger:** `on: push: tags: ['v*']`

**Steps:**
- Login GHCR (`ghcr.io/<org>/vibegraph-backend`, `vibegraph-frontend`)
- Build multi-arch (`linux/amd64`, `linux/arm64`) qua `docker buildx`
- Push với 3 tags: `latest`, `<version>`, `<sha>`
- Generate SBOM (Software Bill of Materials) qua `anchore/sbom-action`
- Sign image với cosign (optional Phase 2)

**Đạt được khi:**
- [ ] Tag `v1.0.0` → image push lên GHCR
- [ ] `docker pull ghcr.io/<org>/vibegraph-backend:1.0.0` work
- [ ] Multi-arch (test trên Mac M-series + Linux x64)

**Tham chiếu:** `task-breakdown.md` 5.5, `common/git-workflow.md`

---

## 9. Production deployment

### Single-host VPS (Phase 1 default)
**Yêu cầu:** 4GB RAM, 2 CPU, 20GB SSD, Docker installed.

**Setup:**
```bash
# 1. Pull image hoặc copy repo
git clone <repo> /opt/vibegraph
cd /opt/vibegraph

# 2. Production .env
cp .env.example .env.prod
# Sửa password mạnh, set NEO4J_PASSWORD, etc.

# 3. Run với compose override
docker compose --env-file .env.prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d

# 4. Setup systemd để auto-restart on reboot
sudo cp deploy/vibegraph.service /etc/systemd/system/
sudo systemctl enable vibegraph
sudo systemctl start vibegraph
```

### `docker-compose.prod.yml` (Sprint 3 task 5.14)
**Override cho production:**
- Tách network internal/external
- Resource limits chặt hơn
- Bind mount logs ra host (`./logs:/app/logs`)
- Reverse proxy Nginx (host) terminate HTTPS, proxy tới `frontend:80` và `backend:8080`
- KHÔNG expose port Neo4j 7687/7474 ra host

### HTTPS (production yêu cầu)
- Caddy hoặc Traefik ở edge (auto Let's Encrypt)
- Hoặc Nginx + certbot
- HSTS header bật (`web/security.md`)

### Backup
- Neo4j volume backup hằng ngày: `docker exec neo4j neo4j-admin database dump neo4j --to-path=/backups`
- Steering files trong project users tự backup (Git)

**Đạt được khi:**
- [ ] Production deploy script chạy 1 lần là up
- [ ] Reboot VPS → tự khởi động lại stack
- [ ] HTTPS hoạt động (A+ SSL Labs)
- [ ] Neo4j không expose ra Internet

**Tham chiếu:** `task-breakdown.md` 5.14, `common/security.md`, `web/security.md`

---

## 10. Troubleshooting

### Neo4j healthcheck timeout
**Triệu chứng:** `docker compose ps` báo `neo4j: starting` mãi.
**Nguyên nhân:** Neo4j 5 cần ~30-60s khởi động lần đầu (load APOC plugin).
**Giải quyết:** đợi thêm; nếu > 2 phút → `docker compose logs neo4j` xem stack trace; kiểm tra RAM ≥ 2GB.

### Backend connection refused tới Neo4j
**Triệu chứng:** Spring Boot log `Connection refused: bolt://neo4j:7687`.
**Nguyên nhân:** Neo4j chưa healthy nhưng backend start lên.
**Giải quyết:** đảm bảo `depends_on: neo4j: condition: service_healthy` (đã có); add Spring retry với exponential backoff (`common/config/Neo4jMigrationRunner`).

### Frontend không gọi được API
**Triệu chứng:** browser DevTools báo CORS hoặc 404.
**Nguyên nhân:** Vite build với `VITE_API_URL` sai, hoặc Nginx không proxy `/api/*`.
**Giải quyết:** verify `nginx.conf` có block `location /api { proxy_pass http://backend:8080; }`; rebuild frontend với `--build-arg VITE_API_URL=/api`.

### File watcher không detect change trên macOS
**Triệu chứng:** save file `.java` nhưng graph không update.
**Nguyên nhân:** WatchService trên macOS có lag (JDK quirk).
**Giải quyết:** polling fallback đã có sẵn (`watcher/MODULE-GUIDE.md`); verify `vibegraph.watcher.poll-fallback-interval=5s`.

### MCP endpoint trả 404
**Triệu chứng:** Cursor/Kiro báo "Cannot connect to MCP server".
**Nguyên nhân:** Spring AI MCP starter chưa enable, hoặc transport sai.
**Giải quyết:** check `application.yaml` có `spring.ai.mcp.server.enabled=true`; client config phải `transport: streamable-http`.

### Out of memory khi parse project lớn
**Triệu chứng:** `OutOfMemoryError: Java heap space`.
**Giải quyết:** tăng heap qua `JAVA_OPTS=-Xmx2g`; hoặc `MaxRAMPercentage=80` trong Dockerfile; kiểm tra parser có dùng `parallelStream` đúng (không cumulate hết AST trong memory).

### Inotify limit reached (Linux)
**Triệu chứng:** `WatchService` báo "user instances limit reached".
**Giải quyết:**
```bash
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

---

## Definition of Done cho DevOps

- [ ] `docker compose up -d` từ clean state lên full stack < 60s
- [ ] Quickstart 5 phút verified bởi dev mới (Sprint 1 task 5.7)
- [ ] CI green trên PR (build + test + lint)
- [ ] Production deploy script tested trên VPS thật (Sprint 3 task 5.14)
- [ ] Image GHCR multi-arch build OK
- [ ] HTTPS production setup hoạt động
- [ ] Backup script Neo4j daily verified
- [ ] Tất cả env var có trong `.env.example`, không hardcode trong source
- [ ] Troubleshooting guide cover ≥ 5 issue thường gặp

---

## Lưu ý cross-cutting

- `.env` KHÔNG bao giờ commit (`common/security.md`)
- `docker compose down -v` xóa data — luôn confirm với user trước khi chạy
- Neo4j password default `vibegraph` chỉ cho dev — production phải override
- Backend mount `/projects` cần quyền **read + write** (steering writer)
- CI cache size có giới hạn (10GB / repo trên GitHub) — clean cache cũ định kỳ
- Image GHCR public hay private là quyết định của team — Phase 1 mặc định private cho tới khi mở source
