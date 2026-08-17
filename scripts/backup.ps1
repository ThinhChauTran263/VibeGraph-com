<#
  VibeGraph - backup the three persisted data stores (Đ7-2b).

  What it captures, in priority order:

    1. Postgres (`postgres-data`)  -> CONTROL PLANE, NOT REBUILDABLE.
       users, projects (ownership), api_keys, credits, quotas, audit logs,
       refresh sessions. If this volume is lost the data is gone for good.
       Captured with `pg_dump` while the container keeps serving (no downtime).

    2. Neo4j (`neo4j-data`)       -> DATA PLANE, rebuildable by re-analyzing.
       Captured with `neo4j-admin database dump`. Neo4j refuses to dump a
       database that is mounted in a running server ("It is not possible to
       dump a database that is mounted in a running Neo4j server"), so this
       step STOPS the neo4j container, dumps, and starts it again. Expect a
       short outage of graph reads. Skip it with -SkipNeo4j.

    3. Uploads (`upload-workspaces`) -> imported archive workspaces.
       Captured as a tar.gz from a throwaway container. Rebuildable only by
       re-uploading the original archives, which the operator may not still
       have.

  Secrets: this script never reads, prints, or writes credential VALUES.
  Postgres credentials are resolved inside the container from its own
  POSTGRES_USER / POSTGRES_DB / POSTGRES_PASSWORD environment, and Neo4j
  credentials from its own NEO4J_AUTH. Only variable NAMES appear in output.

  WARNING: the dump files contain password hashes, API key hashes and audit
  data. They are written OUTSIDE the repository by default so they can never
  be committed. Treat the output directory as a secret.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts/backup.ps1
    powershell -ExecutionPolicy Bypass -File scripts/backup.ps1 -SkipNeo4j
    powershell -ExecutionPolicy Bypass -File scripts/backup.ps1 -OutRoot D:\vg-backups
#>
param(
    # Repository root (where docker-compose.yml lives).
    [string]$Root = (Split-Path -Parent $PSScriptRoot),

    # Parent directory for timestamped backup folders. Defaults to a sibling of
    # the repo so dumps never land inside git.
    [string]$OutRoot = "",

    # Neo4j logical database to dump. Matches ${NEO4J_DATABASE:neo4j}.
    [string]$Neo4jDatabase = "neo4j",

    [switch]$SkipNeo4j,
    [switch]$SkipUploads,

    # How long to wait for neo4j to report healthy again after the dump.
    [int]$Neo4jRestartTimeoutSec = 180
)

$ErrorActionPreference = "Stop"
$startedAt = Get-Date

if ([string]::IsNullOrWhiteSpace($OutRoot)) {
    $OutRoot = Join-Path (Split-Path -Parent $Root) "vibegraph-backups"
}

function Fail([string]$message) {
    Write-Host "FAIL: $message" -ForegroundColor Red
    exit 1
}

function Assert-LastExit([string]$what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what (exit $LASTEXITCODE)" }
}

function ConvertTo-DockerPath([string]$path) {
    # Docker Desktop accepts forward-slashed Windows paths for -v.
    return ((Resolve-Path -LiteralPath $path).Path -replace '\\', '/')
}

function Get-ServiceContainerId([string]$service) {
    $id = docker compose ps -q $service 2>$null | Where-Object { $_ } | Select-Object -First 1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($id)) {
        Fail "compose service '$service' has no container. Start the stack first: docker compose up -d"
    }
    return $id.Trim()
}

function Assert-Healthy([string]$service) {
    $id = Get-ServiceContainerId $service
    $raw = docker inspect $id --format '{{json .State}}' 2>$null
    Assert-LastExit "docker inspect $service"
    $state = $raw | ConvertFrom-Json
    if ($state.Status -ne 'running') {
        Fail "service '$service' is '$($state.Status)', not running. Refusing to back up a stopped store."
    }
    $health = if ($state.Health) { $state.Health.Status } else { 'none' }
    if ($health -ne 'healthy' -and $health -ne 'none') {
        Fail "service '$service' health is '$health'. Refusing to back up an unhealthy store."
    }
    Write-Host "    $service : running / $health"
    return $id
}

function Get-MountVolumeName([string]$containerId, [string]$destination) {
    $mounts = docker inspect $containerId --format '{{json .Mounts}}' 2>$null | ConvertFrom-Json
    Assert-LastExit "docker inspect mounts"
    $match = $mounts | Where-Object { $_.Destination -eq $destination -and $_.Type -eq 'volume' } | Select-Object -First 1
    if (-not $match) { Fail "no named volume mounted at '$destination' on container $containerId" }
    return $match.Name
}

function Get-ContainerImage([string]$containerId) {
    $image = docker inspect $containerId --format '{{.Config.Image}}' 2>$null
    Assert-LastExit "docker inspect image"
    return $image.Trim()
}

