<#
.SYNOPSIS
    End-to-end rehearsal of the Supabase backfill SQL against a real PostgreSQL container.

.DESCRIPTION
    backfill-supabase-realtime.Tests.ps1 covers everything decidable without a database. This
    script closes the remaining gap: it executes the SQL that script GENERATES - the import
    transaction, the canonical checksum queries, the semantic verification and the Realtime
    publication gate - against a real PostgreSQL instance, using the pg_dump and psql that ship
    inside the postgres image.

    Only Docker is required. No PostgreSQL client tools are needed on the host, and nothing
    connects to a real Supabase project.

    What this rehearsal does NOT cover, because it runs the client tools inside the container
    rather than from the host:
      * host-side PGPASSFILE handling and ACLs (covered by the unit tests)
      * host-side process argument construction (covered by the unit tests)
      * network latency and managed-instance limits of a real Supabase project

    A staging rehearsal with the real script is still required before a production cutover.

.EXAMPLE
    .\scripts\tests\backfill-rehearsal.ps1
#>
[CmdletBinding()]
param(
    [string]$Image = 'postgres:16-alpine',
    [string]$SourceSchema = 'public',
    [string]$TargetSchema = 'vibegraph_realtime'
)

$ErrorActionPreference = 'Stop'

$script:Passed = 0
$script:Failed = 0
$script:Failures = @()

function Test-Case {
    param([string]$Name, [scriptblock]$Body)
    try {
        & $Body
        $script:Passed++
        Write-Host ("  PASS  {0}" -f $Name)
    }
    catch {
        $script:Failed++
        $script:Failures += ("{0}: {1}" -f $Name, $_.Exception.Message)
        Write-Host ("  FAIL  {0} -- {1}" -f $Name, $_.Exception.Message)
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ("$Expected" -ne "$Actual") {
        throw ("{0} (expected '{1}', got '{2}')" -f $Message, $Expected, $Actual)
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'SKIP: docker is not available; the rehearsal needs a PostgreSQL container.'
    exit 0
}

$scriptPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSCommandPath)) 'backfill-supabase-realtime.ps1'
. $scriptPath -LoadFunctionsOnly

$container = 'vibegraph-backfill-rehearsal-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
# Container-local throwaway credential for an ephemeral database. Not a real secret.
$password = 'rehearsal-container-local'
# Two databases, exactly like a real cutover: the primary control plane and the Supabase project.
# Using one database for both would make the target's public schema the source schema too, which is
# not how the import stages rows.
$sourceDatabase = 'vibegraph_source'
$targetDatabase = 'vibegraph_target'

# Windows PowerShell 5.1 wraps a native command's stderr in ErrorRecords, so a harmless psql
# NOTICE becomes a terminating error under $ErrorActionPreference = 'Stop'. Native calls therefore
# run with 'Continue' and are judged by $LASTEXITCODE alone.
function Invoke-Native {
    param([scriptblock]$Body)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Body
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function Invoke-ContainerPsql {
    param([string]$Database = $targetDatabase, [string]$Statement, [switch]$Quiet)
    $output = Invoke-Native {
        docker exec -e PGPASSWORD=$password $container psql --username=postgres `
            --dbname=$Database --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 `
            --command $Statement 2>&1
    }
    if ($LASTEXITCODE -ne 0) {
        throw ("psql failed: {0}" -f (($output | ForEach-Object { "$_" }) -join ' '))
    }
    if (-not $Quiet) {
        return (($output | ForEach-Object { "$_" }) -join "`n").Trim()
    }
}

function Invoke-ContainerPsqlFile {
    param([string]$Database = $targetDatabase, [string]$ContainerPath, [switch]$SingleTransaction)
    $arguments = @('--username=postgres', "--dbname=$Database", '--quiet',
        '--set=ON_ERROR_STOP=1', "--file=$ContainerPath")
    if ($SingleTransaction) {
        $arguments += '--single-transaction'
    }
    $output = Invoke-Native { docker exec -e PGPASSWORD=$password $container psql @arguments 2>&1 }
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = (($output | ForEach-Object { "$_" }) -join "`n")
    }
}

