<#
  VibeGraph - one-command dev startup (demo-safe).

  Brings the whole local stack up in the right order and verifies each step:
    1. Postgres + Neo4j (docker compose) -> wait until pg_isready passes and the
       Neo4j HTTP port answers. D-M1: the backend requires Postgres (Flyway +
       ddl-auto: validate), so starting Neo4j alone leaves it unable to boot.
    2. Backend (mvnw spring-boot:run, detached window) -> wait for /api/projects = 200.
    3. Frontend (npm run dev, detached window)          -> wait for :5173 = 200.

  Use this before a demo so nothing starts out of order or silently dies.
  Stop everything with scripts/dev-down.ps1.

  Usage:  powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
#>
param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [int]$BackendTimeoutSec = 210,
    [int]$FrontendTimeoutSec = 90
)

$ErrorActionPreference = "Stop"

function Http200($url, $timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $code = curl.exe -s -o NUL -w "%{http_code}" --max-time 5 $url 2>$null
        if ($code -eq "200") { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "==> [1/3] Postgres + Neo4j (docker compose up -d postgres neo4j)" -ForegroundColor Cyan
Push-Location $Root
try {
    docker compose up -d postgres neo4j | Out-Null
} finally {
    Pop-Location
}
# D-M1: backend needs Postgres BEFORE it starts (Flyway + ddl-auto: validate).
Write-Host "    waiting for Postgres (pg_isready)..."
$pgDeadline = (Get-Date).AddSeconds(60)
$pgReady = $false
while ((Get-Date) -lt $pgDeadline) {
    docker compose exec -T postgres pg_isready -q 2>$null
    if ($LASTEXITCODE -eq 0) { $pgReady = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $pgReady) {
    Write-Host "    Postgres did not become ready in 60s. Check: docker logs vibegraph-postgres" -ForegroundColor Red
    exit 1
}
Write-Host "    Postgres is up." -ForegroundColor Green
Write-Host "    waiting for Neo4j HTTP (:7474)..."
if (-not (Http200 "http://localhost:7474" 60)) {
    Write-Host "    Neo4j did not become ready in 60s. Check: docker logs vibegraph-neo4j" -ForegroundColor Red
    exit 1
}
Write-Host "    Neo4j is up." -ForegroundColor Green

Write-Host "==> [2/3] Backend (mvnw spring-boot:run)" -ForegroundColor Cyan
$backendUp = Http200 "http://localhost:8080/api/projects" 3
if ($backendUp) {
    Write-Host "    backend already running." -ForegroundColor Green
} else {
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "set SPRING_DEVTOOLS_RESTART_ENABLED=false && .\mvnw.cmd spring-boot:run" `
        -WorkingDirectory $Root | Out-Null
    Write-Host "    launched; waiting for /api/projects = 200 (compile ~2min first run)..."
    if (-not (Http200 "http://localhost:8080/api/projects" $BackendTimeoutSec)) {
        Write-Host "    backend did not answer in ${BackendTimeoutSec}s. Check its window / logs." -ForegroundColor Red
        exit 1
    }
    Write-Host "    backend is up." -ForegroundColor Green
}

Write-Host "==> [3/3] Frontend (npm run dev)" -ForegroundColor Cyan
$frontendUp = Http200 "http://localhost:5173" 2
if ($frontendUp) {
    Write-Host "    frontend already running." -ForegroundColor Green
} else {
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "npm run dev" `
        -WorkingDirectory (Join-Path $Root "vibegraph-web") | Out-Null
    Write-Host "    launched; waiting for :5173..."
    if (-not (Http200 "http://localhost:5173" $FrontendTimeoutSec)) {
        Write-Host "    frontend did not answer in ${FrontendTimeoutSec}s. Check its window." -ForegroundColor Yellow
    } else {
        Write-Host "    frontend is up." -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "READY:" -ForegroundColor Green
Write-Host "  Frontend : http://localhost:5173"
Write-Host "  Backend  : http://localhost:8080/api/projects"
Write-Host "  Neo4j    : http://localhost:7474"
Write-Host "Stop all:  powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1"