function Wait-Healthy([string]$service, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $id = docker compose ps -q $service 2>$null | Where-Object { $_ } | Select-Object -First 1
        if ($id) {
            $raw = docker inspect $id.Trim() --format '{{json .State}}' 2>$null
            if ($LASTEXITCODE -eq 0) {
                $state = $raw | ConvertFrom-Json
                $health = if ($state.Health) { $state.Health.Status } else { 'none' }
                if ($state.Status -eq 'running' -and ($health -eq 'healthy' -or $health -eq 'none')) { return $true }
            }
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

Push-Location $Root
try {
    if (-not (Test-Path (Join-Path $Root 'docker-compose.yml'))) {
        Fail "docker-compose.yml not found under '$Root'. Pass -Root <repo root>."
    }

    docker version --format '{{.Server.Version}}' | Out-Null
    Assert-LastExit "docker is not reachable"

    # ---------------------------------------------------------------- preflight
    Write-Host "==> [1/5] preflight" -ForegroundColor Cyan
    $pgId = Assert-Healthy 'postgres'
    $pgVolume = Get-MountVolumeName $pgId '/var/lib/postgresql/data'
    $pgImage = Get-ContainerImage $pgId
    $composeProject = (docker inspect $pgId --format '{{index .Config.Labels "com.docker.compose.project"}}').Trim()

    $neoId = $null; $neoVolume = $null; $neoImage = $null
    if (-not $SkipNeo4j) {
        $neoId = Assert-Healthy 'neo4j'
        $neoVolume = Get-MountVolumeName $neoId '/data'
        $neoImage = Get-ContainerImage $neoId
    }

    $uploadVolume = $null
    if (-not $SkipUploads) {
        $backendId = Get-ServiceContainerId 'backend'
        $uploadVolume = Get-MountVolumeName $backendId '/uploads'
    }

    Write-Host "    compose project : $composeProject"
    Write-Host "    volumes         : $pgVolume$(if ($neoVolume) { ", $neoVolume" })$(if ($uploadVolume) { ", $uploadVolume" })"

    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') + 'Z'
    $outDir = Join-Path $OutRoot $stamp
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
    Write-Host "    output          : $outDir"

    $manifest = [ordered]@{
        created_utc     = (Get-Date).ToUniversalTime().ToString('o')
        compose_project = $composeProject
        repo_root       = $Root
        volumes         = [ordered]@{
            postgres = $pgVolume
            neo4j    = $neoVolume
            uploads  = $uploadVolume
        }
        images          = [ordered]@{ postgres = $pgImage; neo4j = $neoImage }
        artifacts       = [ordered]@{}
        row_counts      = [ordered]@{}
        graph_counts    = [ordered]@{}
    }

    # ------------------------------------------------- postgres row-count baseline
    # These three numbers ARE the restore-drill acceptance criteria (Đ7-2c).
    # Table is `projects`, not `project_ownership`: entity ProjectOwnership maps to
    # @Table(name = "projects") in auth/domain/ProjectOwnership.java.
    Write-Host "==> [2/5] postgres baseline counts" -ForegroundColor Cyan
    $countSql = 'SELECT (SELECT count(*) FROM users) || ''|'' || (SELECT count(*) FROM projects) || ''|'' || (SELECT count(*) FROM api_keys)'
    $counts = docker compose exec -T postgres sh -c "psql -v ON_ERROR_STOP=1 -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -tAc `"$countSql`""
    Assert-LastExit "counting rows in postgres"
    $parts = ($counts | Where-Object { $_ -match '\d' } | Select-Object -First 1).Trim() -split '\|'
    if ($parts.Count -ne 3) { Fail "unexpected count output from psql" }
    $manifest.row_counts.users = [int]$parts[0]
    $manifest.row_counts.projects = [int]$parts[1]
    $manifest.row_counts.api_keys = [int]$parts[2]
    Write-Host "    users=$($parts[0])  projects=$($parts[1])  api_keys=$($parts[2])"

    # ------------------------------------------------------------ postgres dump
    # Written inside the container then copied out, so PowerShell never touches
    # the byte stream (a `>` redirect would re-encode it).
    Write-Host "==> [3/5] pg_dump (online, no downtime)" -ForegroundColor Cyan
    $pgDumpName = 'postgres.sql'
    $pgDumpPath = Join-Path $outDir $pgDumpName
    docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=plain --no-owner --no-privileges --file=/tmp/vg-backup.sql'
    Assert-LastExit "pg_dump"
    docker cp "${pgId}:/tmp/vg-backup.sql" $pgDumpPath | Out-Null
    Assert-LastExit "copying pg_dump output out of the container"
    docker compose exec -T postgres rm -f /tmp/vg-backup.sql | Out-Null

    if (-not (Test-Path $pgDumpPath)) { Fail "pg_dump produced no file" }
    $pgBytes = (Get-Item $pgDumpPath).Length
    if ($pgBytes -le 0) { Fail "pg_dump output is empty" }
    $createTables = (Select-String -LiteralPath $pgDumpPath -Pattern '^CREATE TABLE' -AllMatches).Count
    Write-Host "    $pgDumpName : $([math]::Round($pgBytes / 1MB, 2)) MB, $createTables CREATE TABLE statements"
    if ($createTables -lt 19) {
        Fail "only $createTables CREATE TABLE statements; expected at least 19 (one per SQL migration in src/main/resources/db/migration). Dump looks truncated."
    }
    $manifest.artifacts.postgres = [ordered]@{
        file          = $pgDumpName
        bytes         = $pgBytes
        create_tables = $createTables
        sha256        = (Get-FileHash -LiteralPath $pgDumpPath -Algorithm SHA256).Hash
    }

    # --------------------------------------------------------------- neo4j dump
    Write-Host "==> [4/5] neo4j dump" -ForegroundColor Cyan
    if ($SkipNeo4j) {
        Write-Host "    skipped (-SkipNeo4j). Graph data is rebuildable by re-analyzing projects."
    }
    else {
        # Record the node count first, while the server is still up, so the
        # restore drill (Đ7-2d) has a number to match.
        try {
            $graph = docker compose exec -T neo4j sh -c 'cypher-shell -u "${NEO4J_AUTH%%/*}" -p "${NEO4J_AUTH#*/}" --format plain "MATCH (n:Symbol) RETURN count(n) AS symbols"'
            $symbols = ($graph | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
            if ($symbols) {
                $manifest.graph_counts.symbols = [int]$symbols
                Write-Host "    Symbol nodes before dump: $symbols"
            }
        }
        catch {
            Write-Host "    WARN: could not read Symbol count ($($_.Exception.Message)). Continuing." -ForegroundColor Yellow
        }

        $dumpFile = "$Neo4jDatabase.dump"
        Write-Host "    stopping neo4j (a mounted database cannot be dumped)..."
        docker compose stop neo4j | Out-Null
        Assert-LastExit "docker compose stop neo4j"
        try {
            docker run --rm `
                -v "${neoVolume}:/data" `
                -v "$(ConvertTo-DockerPath $outDir):/backups" `
                $neoImage `
                neo4j-admin database dump $Neo4jDatabase --to-path=/backups --overwrite-destination | Out-Null
            Assert-LastExit "neo4j-admin database dump"
        }
        finally {
            Write-Host "    starting neo4j back up..."
            docker compose start neo4j | Out-Null
        }

        $dumpPath = Join-Path $outDir $dumpFile
        if (-not (Test-Path $dumpPath)) { Fail "neo4j-admin produced no $dumpFile" }
        $neoBytes = (Get-Item $dumpPath).Length
        Write-Host "    $dumpFile : $([math]::Round($neoBytes / 1MB, 2)) MB"
        $manifest.artifacts.neo4j = [ordered]@{
            file     = $dumpFile
            database = $Neo4jDatabase
            bytes    = $neoBytes
            sha256   = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
        }

        if (-not (Wait-Healthy 'neo4j' $Neo4jRestartTimeoutSec)) {
            Write-Host "    WARN: neo4j did not report healthy within ${Neo4jRestartTimeoutSec}s. Check: docker compose logs neo4j" -ForegroundColor Yellow
        }
        else {
            Write-Host "    neo4j healthy again."
        }
    }

    # ------------------------------------------------------------- uploads tar
    Write-Host "==> [5/5] upload-workspaces archive" -ForegroundColor Cyan
    if ($SkipUploads) {
        Write-Host "    skipped (-SkipUploads)."
    }
    else {
        $tarName = 'upload-workspaces.tar.gz'
        # Reuse the postgres image purely for its busybox tar, so no extra pull.
        docker run --rm `
            -v "${uploadVolume}:/uploads:ro" `
            -v "$(ConvertTo-DockerPath $outDir):/backup" `
            $pgImage `
            sh -c "tar czf /backup/$tarName -C /uploads ." | Out-Null
        Assert-LastExit "tarring $uploadVolume"
        $tarPath = Join-Path $outDir $tarName
        if (-not (Test-Path $tarPath)) { Fail "tar produced no $tarName" }
        $tarBytes = (Get-Item $tarPath).Length
        Write-Host "    $tarName : $([math]::Round($tarBytes / 1MB, 2)) MB"
        $manifest.artifacts.uploads = [ordered]@{
            file   = $tarName
            bytes  = $tarBytes
            sha256 = (Get-FileHash -LiteralPath $tarPath -Algorithm SHA256).Hash
        }
    }

    $elapsed = ((Get-Date) - $startedAt).TotalSeconds
    $manifest.backup_wall_clock_sec = [math]::Round($elapsed, 1)
    $manifestPath = Join-Path $outDir 'manifest.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    Write-Host ""
    Write-Host "BACKUP OK  ($([math]::Round($elapsed, 1))s)" -ForegroundColor Green
    Write-Host "  $outDir"
    Get-ChildItem -LiteralPath $outDir | ForEach-Object {
        Write-Host ("    {0,-32} {1,10:N2} MB" -f $_.Name, ($_.Length / 1MB))
    }
    Write-Host ""
    Write-Host "  These files contain password hashes and API key hashes. Keep them out of git" -ForegroundColor Yellow
    Write-Host "  and off shared drives. Rehearse the restore before you need it:" -ForegroundColor Yellow
    Write-Host "    powershell -ExecutionPolicy Bypass -File scripts/restore.ps1 -BackupDir `"$outDir`" -DryRun"
}
finally {
    Pop-Location
}
