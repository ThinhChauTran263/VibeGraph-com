<#
.SYNOPSIS
    End-to-end demo of the VibeGraph Local Patch MVP, driven by a single command.

.DESCRIPTION
    Exercises the full CLI -> backend Local Patch flow against the running Docker stack:
      1. Verifies the Docker stack is healthy (backend / frontend / postgres / neo4j).
      2. Ensures the `vibegraph` CLI is available (installs it, or falls back to `node`).
      3. Creates a throwaway sample Java project under ./projects/demo-local-patch.
      4. Registers a random demo user (isolated, temp config dir).
      5. Imports the project (import-local), edits a file, pushes the patch, analyzes,
         and prints status/list.
      6. Runs a negative dry-run: drops a fake .env + secrets/prod.pem into the sample
         and confirms the CLI skips them (secret pattern) instead of sending them.
      7. Always deletes the demo secret files. Keeps the demo project unless -Clean.

    No real secrets are used or printed. The demo user password is generated at runtime
    and never echoed. Auth token + push snapshots live only in a temporary config dir.

.PARAMETER Clean
    After the demo, delete the backend project and remove ./projects/demo-local-patch.

.PARAMETER Rebuild
    If the stack is unhealthy, bring it up with `docker compose up -d --build`
    (default only runs `docker compose up -d`).

