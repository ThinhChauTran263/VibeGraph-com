<#
.SYNOPSIS
    Restores a VibeGraph backup into FRESH volumes and verifies it loaded.

.DESCRIPTION
    This is the restore drill for Đ7-2c. It never writes to the volumes the
    running stack uses: every artifact lands in a NEW volume suffixed with a
    timestamp, so the drill can be run on a live machine without risking the
    data it is meant to protect.

    A backup nobody has restored is not a backup. The drill answers one
    question with numbers: does postgres.sql load, and do users / projects /
    api_keys come back at exactly the counts backup.ps1 recorded in
    manifest.json? Any mismatch fails the run.

    Nothing is switched over automatically. When the drill passes, the script
    prints the volume names it created and the manual steps to point the stack
    at them — that swap is the operator's call, not this script's.

.PARAMETER BackupDir
    A timestamped directory produced by scripts/backup.ps1 (the one holding
    manifest.json).

.PARAMETER Confirm
    Required. Without it the script prints the plan and exits, so an accidental
    invocation cannot start pulling images or creating volumes.

.EXAMPLE
    # See what it would do, touch nothing:
    ./scripts/restore.ps1 -BackupDir ../vibegraph-backups/2026-08-13T04-12-00Z

.EXAMPLE
    # Run the drill:
    ./scripts/restore.ps1 -BackupDir ../vibegraph-backups/2026-08-13T04-12-00Z -Confirm
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,

    # Repository root (where docker-compose.yml lives).
    [string]$Root = (Split-Path -Parent $PSScriptRoot),

    # Appended to every volume this script creates. Defaults to a UTC stamp so
    # repeated drills never collide.
    [string]$Suffix = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'),

    # Postgres row counts are the only pass/fail gate. Graph data is
    # rebuildable by re-analyzing, and uploads are large; skip them to keep a
    # routine drill short.
    [switch]$SkipNeo4j,
    [switch]$SkipUploads,

    # Keep the restored volumes and the temporary container after the run.
    # Off by default: the drill proves loadability, it does not accumulate
    # copies of the database on the operator's disk.
    [switch]$KeepVolumes,

    [switch]$Confirm,

    [int]$PostgresReadyTimeoutSec = 120
)

$ErrorActionPreference = "Stop"
$startedAt = Get-Date

function Fail([string]$message) {
    Write-Host "FAIL: $message" -ForegroundColor Red
    exit 1
}

function Assert-LastExit([string]$what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what (exit $LASTEXITCODE)" }
}

function Step([string]$text) {
    Write-Host "==> $text" -ForegroundColor Cyan
}

# --------------------------------------------------------------------------
# [1/6] Read and validate the backup
# --------------------------------------------------------------------------
Step "[1/6] reading backup"

if (-not (Test-Path -LiteralPath $BackupDir)) { Fail "backup directory not found: $BackupDir" }
$BackupDir = (Resolve-Path -LiteralPath $BackupDir).Path

$manifestPath = Join-Path $BackupDir 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    Fail "manifest.json not found in $BackupDir — is this a directory produced by backup.ps1?"
}

try { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json }
catch { Fail "manifest.json is not valid JSON: $($_.Exception.Message)" }

$pgArtifact = $manifest.artifacts.postgres
if (-not $pgArtifact) { Fail "manifest lists no postgres artifact; there is nothing to verify" }

$pgDumpPath = Join-Path $BackupDir $pgArtifact.file
if (-not (Test-Path -LiteralPath $pgDumpPath)) { Fail "postgres dump missing: $pgDumpPath" }

# The checksum is the only guard against a truncated or half-copied dump. A
# restore that silently loads a damaged file is worse than one that refuses.
$actualHash = (Get-FileHash -LiteralPath $pgDumpPath -Algorithm SHA256).Hash
if ($pgArtifact.sha256 -and $actualHash -ne $pgArtifact.sha256) {
    Fail "postgres.sql checksum mismatch — the file changed since backup. expected $($pgArtifact.sha256), got $actualHash"
}

$expected = $manifest.row_counts
if (-not $expected -or $null -eq $expected.users) {
    Fail "manifest has no row_counts; without them the drill has no pass/fail criterion"
}

$pgImage = if ($manifest.images.postgres) { $manifest.images.postgres } else { 'postgres:16.11-alpine' }

