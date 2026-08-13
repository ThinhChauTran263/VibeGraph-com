#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

RESET=0
NO_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --reset) RESET=1 ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)
      cat <<HELP
VibeGraph Quick Start (macOS/Linux)

Usage:
  ./quick-start-mac.sh [--reset] [--no-build]

Options:
  --reset     Stop the stack and remove Docker volumes before starting.
  --no-build  Start existing images without rebuilding.
HELP
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

step() { printf '\n==> %s\n' "$1"; }
ok() { printf '[OK] %s\n' "$1"; }
fail() { printf '[ERROR] %s\n' "$1" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

ensure_env_file() {
  local env_file=".env"
  local example_file=".env.example"

  if [[ ! -f "$env_file" ]]; then
    if [[ -f "$example_file" ]]; then
      cp "$example_file" "$env_file"
      ok "Created .env from .env.example"
    else
      : > "$env_file"
      ok "Created .env with local defaults"
    fi
  fi

  local defaults=(
    "SPRING_PROFILES_ACTIVE=dev"
    "SERVER_PORT=8080"
    "POSTGRES_HOST=localhost"
    "POSTGRES_PORT=5432"
    "POSTGRES_DB=vibegraph"
    "POSTGRES_USER=vibegraph"
    "POSTGRES_PASSWORD=vibegraph"
    "JWT_SECRET=local-smoke-test-secret-32-characters-min"
    "JWT_EXPIRATION_MS=86400000"
    "NEO4J_URI=bolt://localhost:7687"
    "NEO4J_USERNAME=neo4j"
    "NEO4J_PASSWORD=vibegraph"
    "NEO4J_DATABASE=neo4j"
    "NEO4J_MAX_CONNECTION_POOL_SIZE=100"
    "NEO4J_CONNECTION_ACQUISITION_TIMEOUT=60s"
    "NEO4J_MAX_CONNECTION_LIFETIME=1h"
    "VIBEGRAPH_REALTIME_DEMO_PERMIT=false"
    "VIBEGRAPH_AUTH_BOOTSTRAP_ENABLED=false"
    "VIBEGRAPH_PROJECTS_ALLOWED_ROOT="
    "VIBEGRAPH_UPLOAD_WORKSPACE=.vibegraph/uploads"
    "VIBEGRAPH_IMPORT_ARCHIVE_MAX_SIZE=100MB"
    "VIBEGRAPH_IMPORT_ARCHIVE_MAX_REQUEST_SIZE=105MB"
    "VIBEGRAPH_PARSER_CACHE_DIR=.vibegraph/parse-cache"
    "VIBEGRAPH_PARSER_USE_CACHE=true"
    "VIBEGRAPH_PARSER_DEEP_CPG=false"
    "VIBEGRAPH_WATCHER_ENABLED=true"
    "VIBEGRAPH_WATCHER_DEBOUNCE_MS=500"
    "FRONTEND_URL=http://localhost:3000"
    "VITE_API_URL=http://localhost:8080"
    "VITE_WS_URL=http://localhost:8080/ws/graph-updates"
    "GEMINI_API_KEY="
    "GEMINI_API_KEYS="
    "SPRING_AI_MODEL_CHAT=none"
    "VIBEGRAPH_USECASE_LLM_ENABLED=false"
  )

  local missing=()
  local entry key
  for entry in "${defaults[@]}"; do
    key="${entry%%=*}"
    if ! grep -Eq "^[[:space:]]*${key}[[:space:]]*=" "$env_file"; then
      missing+=("$entry")
    fi
  done

  if [[ "${#missing[@]}" -gt 0 ]]; then
    {
      printf '\n# Added by quick-start-mac.sh for the current Docker stack\n'
      printf '%s\n' "${missing[@]}"
    } >> "$env_file"
    ok "Added missing .env keys for Postgres/JWT/current Docker stack"
  fi
}

env_value() {
  local key="$1"
  grep -E "^[[:space:]]*${key}[[:space:]]*=" .env | tail -n 1 | sed -E "s/^[[:space:]]*${key}[[:space:]]*=//; s/^['\"]//; s/['\"]$//"
}

assert_env_ready() {
  local required=(
    SERVER_PORT
    POSTGRES_DB
    POSTGRES_USER
    POSTGRES_PASSWORD
    JWT_SECRET
    NEO4J_USERNAME
    NEO4J_PASSWORD
    NEO4J_DATABASE
    NEO4J_MAX_CONNECTION_POOL_SIZE
    NEO4J_CONNECTION_ACQUISITION_TIMEOUT
    NEO4J_MAX_CONNECTION_LIFETIME
    VIBEGRAPH_IMPORT_ARCHIVE_MAX_SIZE
    VIBEGRAPH_IMPORT_ARCHIVE_MAX_REQUEST_SIZE
    VIBEGRAPH_PARSER_CACHE_DIR
    VIBEGRAPH_WATCHER_ENABLED
    VIBEGRAPH_WATCHER_DEBOUNCE_MS
    FRONTEND_URL
    VITE_API_URL
    VITE_WS_URL
  )

  local missing=()
  local key value
  for key in "${required[@]}"; do
    value="$(env_value "$key" || true)"
    if [[ -z "$value" ]]; then
      missing+=("$key")
    fi
  done

  if [[ "${#missing[@]}" -gt 0 ]]; then
    fail "Missing required .env values: ${missing[*]}. Fill them in and run again."
  fi
  if [[ "$(env_value JWT_SECRET | wc -c | tr -d ' ')" -lt 33 ]]; then
    fail "JWT_SECRET in .env must be at least 32 characters."
  fi
  ok ".env contains the Postgres, Neo4j, JWT, backend, and frontend keys required by docker-compose.yml"
}

wait_for_docker() {
  step "Waiting for Docker Desktop"
  for _ in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      ok "Docker is running"
      return 0
    fi
    sleep 3
  done
  fail "Docker is installed but not running. Open Docker Desktop, wait until it is ready, then run this script again."
}

wait_for_http() {
  local url="$1"
  local name="$2"
  local attempts="${3:-60}"
  step "Waiting for ${name} (${url})"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      ok "${name} is reachable"
      return 0
    fi
    sleep 3
  done
  fail "${name} did not become reachable at ${url}. Run: docker compose logs --tail=200"
}

ensure_test_accounts() {
  step "Ensuring local test accounts"
  local db_user db_name
  db_user="$(env_value POSTGRES_USER)"
  db_name="$(env_value POSTGRES_DB)"

  docker compose exec -T postgres psql -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 <<'SQL'
WITH free_plan AS (
    SELECT id FROM plans WHERE code = 'FREE'
),
admin_upsert AS (
    INSERT INTO users (email, password_hash, display_name, email_verified, role)
    SELECT 'admin@vibegraph.com', '$2a$10$HQ8AF989FyysS/rgHQ7twOwuBRKDioEHpDp9hBjkJ2WVbjbOkksd.', 'VibeGraph Admin', true, 'ADMIN'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower('admin@vibegraph.com'))
    RETURNING id
),
admin_existing AS (
    UPDATE users
    SET password_hash = '$2a$10$HQ8AF989FyysS/rgHQ7twOwuBRKDioEHpDp9hBjkJ2WVbjbOkksd.',
        display_name = COALESCE(NULLIF(display_name, ''), 'VibeGraph Admin'),
        email_verified = true,
        role = 'ADMIN'
    WHERE lower(email) = lower('admin@vibegraph.com')
    RETURNING id
),
user_upsert AS (
    INSERT INTO users (email, password_hash, display_name, email_verified, role)
    SELECT 'user@vibegraph.com', '$2a$10$LOUKr3Pu3.YOQvtZ0Evaee5u1WKiYN0ziLKnS/Mh7B5Xgf6f.gWGa', 'VibeGraph User', true, 'USER'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower('user@vibegraph.com'))
    RETURNING id
),
user_existing AS (
    UPDATE users
    SET password_hash = '$2a$10$LOUKr3Pu3.YOQvtZ0Evaee5u1WKiYN0ziLKnS/Mh7B5Xgf6f.gWGa',
        display_name = COALESCE(NULLIF(display_name, ''), 'VibeGraph User'),
        email_verified = true,
        role = 'USER'
    WHERE lower(email) = lower('user@vibegraph.com')
    RETURNING id
),
all_test_users AS (
    SELECT id FROM admin_upsert
    UNION
    SELECT id FROM admin_existing
    UNION
    SELECT id FROM user_upsert
    UNION
    SELECT id FROM user_existing
)
INSERT INTO user_account_settings (user_id, plan_id)
SELECT all_test_users.id, free_plan.id
FROM all_test_users
CROSS JOIN free_plan
ON CONFLICT (user_id) DO NOTHING;
SQL
  ok "Local test accounts are ready: admin@vibegraph.com/admin123 and user@vibegraph.com/user123"
}