function Export-CanonicalToContainerFile {
    param([string]$Database = $targetDatabase, [string]$Statement, [string]$ContainerPath)
    Invoke-Native {
        docker exec -e PGPASSWORD=$password $container psql --username=postgres --dbname=$Database `
            --no-align --tuples-only --quiet --set=ON_ERROR_STOP=1 "--output=$ContainerPath" `
            --command $Statement 2>&1
    } | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "canonical export failed writing $ContainerPath"
    }
}

function Get-ContainerFileHash {
    param([string]$ContainerPath)
    $output = Invoke-Native { docker exec $container sh -c "sha256sum $ContainerPath" 2>&1 }
    if ($LASTEXITCODE -ne 0) {
        throw "sha256sum failed for $ContainerPath"
    }
    return ((($output | ForEach-Object { "$_" }) -join ' ') -split '\s+')[0]
}

function Copy-TextToContainer {
    param([string]$Text, [string]$ContainerPath)
    $local = Join-Path ([System.IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString('N') + '.sql')
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($local, $Text, $encoding)
    try {
        Invoke-Native { docker cp $local ("{0}:{1}" -f $container, $ContainerPath) } | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'docker cp failed' }
    }
    finally {
        Remove-Item -LiteralPath $local -Force -ErrorAction SilentlyContinue
    }
}

Write-Host 'backfill rehearsal (real PostgreSQL container)'

