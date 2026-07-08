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

Write-Step "Checking repository files"
if (!(Test-Path (Join-Path $Root '.env'))) {
    Fail "Missing .env in repository root. Put the internal .env file next to docker-compose.yml, then run again."
}
if (!(Test-Path (Join-Path $Root 'docker-compose.yml'))) {
    Fail "docker-compose.yml not found. Run this script from the VibeGraph repository root."
}
Write-Ok ".env and docker-compose.yml found"

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
Wait-For-Http "http://localhost:3000" "Frontend" 40

Write-Host "`nVibeGraph is ready." -ForegroundColor Green
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend:  http://localhost:8080"
Write-Host "Neo4j:    http://localhost:7474"
Write-Host "`nUseful commands:"
Write-Host "  docker compose logs -f backend"
Write-Host "  docker compose logs -f frontend"
Write-Host "  docker compose down"
Write-Host "  .\quick-start-win.ps1 -Reset   # delete local Docker volumes and start fresh"
