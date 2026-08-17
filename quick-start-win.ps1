# VibeGraph Quick Start (Windows)
# Run from the repository root:
#   powershell -ExecutionPolicy Bypass -File .\quick-start-win.ps1

param(
    [switch]$Reset,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Write-Step($Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok($Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Fail($Message) {
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

function Test-Command($Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-EnvMap {
    $envPath = Join-Path $Root '.env'
    $map = @{}
    if (!(Test-Path $envPath)) {
        return $map
    }

    foreach ($line in Get-Content $envPath) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#') -or !$trimmed.Contains('=')) {
            continue
        }
        $parts = $trimmed.Split('=', 2)
        $map[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
    return $map
}

function Ensure-EnvFile {
    $envPath = Join-Path $Root '.env'
    $examplePath = Join-Path $Root '.env.example'

    $defaults = [ordered]@{
        'SPRING_PROFILES_ACTIVE' = 'dev'
        'SERVER_PORT' = '8080'
        'POSTGRES_HOST' = 'localhost'
        'POSTGRES_PORT' = '5432'
        'POSTGRES_DB' = 'vibegraph'
        'POSTGRES_USER' = 'vibegraph'
        'POSTGRES_PASSWORD' = 'vibegraph'
        'JWT_SECRET' = 'local-smoke-test-secret-32-characters-min'
        'JWT_EXPIRATION_MS' = '86400000'
        'NEO4J_URI' = 'bolt://localhost:7687'
        'NEO4J_USERNAME' = 'neo4j'
        'NEO4J_PASSWORD' = 'vibegraph'
        'NEO4J_DATABASE' = 'neo4j'
        'NEO4J_MAX_CONNECTION_POOL_SIZE' = '100'
        'NEO4J_CONNECTION_ACQUISITION_TIMEOUT' = '60s'
        'NEO4J_MAX_CONNECTION_LIFETIME' = '1h'
        'VIBEGRAPH_REALTIME_DEMO_PERMIT' = 'false'
        'VIBEGRAPH_AUTH_BOOTSTRAP_ENABLED' = 'false'
        'VIBEGRAPH_PROJECTS_ALLOWED_ROOT' = ''
        'VIBEGRAPH_UPLOAD_WORKSPACE' = '.vibegraph/uploads'
        'VIBEGRAPH_IMPORT_ARCHIVE_MAX_SIZE' = '100MB'
        'VIBEGRAPH_IMPORT_ARCHIVE_MAX_REQUEST_SIZE' = '105MB'
        'VIBEGRAPH_PARSER_CACHE_DIR' = '.vibegraph/parse-cache'
        'VIBEGRAPH_PARSER_USE_CACHE' = 'true'
        'VIBEGRAPH_PARSER_DEEP_CPG' = 'false'
        'VIBEGRAPH_WATCHER_ENABLED' = 'true'
        'VIBEGRAPH_WATCHER_DEBOUNCE_MS' = '500'
        'FRONTEND_URL' = 'http://localhost:3000'
        'VITE_API_URL' = 'http://localhost:8080'
        'VITE_WS_URL' = 'http://localhost:8080/ws/graph-updates'
        'GEMINI_API_KEY' = ''
        'GEMINI_API_KEYS' = ''
        'SPRING_AI_MODEL_CHAT' = 'none'
        'VIBEGRAPH_USECASE_LLM_ENABLED' = 'false'
    }

    if (!(Test-Path $envPath)) {
        if (Test-Path $examplePath) {
            Copy-Item $examplePath $envPath
            Write-Ok "Created .env from .env.example"
        } else {
            New-Item -ItemType File -Path $envPath -Force | Out-Null
            Write-Ok "Created .env with local defaults"
        }
    }

    $content = Get-Content $envPath -Raw
    $missing = @()
    foreach ($key in $defaults.Keys) {
        if ($content -notmatch "(?m)^\s*$([regex]::Escape($key))\s*=") {
            $missing += "$key=$($defaults[$key])"
        }
    }

    if ($missing.Count -gt 0) {
        Add-Content -Path $envPath -Value ("`n# Added by quick-start-win.ps1 for the current Docker stack")
        Add-Content -Path $envPath -Value $missing
        Write-Ok "Added missing .env keys for Postgres/JWT/current Docker stack"
    }
}

function Assert-EnvReady {
    $envMap = Get-EnvMap
    $required = @(
        'SERVER_PORT',
        'POSTGRES_DB',
        'POSTGRES_USER',
        'POSTGRES_PASSWORD',
        'JWT_SECRET',
        'NEO4J_USERNAME',
        'NEO4J_PASSWORD',
        'NEO4J_DATABASE',
        'NEO4J_MAX_CONNECTION_POOL_SIZE',
        'NEO4J_CONNECTION_ACQUISITION_TIMEOUT',
        'NEO4J_MAX_CONNECTION_LIFETIME',
        'VIBEGRAPH_IMPORT_ARCHIVE_MAX_SIZE',
        'VIBEGRAPH_IMPORT_ARCHIVE_MAX_REQUEST_SIZE',
        'VIBEGRAPH_PARSER_CACHE_DIR',
        'VIBEGRAPH_WATCHER_ENABLED',
        'VIBEGRAPH_WATCHER_DEBOUNCE_MS',
        'FRONTEND_URL',
        'VITE_API_URL',
        'VITE_WS_URL'
    )

    $missing = @()
    foreach ($key in $required) {
        if (!$envMap.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($envMap[$key])) {
            $missing += $key
        }
    }

    if ($missing.Count -gt 0) {
        Fail "Missing required .env values: $($missing -join ', '). Fill them in and run again."
    }
    if ($envMap['JWT_SECRET'].Length -lt 32) {
        Fail "JWT_SECRET in .env must be at least 32 characters."
    }
    Write-Ok ".env contains the Postgres, Neo4j, JWT, backend, and frontend keys required by docker-compose.yml"
}

function Wait-For-Docker {
    Write-Step "Waiting for Docker Desktop"
    for ($i = 1; $i -le 60; $i++) {
        docker info *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Docker is running"
            return
        }
        Start-Sleep -Seconds 3
    }
    Fail "Docker is installed but not running. Open Docker Desktop, wait until it is ready, then run this script again."
}

function Wait-For-Http($Url, $Name, $Attempts = 60) {
    Write-Step "Waiting for $Name ($Url)"
    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Ok "$Name is reachable"
                return
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    Fail "$Name did not become reachable at $Url. Run: docker compose logs --tail=200"
}

function Ensure-LocalFrontendDev {
    $webRoot = Join-Path $Root 'vibegraph-web'
    $logDir = Join-Path $Root '.vibegraph'
    $logPath = Join-Path $logDir 'vite-5173.log'

    if (!(Test-Path $webRoot)) {
        Fail "vibegraph-web/ not found. Cannot start the local Vite frontend."
    }
    if (!(Test-Command node)) {
        Fail "Node.js is missing. Install Node.js 20.19+ or 22.12+ before starting the local Vite frontend."
    }
    if (!(Test-Command npm)) {
        Fail "npm is missing. Install Node.js/npm before starting the local Vite frontend."
    }
    if (!(Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }

    try {
        $response = Invoke-WebRequest -Uri 'http://localhost:5173' -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
            Write-Ok "Local Vite frontend is already reachable at http://localhost:5173"
            return
        }
    } catch {
        # Not running yet; start it below.
    }

    if (!(Test-Path (Join-Path $webRoot 'node_modules'))) {
        Write-Step "Installing frontend dependencies for local Vite"
        Push-Location $webRoot
        try {
            npm ci
            if ($LASTEXITCODE -ne 0) {
                Fail "npm ci failed in vibegraph-web/. Check Node/npm output above."
            }
        } finally {
            Pop-Location
        }
    }

    Write-Step "Starting local Vite frontend on http://localhost:5173"
    $command = "npm run dev -- --host 0.0.0.0 --port 5173 > `"$logPath`" 2>&1"
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $command -WorkingDirectory $webRoot -WindowStyle Hidden | Out-Null
    Wait-For-Http 'http://localhost:5173' 'Local Vite frontend' 30
    Write-Ok "Local Vite log: $logPath"
}

function Ensure-TestAccounts {
    Write-Step "Ensuring local test accounts"
    $envMap = Get-EnvMap
    $dbUser = $envMap['POSTGRES_USER']
    $dbName = $envMap['POSTGRES_DB']
    $adminHash = '$2a$10$HQ8AF989FyysS/rgHQ7twOwuBRKDioEHpDp9hBjkJ2WVbjbOkksd.'
    $userHash = '$2a$10$LOUKr3Pu3.YOQvtZ0Evaee5u1WKiYN0ziLKnS/Mh7B5Xgf6f.gWGa'

    $sql = @"
WITH free_plan AS (
    SELECT id FROM plans WHERE code = 'FREE'
),
admin_upsert AS (
    INSERT INTO users (email, password_hash, display_name, email_verified, role)
    SELECT 'admin@vibegraph.com', '$adminHash', 'VibeGraph Admin', true, 'ADMIN'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower('admin@vibegraph.com'))
    RETURNING id
),
admin_existing AS (
    UPDATE users
    SET password_hash = '$adminHash',
        display_name = COALESCE(NULLIF(display_name, ''), 'VibeGraph Admin'),
        email_verified = true,
        role = 'ADMIN'
    WHERE lower(email) = lower('admin@vibegraph.com')
    RETURNING id
),
user_upsert AS (
    INSERT INTO users (email, password_hash, display_name, email_verified, role)
    SELECT 'user@vibegraph.com', '$userHash', 'VibeGraph User', true, 'USER'
    WHERE NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower('user@vibegraph.com'))
    RETURNING id
),
user_existing AS (
    UPDATE users
    SET password_hash = '$userHash',
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
"@

    $sql | docker compose exec -T postgres psql -U $dbUser -d $dbName -v ON_ERROR_STOP=1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Fail "Failed to seed local test accounts. Run: docker compose logs --tail=200 postgres backend"
    }
    Write-Ok "Local test accounts are ready: admin@vibegraph.com/admin123 and user@vibegraph.com/user123"
}

Write-Step "Checking repository files"
if (!(Test-Path (Join-Path $Root 'docker-compose.yml'))) {
    Fail "docker-compose.yml not found. Run this script from the VibeGraph repository root."
}
Ensure-EnvFile
Assert-EnvReady
if (!(Test-Path (Join-Path $Root 'projects'))) {
    New-Item -ItemType Directory -Path (Join-Path $Root 'projects') | Out-Null
    Write-Ok "Created projects/ for the writable local patch mount"
}
Write-Ok ".env, projects/, and docker-compose.yml are ready"

Write-Step "Checking Docker"
if (!(Test-Command docker)) {
    if (Test-Command winget) {
        Write-Step "Docker is missing. Installing Docker Desktop with winget"
        winget install --id Docker.DockerDesktop --source winget --accept-package-agreements --accept-source-agreements
        Write-Host "Docker Desktop may require a logout/restart or WSL setup on first install."
    } else {
        Fail "Docker is missing and winget is not available. Install Docker Desktop manually: https://www.docker.com/products/docker-desktop/"
    }
}

try {
    Start-Process "Docker Desktop" -ErrorAction SilentlyContinue | Out-Null
} catch {
    $dockerDesktop = "$Env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerDesktop) {
        Start-Process $dockerDesktop | Out-Null
    }
}
Wait-For-Docker

Write-Step "Checking Docker Compose"
docker compose version | Out-Host
if ($LASTEXITCODE -ne 0) {
    Fail "Docker Compose v2 is required. Update Docker Desktop and run again."
}

Write-Step "Validating docker-compose.yml and .env"
docker compose config --quiet
if ($LASTEXITCODE -ne 0) {
    Fail "docker compose config failed. Check .env values and docker-compose.yml."
}
Write-Ok "Docker Compose config is valid"
Write-Ok "Backend startup will run Flyway migrations V1..V4, including plans and credit tables"

if ($Reset) {
    Write-Step "Reset requested: stopping stack and removing volumes"
    docker compose down -v --remove-orphans
} else {
    Write-Step "Stopping stale containers, keeping data volumes"
    docker compose down --remove-orphans
}

Write-Step "Starting VibeGraph stack"
if ($NoBuild) {
    docker compose up -d
} else {
    docker compose up -d --build
}

Write-Step "Current containers"
docker compose ps

Wait-For-Http "http://localhost:7474" "Neo4j Browser" 40
Wait-For-Http "http://localhost:8080/actuator/health" "Backend health" 80
Ensure-TestAccounts
Wait-For-Http "http://localhost:3000" "Frontend" 40
Ensure-LocalFrontendDev

Write-Host "`nVibeGraph is ready." -ForegroundColor Green
Write-Host "Frontend: http://localhost:3000"
Write-Host "Local dev frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8080"
Write-Host "Postgres: localhost:5432"
Write-Host "Neo4j:    http://localhost:7474"
Write-Host "`nTest accounts:"
Write-Host "  Admin: admin@vibegraph.com / admin123"
Write-Host "  User:  user@vibegraph.com / user123"
Write-Host "`nUseful commands:"
Write-Host "  docker compose logs -f backend"
Write-Host "  docker compose logs -f frontend"
Write-Host "  docker compose logs -f postgres"
Write-Host "  Get-Content .\.vibegraph\vite-5173.log -Wait"
Write-Host "  docker compose down"
Write-Host "  .\quick-start-win.ps1 -Reset   # delete volumes and recreate DB from Flyway V1..V4"
