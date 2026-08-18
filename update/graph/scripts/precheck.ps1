<#
.SYNOPSIS
    VibeGraph CI/CD pre-check gatekeeper (run before pushing commits for review).

.DESCRIPTION
    Default run (no switches):
        1. frontend:lint         -> npm run lint        (vibegraph-web)
        2. frontend:type-check   -> npm run type-check  (vibegraph-web)
        3. backend:compile       -> mvnw -q -DskipTests compile (repo root)

    -WithTests additionally runs:
        4. frontend:unit-tests   -> npm run test:unit -- run  (vitest run, non-watch)
        5. backend:test          -> mvnw test

    -SkipBackend skips all Maven steps.

    Only npm scripts that actually exist in vibegraph-web/package.json are used.
    The Maven wrapper (mvnw.cmd) at the repo root is preferred; falls back to
    'mvn' on PATH if the wrapper is missing.

    Maven steps are retried once after 60 seconds because another agent/process
    may hold Maven build file locks in the same repo concurrently.

    Prints a [Step, PASS/FAIL] summary and exits non-zero if any step failed.

.PARAMETER WithTests
    Also run frontend unit tests and backend tests. Off by default.

.PARAMETER SkipBackend
    Skip all Maven/backend steps. Off by default.

.EXAMPLE
    .\precheck.ps1
.EXAMPLE
    .\precheck.ps1 -WithTests
.EXAMPLE
    .\precheck.ps1 -SkipBackend
#>

[CmdletBinding()]
param(
    [switch]$WithTests,
    [switch]$SkipBackend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --- Paths -------------------------------------------------------------
# Script lives in <repo>/update/graph/scripts -> repo root is 3 levels up.
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$WebDir   = Join-Path $RepoRoot 'vibegraph-web'

# --- Maven command: prefer wrapper, fall back to mvn on PATH ------------
$MvnExe = Join-Path $RepoRoot 'mvnw.cmd'
if (-not (Test-Path $MvnExe)) {
    $MvnExe = 'mvn'
}

# --- Step bookkeeping ----------------------------------------------------
$script:Results = [System.Collections.Generic.List[object]]::new()

function Invoke-Step {
    param(
        [string]$Name,
        [string]$WorkingDir,
        [string]$Command,
        [string[]]$Arguments,
        [int]$Retries = 0,
        [int]$RetryDelaySeconds = 60
    )

    Write-Host ''
    Write-Host "==> $Name : $Command $($Arguments -join ' ')" -ForegroundColor Cyan

    $attempt  = 0
    $exitCode = 1
    while ($true) {
        $attempt++
        Push-Location $WorkingDir
        try {
            & $Command @Arguments
            $exitCode = $LASTEXITCODE
        }
        catch {
            Write-Host "Failed to launch '$Command': $_" -ForegroundColor Red
            $exitCode = 1
        }
        finally {
            Pop-Location
        }

        if ($exitCode -eq 0 -or $attempt -gt $Retries) { break }

        Write-Host ("Step '$Name' failed (exit $exitCode). Possible concurrent Maven build lock. " +
                    "Retrying in $RetryDelaySeconds s (attempt $($attempt + 1) of $($Retries + 1))...") -ForegroundColor Yellow
        Start-Sleep -Seconds $RetryDelaySeconds
    }

    $status = 'FAIL'
    if ($exitCode -eq 0) { $status = 'PASS' }
    $script:Results.Add([pscustomobject]@{ Step = $Name; Result = $status; ExitCode = $exitCode })

    $color = 'Red'
    if ($status -eq 'PASS') { $color = 'Green' }
    Write-Host "==> $Name : $status (exit $exitCode)" -ForegroundColor $color
}

# --- Sanity checks -------------------------------------------------------
if (-not (Test-Path $WebDir)) {
    Write-Error "Frontend directory not found: $WebDir"
    exit 2
}
if (-not (Test-Path (Join-Path $WebDir 'node_modules'))) {
    Write-Warning "node_modules not found in $WebDir - run 'npm install' there first."
}

# --- Frontend gates ------------------------------------------------------
Invoke-Step -Name 'frontend:lint'       -WorkingDir $WebDir -Command 'npm' -Arguments @('run', 'lint')
Invoke-Step -Name 'frontend:type-check' -WorkingDir $WebDir -Command 'npm' -Arguments @('run', 'type-check')

if ($WithTests) {
    # 'test:unit' is vitest (watch mode by default); pass 'run' for a single non-watch pass.
    Invoke-Step -Name 'frontend:unit-tests' -WorkingDir $WebDir -Command 'npm' -Arguments @('run', 'test:unit', '--', 'run')
}

# --- Backend gates -------------------------------------------------------
if ($SkipBackend) {
    Write-Host ''
    Write-Host 'Backend steps skipped (-SkipBackend).' -ForegroundColor Yellow
}
else {
    Invoke-Step -Name 'backend:compile' -WorkingDir $RepoRoot -Command $MvnExe `
        -Arguments @('-q', '-DskipTests', 'compile') -Retries 1

    if ($WithTests) {
        Invoke-Step -Name 'backend:test' -WorkingDir $RepoRoot -Command $MvnExe `
            -Arguments @('test') -Retries 1
    }
}

# --- Summary ---------------------------------------------------------------
Write-Host ''
Write-Host '================= PRECHECK SUMMARY ================='
$script:Results | Format-Table Step, Result, ExitCode -AutoSize | Out-String | Write-Host

$failed = @($script:Results | Where-Object { $_.Result -eq 'FAIL' })
if ($failed.Count -gt 0) {
    Write-Host "PRECHECK FAILED: $($failed.Count) step(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host 'PRECHECK PASSED: all steps succeeded.' -ForegroundColor Green
exit 0