step "Checking repository files"
[[ -f docker-compose.yml ]] || fail "docker-compose.yml not found. Run this script from the VibeGraph repository root."
ensure_env_file
assert_env_ready
if [[ ! -d projects ]]; then
  mkdir -p projects
  ok "Created projects/ for the writable local patch mount"
fi
ok ".env, projects/, and docker-compose.yml are ready"

step "Checking Docker"
if ! have docker; then
  if [[ "$(uname -s)" == "Darwin" ]] && have brew; then
    step "Docker is missing. Installing Docker Desktop with Homebrew"
    brew install --cask docker
  else
    fail "Docker is missing. Install Docker Desktop manually: https://www.docker.com/products/docker-desktop/"
  fi
fi

if [[ "$(uname -s)" == "Darwin" ]]; then
  open -a Docker >/dev/null 2>&1 || true
fi
wait_for_docker

step "Checking Docker Compose"
docker compose version >/dev/null || fail "Docker Compose v2 is required. Update Docker Desktop and run again."
docker compose version

step "Validating docker-compose.yml and .env"
docker compose config --quiet || fail "docker compose config failed. Check .env values and docker-compose.yml."
ok "Docker Compose config is valid"
ok "Backend startup will run Flyway migrations V1..V4, including plans and credit tables"

if [[ "$RESET" == "1" ]]; then
  step "Reset requested: stopping stack and removing volumes"
  docker compose down -v --remove-orphans
else
  step "Stopping stale containers, keeping data volumes"
  docker compose down --remove-orphans
fi

step "Starting VibeGraph stack"
if [[ "$NO_BUILD" == "1" ]]; then
  docker compose up -d
else
  docker compose up -d --build
fi

step "Current containers"
docker compose ps

wait_for_http "http://localhost:7474" "Neo4j Browser" 40
wait_for_http "http://localhost:8080/actuator/health" "Backend health" 80
ensure_test_accounts
wait_for_http "http://localhost:3000" "Frontend" 40

cat <<DONE

VibeGraph is ready.
Frontend: http://localhost:3000
Backend:  http://localhost:8080
Postgres: localhost:5432
Neo4j:    http://localhost:7474

Test accounts:
  Admin: admin@vibegraph.com / admin123
  User:  user@vibegraph.com / user123

Useful commands:
  docker compose logs -f backend
  docker compose logs -f frontend
  docker compose logs -f postgres
  docker compose down
  ./quick-start-mac.sh --reset   # delete volumes and recreate DB from Flyway V1..V4
DONE