Write-Host "    backup          : $BackupDir"
Write-Host "    created (UTC)   : $($manifest.created_utc)"
Write-Host "    postgres image  : $pgImage"
Write-Host "    expected counts : users=$($expected.users)  projects=$($expected.projects)  api_keys=$($expected.api_keys)"

# --------------------------------------------------------------------------
# Plan, and the -Confirm gate
# --------------------------------------------------------------------------
$pgVolume  = "vibegraph-restore-postgres-$Suffix"
$neoVolume = "vibegraph-restore-neo4j-$Suffix"
$upVolume  = "vibegraph-restore-uploads-$Suffix"
$container = "vibegraph-restore-$Suffix"

Write-Host ""
Write-Host "Plan (nothing below touches the running stack or its volumes):"
Write-Host "  create volume    $pgVolume"
if (-not $SkipNeo4j)   { Write-Host "  create volume    $neoVolume" }
if (-not $SkipUploads) { Write-Host "  create volume    $upVolume" }
Write-Host "  start container  $container ($pgImage) bound to $pgVolume, no published port"
Write-Host "  load             $($pgArtifact.file) into it"
Write-Host "  compare          users / projects / api_keys against manifest"
if ($KeepVolumes) { Write-Host "  keep             volumes and container after the run (-KeepVolumes)" }
else              { Write-Host "  remove           the container and the volumes it created when done" }
Write-Host ""

if (-not $Confirm) {
    Write-Host "Dry run. Re-run with -Confirm to execute." -ForegroundColor Yellow
    exit 0
}

# Refuse to reuse a name, rather than writing into something that already
# exists and might not be ours.
foreach ($v in @($pgVolume, $neoVolume, $upVolume)) {
    docker volume inspect $v 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Fail "volume $v already exists; pass a different -Suffix" }
}

# --------------------------------------------------------------------------
# [2/6] Fresh volume + throwaway Postgres
# --------------------------------------------------------------------------
Step "[2/6] starting an isolated postgres"

$restorePassword = [guid]::NewGuid().ToString('N')
$restoreUser = 'vgrestore'
$restoreDb   = 'vgrestore'

docker volume create $pgVolume | Out-Null
Assert-LastExit "creating volume $pgVolume"

