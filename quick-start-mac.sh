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

step "Checking repository files"
[[ -f .env ]] || fail "Missing .env in repository root. Put the internal .env file next to docker-compose.yml, then run again."
[[ -f docker-compose.yml ]] || fail "docker-compose.yml not found. Run this script from the VibeGraph repository root."
ok ".env and docker-compose.yml found"

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
wait_for_http "http://localhost:3000" "Frontend" 40

cat <<DONE

VibeGraph is ready.
Frontend: http://localhost:3000
Backend:  http://localhost:8080
Neo4j:    http://localhost:7474

Useful commands:
  docker compose logs -f backend
  docker compose logs -f frontend
  docker compose down
  ./quick-start-mac.sh --reset   # delete local Docker volumes and start fresh
DONE