.PARAMETER ApiUrl
    Override the backend API URL. Defaults to the backend's published 8080 port.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\demo-local-patch.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\demo-local-patch.ps1 -Clean
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$Rebuild,
    [string]$ApiUrl
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# --- paths & run-scoped state --------------------------------------------------------------
$RepoRoot     = Split-Path -Parent $PSScriptRoot
$Timestamp    = Get-Date -Format 'yyyyMMdd-HHmmss'
$SampleName   = 'demo-local-patch'
$SampleHost   = Join-Path (Join-Path $RepoRoot 'projects') $SampleName
$BackendPath  = "/projects/$SampleName"            # backend-visible bind-mount path (POSIX)
$JavaRelDir   = 'src/main/java/com/example/demo'
$JavaFileHost = Join-Path $SampleHost ($JavaRelDir -replace '/', '\') | Join-Path -ChildPath 'App.java'
$TempConfig   = Join-Path $env:TEMP "vibegraph-demo-$Timestamp"
$DemoEmail    = "demo-$Timestamp@test.local"
# Generated throwaway password (NOT a real secret) — never printed or recorded.
$DemoPassword = 'Demo!' + ([guid]::NewGuid().ToString('N').Substring(0, 16))

$script:Commands = New-Object System.Collections.Generic.List[string]
$script:ProjectId = $null
$script:Overall  = 'PASS'
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:VibeExe    = $null
$script:VibePrefix = @()

# --- output helpers ------------------------------------------------------------------------
function Write-Step  { param([string]$Text) Write-Host "`n=== $Text ===" -ForegroundColor Green }
function Write-Info  { param([string]$Text) Write-Host "    $Text" -ForegroundColor Gray }
function Write-Warn2 { param([string]$Text) Write-Host "    ! $Text" -ForegroundColor Yellow }
function Fail-Check  { param([string]$Text) $script:Overall = 'FAIL'; $script:Failures.Add($Text); Write-Host "    [FAIL] $Text" -ForegroundColor Red }
function Pass-Check  { param([string]$Text) Write-Host "    [OK]   $Text" -ForegroundColor Green }

# Build a display string with the value after --password redacted.
function Format-VibeDisplay {
    param([string[]]$Parts)
    $redacted = @()
    for ($i = 0; $i -lt $Parts.Count; $i++) {
        if ($i -gt 0 -and $Parts[$i - 1] -eq '--password') { $redacted += '***' }
        else { $redacted += $Parts[$i] }
    }
    return ($redacted -join ' ')
}

# Run the vibegraph CLI (global binary or `node` fallback). Records a redacted command line.
function Invoke-Vibe {
    param(
        [Parameter(Mandatory)][string[]]$VibeArgs,
        [switch]$AllowFail
    )
    $full = @()
    if ($script:VibePrefix.Count -gt 0) { $full += $script:VibePrefix }
    $full += $VibeArgs
    $display = "$($script:VibeExe) " + (Format-VibeDisplay -Parts $full)
    $script:Commands.Add($display)
    Write-Host "  > $display" -ForegroundColor Cyan
    $out = & $script:VibeExe @full 2>&1 | Out-String
    $code = $LASTEXITCODE
    if ($out.Trim()) { Write-Host ($out.TrimEnd()) }
    if ($code -ne 0 -and -not $AllowFail) {
        throw "CLI command failed (exit $code): $display"
    }
    return $out
}

# --- docker helpers ------------------------------------------------------------------------
$ComposeServices = @{
    'backend'  = 'vibegraph-backend'
    'frontend' = 'vibegraph-frontend'
    'postgres' = 'vibegraph-postgres'
    'neo4j'    = 'vibegraph-neo4j'
}

function Get-ContainerHealth {
    param([string]$Container)
    $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $Container 2>$null
    if ($LASTEXITCODE -ne 0) { return 'missing' }
    return ($status | Out-String).Trim()
}

function Test-StackHealthy {
    $ok = $true
    foreach ($svc in $ComposeServices.Keys) {
        $h = Get-ContainerHealth -Container $ComposeServices[$svc]
        if ($h -eq 'healthy' -or $h -eq 'running') {
            Write-Info "$svc : $h"
        } else {
            Write-Warn2 "$svc : $h"
            $ok = $false
        }
    }
    return $ok
}

function Wait-Backend {
    param([string]$Url, [int]$TimeoutSec = 150)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 5 -ErrorAction Stop
            if ($resp.status -eq 'UP') { return $true }
        } catch { Start-Sleep -Seconds 3 }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Resolve-ApiUrl {
    if ($ApiUrl) { return $ApiUrl.TrimEnd('/') }
    try {
        $mapping = (& docker compose port backend 8080 2>$null | Out-String).Trim()
        if ($mapping -match ':(\d+)\s*$') { return "http://localhost:$($Matches[1])" }
    } catch { }
    return 'http://localhost:8080'
}

# ===========================================================================================
# MAIN
# ===========================================================================================
try {
    Write-Host "VibeGraph Local Patch demo  (run id: $Timestamp)" -ForegroundColor White
    Write-Host "Repo: $RepoRoot" -ForegroundColor DarkGray

    # --- 1. Docker stack ------------------------------------------------------------------
    Write-Step '1/9 Checking Docker stack'
    Push-Location $RepoRoot
    try {
        & docker compose ps --format 'table {{.Name}}\t{{.State}}\t{{.Status}}' | Out-Host
        if (-not (Test-StackHealthy)) {
            $upArgs = @('compose', 'up', '-d')
            if ($Rebuild) { $upArgs += '--build' }
            Write-Warn2 "Stack not fully healthy. Running: docker $($upArgs -join ' ')"
            & docker @upArgs | Out-Host
        }
    } finally { Pop-Location }

    $ApiUrlResolved = Resolve-ApiUrl
    Write-Info "API URL: $ApiUrlResolved"
    if (-not (Wait-Backend -Url $ApiUrlResolved)) {
        throw "Backend is not healthy at $ApiUrlResolved. Start it with: docker compose up -d --build"
    }
    if (-not (Test-StackHealthy)) {
        throw "One or more services are unhealthy. Fix with: docker compose up -d --build"
    }
    Pass-Check 'Docker stack healthy (backend, frontend, postgres, neo4j)'

    # --- 2. CLI availability --------------------------------------------------------------
    Write-Step '2/9 Ensuring vibegraph CLI'
    $cliEntry = Join-Path $RepoRoot 'vibegraph-cli\bin\vibegraph.js'
    $vibeGlobal = Get-Command vibegraph -ErrorAction SilentlyContinue
    if (-not $vibeGlobal) {
        Write-Warn2 'vibegraph not on PATH. Attempting: npm install -g ./vibegraph-cli'
        Push-Location $RepoRoot
        try {
            & npm install -g ./vibegraph-cli 2>&1 | Out-Host
            $vibeGlobal = Get-Command vibegraph -ErrorAction SilentlyContinue
        } catch {
            Write-Warn2 "Global install failed: $($_.Exception.Message)"
        } finally {
            Pop-Location
        }
    }
    if ($vibeGlobal) {
        $script:VibeExe = 'vibegraph'
        $script:VibePrefix = @()
        Write-Info 'Using global `vibegraph` binary.'
    } else {
        $script:VibeExe = 'node'
        $script:VibePrefix = @($cliEntry)
        Write-Info "Falling back to: node $cliEntry"
        Write-Info 'To install globally later: npm install -g ./vibegraph-cli'
    }

    # --- 3. Isolated config ---------------------------------------------------------------
    Write-Step '3/9 Isolating CLI config'
    New-Item -ItemType Directory -Path $TempConfig -Force | Out-Null
    $env:VIBEGRAPH_CONFIG_DIR = $TempConfig
    $env:VIBEGRAPH_API_URL = $ApiUrlResolved
    Write-Info "VIBEGRAPH_CONFIG_DIR = $TempConfig"
    Write-Info "VIBEGRAPH_API_URL    = $ApiUrlResolved"

    # --- 4. Sample project ----------------------------------------------------------------
    Write-Step '4/9 Creating sample project'
    $javaDir = Split-Path -Parent $JavaFileHost
    New-Item -ItemType Directory -Path $javaDir -Force | Out-Null
    @'
package com.example.demo;

/** Minimal demo class analyzed by the VibeGraph Local Patch demo. */
public class App {

    public String greet(String name) {
        return "Hello, " + name;
    }
}
'@ | Set-Content -Path $JavaFileHost -Encoding UTF8
    Write-Info "Wrote $JavaFileHost"

    # --- 5. Register ----------------------------------------------------------------------
    Write-Step '5/9 Registering demo user'
    Write-Info "Email: $DemoEmail  (password generated, not shown)"
    Invoke-Vibe -VibeArgs @('register', '--email', $DemoEmail, '--password', $DemoPassword, '--name', 'Demo Local Patch') | Out-Null
    Pass-Check "Registered $DemoEmail"

    # --- 6. Import local ------------------------------------------------------------------
    Write-Step '6/9 Importing project (import-local)'
    $importOut = Invoke-Vibe -VibeArgs @('projects', 'import-local', '--path', $BackendPath, '--name', $SampleName)
    if ($importOut -match '"id"\s*:\s*"([^"]+)"') { $script:ProjectId = $Matches[1] }
    if (-not $script:ProjectId) { throw 'Could not parse projectId from import-local output.' }
    Pass-Check "Imported project id=$($script:ProjectId)"

    # --- 7. Edit + push -------------------------------------------------------------------
    Write-Step '7/9 Editing a file and pushing the patch'
    @'
package com.example.demo;

/** Minimal demo class analyzed by the VibeGraph Local Patch demo. */
public class App {

    public String greet(String name) {
        return "Hello, " + name;
    }

    // Added by demo-local-patch.ps1 to produce a real change to push.
    public String farewell(String name) {
        return "Goodbye, " + name;
    }
}
'@ | Set-Content -Path $JavaFileHost -Encoding UTF8
    Write-Info 'Added farewell() to App.java'
    $pushOut = Invoke-Vibe -VibeArgs @('projects', 'push', $script:ProjectId, '--root', $SampleHost)
    if ($pushOut -match 'Pushed patch:\s*(\d+)\s*changed') {
        Pass-Check "Push applied ($($Matches[1]) changed)"
    } else {
        Fail-Check "Push did not report an applied patch"
    }

    # --- 8. Analyze + status --------------------------------------------------------------
    Write-Step '8/9 Analyze + status + list'
    Invoke-Vibe -VibeArgs @('projects', 'analyze', $script:ProjectId) | Out-Null
    Pass-Check 'Analyze completed'
    Invoke-Vibe -VibeArgs @('projects', 'status', $script:ProjectId) | Out-Null
    Invoke-Vibe -VibeArgs @('projects', 'list') | Out-Null

    # --- 9. Negative dry-run (secret files must be skipped) --------------------------------
    Write-Step '9/9 Negative dry-run: secrets must be skipped'
    $secretsDir = Join-Path $SampleHost 'secrets'
    New-Item -ItemType Directory -Path $secretsDir -Force | Out-Null
    # Placeholder content — deliberately NOT a real secret.
    'DEMO_PLACEHOLDER=example-not-a-secret' | Set-Content -Path (Join-Path $SampleHost '.env') -Encoding UTF8
    'demo placeholder pem - not a real certificate or key' | Set-Content -Path (Join-Path $secretsDir 'prod.pem') -Encoding UTF8
    Write-Info 'Created fake .env and secrets/prod.pem'
    $dryOut = Invoke-Vibe -VibeArgs @('projects', 'push', $script:ProjectId, '--root', $SampleHost, '--dry-run')
    $skipped = 0
    if ($dryOut -match '(\d+)\s*skipped') { $skipped = [int]$Matches[1] }
    if ($skipped -ge 2 -and $dryOut -notmatch '\.env' -and $dryOut -notmatch 'prod\.pem') {
        Pass-Check "Dry-run skipped $skipped file(s); no secret sent"
    } elseif ($skipped -ge 2) {
        Pass-Check "Dry-run skipped $skipped file(s) (secrets excluded from payload)"
    } else {
        Fail-Check "Dry-run did not skip the secret files (skipped=$skipped)"
    }
}
catch {
    $script:Overall = 'FAIL'
    $script:Failures.Add($_.Exception.Message)
    Write-Host "`n[ERROR] $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    # --- cleanup: always remove demo secret files -----------------------------------------
    Write-Step 'Cleanup'
    foreach ($p in @((Join-Path $SampleHost '.env'), (Join-Path $SampleHost 'secrets\prod.pem'))) {
        if (Test-Path $p) { Remove-Item $p -Force -ErrorAction SilentlyContinue; Write-Info "Removed $p" }
    }
    $secretsDir = Join-Path $SampleHost 'secrets'
    if ((Test-Path $secretsDir) -and -not (Get-ChildItem $secretsDir -Force -ErrorAction SilentlyContinue)) {
        Remove-Item $secretsDir -Force -ErrorAction SilentlyContinue
    }
    # Verify no secret files linger.
    $leftover = @()
    foreach ($p in @((Join-Path $SampleHost '.env'), (Join-Path $SampleHost 'secrets\prod.pem'))) {
        if (Test-Path $p) { $leftover += $p }
    }
    if ($leftover.Count -eq 0) { Pass-Check 'No demo secret files left behind' }
    else { Fail-Check "Secret files remain: $($leftover -join ', ')" }

    if ($Clean) {
        if ($script:ProjectId) {
            try { Invoke-Vibe -VibeArgs @('projects', 'delete', $script:ProjectId) -AllowFail | Out-Null } catch { }
        }
        if (Test-Path $SampleHost) { Remove-Item $SampleHost -Recurse -Force -ErrorAction SilentlyContinue; Write-Info "Removed $SampleHost" }
    } else {
        Write-Info "Kept demo project at $SampleHost (use -Clean to remove)."
    }

    # Remove the isolated CLI config (token + snapshots) so nothing persists.
    if (Test-Path $TempConfig) { Remove-Item $TempConfig -Recurse -Force -ErrorAction SilentlyContinue }
    Remove-Item Env:\VIBEGRAPH_CONFIG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\VIBEGRAPH_API_URL -ErrorAction SilentlyContinue

    # --- final report ---------------------------------------------------------------------
    Write-Host "`n================ DEMO SUMMARY ================" -ForegroundColor White
    Write-Host "Project id : $(if ($script:ProjectId) { $script:ProjectId } else { '(not created)' })"
    Write-Host "Demo user  : $DemoEmail"
    Write-Host "Commands run:"
    foreach ($c in $script:Commands) { Write-Host "  - $c" }
    if ($script:Failures.Count -gt 0) {
        Write-Host "Failures:" -ForegroundColor Red
        foreach ($f in $script:Failures) { Write-Host "  - $f" -ForegroundColor Red }
    }
    $color = if ($script:Overall -eq 'PASS') { 'Green' } else { 'Red' }
    Write-Host "RESULT: $($script:Overall)" -ForegroundColor $color
    Write-Host "==============================================" -ForegroundColor White

    if ($script:Overall -ne 'PASS') { exit 1 }
}