# No -p: the container is reachable only through `docker exec`, so the drill
# cannot collide with a Postgres already listening on the host.
docker run -d --name $container `
    -v "${pgVolume}:/var/lib/postgresql/data" `
    -e POSTGRES_USER=$restoreUser `
    -e POSTGRES_PASSWORD=$restorePassword `
    -e POSTGRES_DB=$restoreDb `
    $pgImage | Out-Null
Assert-LastExit "starting $container"

try {
    Step "[3/6] waiting for it to accept connections"
    $deadline = (Get-Date).AddSeconds($PostgresReadyTimeoutSec)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        docker exec $container pg_isready -U $restoreUser -d $restoreDb 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { Fail "postgres did not become ready within $PostgresReadyTimeoutSec s" }
    Write-Host "    ready"

    # ----------------------------------------------------------------------
    # [4/6] Load the dump
    # ----------------------------------------------------------------------
    Step "[4/6] loading $($pgArtifact.file)"

    docker cp $pgDumpPath "${container}:/tmp/restore.sql"
    Assert-LastExit "copying the dump into $container"

    # ON_ERROR_STOP turns a mid-file failure into a non-zero exit. Without it
    # psql reports success after skipping broken statements, which is exactly
    # how a restore drill ends up passing on a dump that lost tables.
    $loadLog = Join-Path $BackupDir "restore-$Suffix.log"
    docker exec $container psql -v ON_ERROR_STOP=1 -U $restoreUser -d $restoreDb -f /tmp/restore.sql 2>&1 |
        Tee-Object -FilePath $loadLog | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "psql failed while loading the dump; see $loadLog" }
    Write-Host "    loaded (log: $loadLog)"

    # ----------------------------------------------------------------------
    # [5/6] The gate: do the counts come back?
    # ----------------------------------------------------------------------
    Step "[5/6] verifying row counts"

    # `projects` is the ownership table. The ProjectOwnership entity maps to
    # @Table(name = "projects") — there is no project_ownership table, and an
    # earlier draft of this drill failed on its first statement because of it.
    $sql = "SELECT (SELECT count(*) FROM users)||'|'||(SELECT count(*) FROM projects)||'|'||(SELECT count(*) FROM api_keys);"
    $raw = docker exec $container psql -U $restoreUser -d $restoreDb -t -A -c $sql
    Assert-LastExit "counting rows in the restored database"

    $parts = ($raw | Out-String).Trim().Split('|')
    if ($parts.Count -lt 3) { Fail "unexpected count output: '$raw'" }

    $got = [ordered]@{
        users    = [int]$parts[0]
        projects = [int]$parts[1]
        api_keys = [int]$parts[2]
    }

    $mismatch = @()
    foreach ($key in @('users', 'projects', 'api_keys')) {
        $want = [int]$expected.$key
        $have = [int]$got.$key
        $verdict = if ($want -eq $have) { 'OK' } else { 'MISMATCH'; }
        Write-Host ("    {0,-9} expected {1,-8} restored {2,-8} {3}" -f $key, $want, $have, $verdict)
        if ($want -ne $have) { $mismatch += "$key expected $want, restored $have" }
    }
    if ($mismatch.Count -gt 0) {
        Fail ("restore verification failed: " + ($mismatch -join '; '))
    }

    Step "[6/6] drill passed"
    Write-Host "    all three control-plane counts match the manifest." -ForegroundColor Green
}
finally {
    if ($KeepVolumes) {
        Write-Host ""
        Write-Host "Kept for inspection (-KeepVolumes):"
        Write-Host "  container : $container"
        Write-Host "  volume    : $pgVolume"
        Write-Host ""
        Write-Host "To point the stack at the restored data, stop the stack, then repoint the"
        Write-Host "postgres-data volume by hand. This script deliberately does not do it:"
        Write-Host "swapping the live volume is a decision, not a step."
        Write-Host ""
        Write-Host "Clean up when finished:"
        Write-Host "  docker rm -f $container"
        Write-Host "  docker volume rm $pgVolume"
    }
    else {
        Write-Host ""
        Step "cleaning up"
        docker rm -f $container 2>$null | Out-Null
        docker volume rm $pgVolume 2>$null | Out-Null
        Write-Host "    removed $container and $pgVolume (the drill's own volume only)"
    }
}

# --------------------------------------------------------------------------
# Neo4j and uploads: loadability only, no count gate
# --------------------------------------------------------------------------
if (-not $SkipNeo4j) {
    if ($manifest.artifacts.neo4j) {
        $neoFile = Join-Path $BackupDir $manifest.artifacts.neo4j.file
        if (Test-Path -LiteralPath $neoFile) {
            Write-Host ""
            Write-Host "Neo4j dump present: $($manifest.artifacts.neo4j.file) ($([math]::Round((Get-Item $neoFile).Length / 1MB, 2)) MB)"
            Write-Host "  Graph data is rebuildable by re-analyzing projects, so it is not part of"
            Write-Host "  the pass/fail gate. To load it into a fresh volume:"
            Write-Host "    docker volume create $neoVolume"
            Write-Host "    docker run --rm -v ${neoVolume}:/data -v ${BackupDir}:/backup $($manifest.images.neo4j) ``"
            Write-Host "      neo4j-admin database load --from-path=/backup --overwrite-destination=true $($manifest.artifacts.neo4j.database)"
        }
    }
    else { Write-Host ""; Write-Host "No Neo4j dump in this backup (taken with -SkipNeo4j)." }
}

if (-not $SkipUploads) {
    if ($manifest.artifacts.uploads) {
        $upFile = Join-Path $BackupDir $manifest.artifacts.uploads.file
        if (Test-Path -LiteralPath $upFile) {
            Write-Host ""
            Write-Host "Uploads archive present: $($manifest.artifacts.uploads.file) ($([math]::Round((Get-Item $upFile).Length / 1MB, 2)) MB)"
            Write-Host "  To extract into a fresh volume:"
            Write-Host "    docker volume create $upVolume"
            Write-Host "    docker run --rm -v ${upVolume}:/uploads -v ${BackupDir}:/backup alpine ``"
            Write-Host "      tar -xzf /backup/$($manifest.artifacts.uploads.file) -C /uploads"
        }
    }
    else { Write-Host ""; Write-Host "No uploads archive in this backup (taken with -SkipUploads)." }
}

Write-Host ""
Write-Host ("Finished in {0:n0}s." -f ((Get-Date) - $startedAt).TotalSeconds)