try {
    Invoke-Native {
        docker run --rm --detach --name $container -e POSTGRES_PASSWORD=$password `
            -e POSTGRES_DB=$sourceDatabase $Image
    } | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'could not start the PostgreSQL container' }

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Invoke-Native { docker exec $container pg_isready --username=postgres --dbname=$sourceDatabase 2>&1 } | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    Assert-True $ready 'the PostgreSQL container never became ready'

    # ---- Schemas -------------------------------------------------------------
    # Source database mirrors the primary control plane (tables in public); target database mirrors
    # the Supabase project (tables in vibegraph_realtime, empty public).
    $repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
    $supabaseMigration = Join-Path (Split-Path -Parent $repositoryRoot) 'src/main/resources/db/supabase/V1__init_realtime_storage.sql'
    Assert-True (Test-Path -LiteralPath $supabaseMigration) "Supabase migration not found at $supabaseMigration"
    $migrationSql = Get-Content -LiteralPath $supabaseMigration -Raw

    Invoke-ContainerPsql -Database $sourceDatabase -Quiet -Statement "CREATE DATABASE $targetDatabase"

    Copy-TextToContainer -Text ("CREATE SCHEMA IF NOT EXISTS $TargetSchema;`nSET search_path TO $TargetSchema;`n" + $migrationSql) `
        -ContainerPath '/tmp/target-schema.sql'
    $applied = Invoke-ContainerPsqlFile -Database $targetDatabase -ContainerPath '/tmp/target-schema.sql'
    Assert-Equal 0 $applied.ExitCode ("target schema migration failed: " + $applied.Output)

    # The source uses the same table shapes; that is what makes the column lists comparable.
    Copy-TextToContainer -Text ("SET search_path TO $SourceSchema;`n" + $migrationSql) -ContainerPath '/tmp/source-schema.sql'
    $appliedSource = Invoke-ContainerPsqlFile -Database $sourceDatabase -ContainerPath '/tmp/source-schema.sql'
    Assert-Equal 0 $appliedSource.ExitCode ("source schema migration failed: " + $appliedSource.Output)

    Test-Case 'target public schema is free of colliding staging table names' {
        # This is the precondition the real script enforces in preflight before it stages rows.
        foreach ($table in $script:BackfillTables) {
            $collision = Invoke-ContainerPsql -Database $targetDatabase -Statement (
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = '$($table.Name)'")
            Assert-Equal 0 $collision ("target public schema already holds " + $table.Name)
        }
    }

    # ---- Seed representative source data ------------------------------------
    # Includes the awkward cases: NULL columns, unicode, embedded quotes and a body containing
    # text that looks like a dump statement, which is exactly what naive text rewriting corrupts.
    $seed = @"
SET search_path TO $SourceSchema;
INSERT INTO announcements (id, type, severity, target, title, body, dismissible, active, created_by_user_id)
VALUES ('11111111-1111-4111-8111-111111111111', 'GENERAL', 'INFO', 'ALL', 'Planned maintenance',
        'Body with a quote '' and unicode ăâđêô', true, true, NULL);
INSERT INTO feedback_reports (id, user_id, status, category, title)
VALUES ('22222222-2222-4222-8222-222222222222', NULL, 'OPEN', 'BUG', 'Report one');
INSERT INTO feedback_messages (id, report_id, sender_user_id, sender_role, body)
VALUES ('33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', NULL, 'USER',
        E'multi\nline\nINSERT INTO public.feedback_reports (id) VALUES (''injected'');');
INSERT INTO request_events (id, user_id, api_key_ref, ip_address, route, http_method, status, event_type)
VALUES ('44444444-4444-4444-8444-444444444444', NULL, NULL, '203.0.113.10', '/api/projects/{id}', 'GET', 200, 'REQUEST');
INSERT INTO security_events (id, event_type, severity, subject_user_id, api_key_ref, source, description)
VALUES ('55555555-5555-4555-8555-555555555555', 'RATE_LIMIT', 'WARNING', NULL, NULL, 'HTTP', 'Request rate limit exceeded');
-- One read, one dismissed, one unread. Only the first two may reach the target.
INSERT INTO user_notifications (id, user_id, announcement_id, read_at, dismissed_at)
VALUES ('66666666-6666-4666-8666-666666666666', '99999999-9999-4999-8999-999999999999',
        '11111111-1111-4111-8111-111111111111', now(), NULL),
       ('77777777-7777-4777-8777-777777777777', '88888888-8888-4888-8888-888888888888',
        '11111111-1111-4111-8111-111111111111', NULL, now()),
       ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        '11111111-1111-4111-8111-111111111111', NULL, NULL);
"@
    Copy-TextToContainer -Text $seed -ContainerPath '/tmp/seed.sql'
    $seeded = Invoke-ContainerPsqlFile -Database $sourceDatabase -ContainerPath '/tmp/seed.sql'
    Assert-Equal 0 $seeded.ExitCode ("seeding failed: " + $seeded.Output)

    # ---- Dump: one invocation, six tables, exactly as the script builds it ----
    $dumpArguments = Get-DumpArguments -HostName 'localhost' -Port 5432 -Database $sourceDatabase `
        -UserName 'postgres' -Schema $SourceSchema -OutputFile '/tmp/dump.sql'
    Test-Case 'one pg_dump invocation exports all six tables' {
        $output = Invoke-Native { docker exec -e PGPASSWORD=$password $container pg_dump @dumpArguments 2>&1 }
        Assert-Equal 0 $LASTEXITCODE ("pg_dump failed: " + (($output | ForEach-Object { "$_" }) -join ' '))
        $lines = Invoke-Native { docker exec $container sh -c "grep -c 'INSERT INTO' /tmp/dump.sql" }
        Assert-True ([int]$lines -ge 8) "expected the dump to contain the seeded rows, got $lines INSERT statements"
    }

    # ---- Import: the real generated script, in one transaction ---------------
    $importScript = Get-ImportScript -Schema $TargetSchema -DumpFile '/tmp/dump.sql'
    Copy-TextToContainer -Text $importScript -ContainerPath '/tmp/import.sql'

    Test-Case 'generated import script applies in a single transaction' {
        $result = Invoke-ContainerPsqlFile -ContainerPath '/tmp/import.sql' -SingleTransaction
        Assert-Equal 0 $result.ExitCode ("import failed: " + $result.Output)
    }

    Test-Case 'staging tables are dropped and the source database is untouched' {
        foreach ($table in $script:BackfillTables) {
            $staging = Invoke-ContainerPsql -Statement (
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = '$($table.Name)'")
            Assert-Equal 0 $staging ("staging table left behind in the target: " + $table.Name)
        }
        $sourceRows = Invoke-ContainerPsql -Database $sourceDatabase `
            -Statement "SELECT count(*) FROM $SourceSchema.request_events"
        Assert-Equal 1 $sourceRows 'the source table must not be modified'
    }

    Test-Case 'row text containing SQL is imported verbatim, not rewritten' {
        $body = Invoke-ContainerPsql -Statement (
            "SELECT body FROM $TargetSchema.feedback_messages WHERE id = '33333333-3333-4333-8333-333333333333'")
        Assert-True ($body -match 'INSERT INTO public\.feedback_reports') 'the embedded statement text must survive'
        $reports = Invoke-ContainerPsql -Statement "SELECT count(*) FROM $TargetSchema.feedback_reports"
        Assert-Equal 1 $reports 'the embedded statement must not have created a row'
    }

    Test-Case 'unread notifications are excluded by the transformation' {
        $targetCount = Invoke-ContainerPsql -Statement "SELECT count(*) FROM $TargetSchema.user_notifications"
        Assert-Equal 2 $targetCount 'only read or dismissed rows may be migrated'
        $unread = Invoke-ContainerPsql -Statement (
            "SELECT count(*) FROM $TargetSchema.user_notifications WHERE read_at IS NULL AND dismissed_at IS NULL")
        Assert-Equal 0 $unread 'no unread row may reach the target'
    }

    Test-Case 'semantic counts match on every table' {
        foreach ($table in $script:BackfillTables) {
            $sourceStatement = Get-CountStatement -Table $table -Schema $SourceSchema -ApplyStatefulFilter
            $targetStatement = Get-CountStatement -Table $table -Schema $TargetSchema
            $sourceCount = Invoke-ContainerPsql -Database $sourceDatabase -Statement $sourceStatement
            $targetCount = Invoke-ContainerPsql -Statement $targetStatement
            Assert-Equal $sourceCount $targetCount ("count mismatch on " + $table.Name)
        }
    }

    Test-Case 'canonical checksums match on every table' {
        foreach ($table in $script:BackfillTables) {
            Export-CanonicalToContainerFile -Database $sourceDatabase -ContainerPath '/tmp/source.csv' `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $SourceSchema)
            Export-CanonicalToContainerFile -ContainerPath '/tmp/target.csv' `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $TargetSchema)

            $sourceHash = Get-ContainerFileHash -ContainerPath '/tmp/source.csv'
            $targetHash = Get-ContainerFileHash -ContainerPath '/tmp/target.csv'
            Assert-True ($sourceHash -match '^[0-9a-f]{64}$') ("no source checksum for " + $table.Name)
            Assert-Equal $sourceHash $targetHash ("canonical checksum mismatch on " + $table.Name)
        }
    }

    Test-Case 'a divergent target row is detected by the checksum' {
        Invoke-ContainerPsql -Quiet -Statement (
            "UPDATE $TargetSchema.request_events SET status = 500 WHERE id = '44444444-4444-4444-8444-444444444444'")
        try {
            $table = $script:BackfillTables | Where-Object { $_.Name -eq 'request_events' }
            Export-CanonicalToContainerFile -Database $sourceDatabase -ContainerPath '/tmp/source.csv' `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $SourceSchema)
            Export-CanonicalToContainerFile -ContainerPath '/tmp/target.csv' `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $TargetSchema)
            $sourceHash = Get-ContainerFileHash -ContainerPath '/tmp/source.csv'
            $targetHash = Get-ContainerFileHash -ContainerPath '/tmp/target.csv'
            Assert-True ($sourceHash -ne $targetHash) 'the checksum must catch a diverged row'
        }
        finally {
            Invoke-ContainerPsql -Quiet -Statement (
                "UPDATE $TargetSchema.request_events SET status = 200 WHERE id = '44444444-4444-4444-8444-444444444444'")
        }
    }

    Test-Case 'foreign-key coverage holds in the target' {
        $orphanMessages = Invoke-ContainerPsql -Statement (
            "SELECT count(*) FROM ""$TargetSchema"".""feedback_messages"" m LEFT JOIN ""$TargetSchema"".""feedback_reports"" r ON r.""id"" = m.""report_id"" WHERE r.""id"" IS NULL")
        Assert-Equal 0 $orphanMessages 'feedback_messages must not be orphaned'
        $orphanNotifications = Invoke-ContainerPsql -Statement (
            "SELECT count(*) FROM ""$TargetSchema"".""user_notifications"" n LEFT JOIN ""$TargetSchema"".""announcements"" a ON a.""id"" = n.""announcement_id"" WHERE a.""id"" IS NULL")
        Assert-Equal 0 $orphanNotifications 'user_notifications must not be orphaned'
    }

    Test-Case 'a second import is idempotent (resume path)' {
        $result = Invoke-ContainerPsqlFile -ContainerPath '/tmp/import.sql' -SingleTransaction
        Assert-Equal 0 $result.ExitCode ("second import failed: " + $result.Output)
        $requestEvents = Invoke-ContainerPsql -Statement "SELECT count(*) FROM $TargetSchema.request_events"
        Assert-Equal 1 $requestEvents 'replaying the import must not duplicate rows'
        $notifications = Invoke-ContainerPsql -Statement "SELECT count(*) FROM $TargetSchema.user_notifications"
        Assert-Equal 2 $notifications 'replaying the import must not duplicate notifications'
    }

    Test-Case 'a failing import rolls back completely' {
        $broken = $importScript + "`nINSERT INTO $TargetSchema.request_events (id, ip_address, route, http_method, status, event_type) VALUES ('44444444-4444-4444-8444-444444444444', NULL, NULL, NULL, NULL, NULL);"
        Copy-TextToContainer -Text $broken -ContainerPath '/tmp/import-broken.sql'
        $result = Invoke-ContainerPsqlFile -ContainerPath '/tmp/import-broken.sql' -SingleTransaction
        Assert-True ($result.ExitCode -ne 0) 'a broken import must fail'
        $leftover = Invoke-ContainerPsql -Statement (
            "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = 'announcements'")
        Assert-Equal 0 $leftover 'a rolled back import must not leave staging tables behind'
        $requestEvents = Invoke-ContainerPsql -Statement "SELECT count(*) FROM $TargetSchema.request_events"
        Assert-Equal 1 $requestEvents 'a rolled back import must not change the target'
    }

    Test-Case 'realtime publication gate passes when telemetry is not published' {
        Invoke-ContainerPsql -Quiet -Statement "CREATE PUBLICATION supabase_realtime FOR TABLE $TargetSchema.announcements"
        $published = Invoke-ContainerPsql -Statement (Get-RealtimePublicationStatement)
        Assert-Equal '' $published 'no telemetry table may be published'
    }

    Test-Case 'realtime publication gate fails when telemetry is published' {
        Invoke-ContainerPsql -Quiet -Statement "ALTER PUBLICATION supabase_realtime ADD TABLE $TargetSchema.request_events"
        try {
            $published = Invoke-ContainerPsql -Statement (Get-RealtimePublicationStatement)
            Assert-True ($published -match 'request_events') 'the gate must report the published telemetry table'
        }
        finally {
            Invoke-ContainerPsql -Quiet -Statement "ALTER PUBLICATION supabase_realtime DROP TABLE $TargetSchema.request_events"
        }
    }
}
finally {
    Invoke-Native { docker rm --force $container 2>&1 } | Out-Null
}

Write-Host ''
Write-Host ("Passed: {0}  Failed: {1}" -f $script:Passed, $script:Failed)
if ($script:Failed -gt 0) {
    $script:Failures | ForEach-Object { Write-Host ("  - {0}" -f $_) }
    exit 1
}
exit 0
