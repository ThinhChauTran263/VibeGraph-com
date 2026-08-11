<#
.SYNOPSIS
    Self-contained checks for scripts/backfill-supabase-realtime.ps1.

.DESCRIPTION
    Runs without Pester and without a database. It covers everything that can be decided from the
    script itself: the maintenance gate, argument construction, credential handling, canonical SQL
    generation, manifest compatibility and temporary-file cleanup.

    The parts that genuinely need two PostgreSQL instances - the dump, the import transaction and
    the live pg_publication_tables gate - are exercised during a rehearsal cutover against a staging
    pair, not here.

.EXAMPLE
    powershell -NoProfile -File .\scripts\tests\backfill-supabase-realtime.Tests.ps1
#>
[CmdletBinding()]
param()

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
    if ($Expected -ne $Actual) { throw ("{0} (expected '{1}', got '{2}')" -f $Message, $Expected, $Actual) }
}

function Assert-Throws {
    param([scriptblock]$Body, [string]$Message)
    try {
        & $Body
    }
    catch {
        return $_
    }
    throw $Message
}

$scriptPath = Join-Path (Split-Path -Parent (Split-Path -Parent $PSCommandPath)) 'backfill-supabase-realtime.ps1'
Assert-True (Test-Path -LiteralPath $scriptPath) "backfill script not found at $scriptPath"

Write-Host 'backfill-supabase-realtime.ps1'

Test-Case 'script parses without syntax errors' {
    $errors = $null
    $tokens = $null
    [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors) | Out-Null
    Assert-True ($null -eq $errors -or $errors.Count -eq 0) 'parser reported errors'
}

# Loading the functions must not run the backfill.
. $scriptPath -LoadFunctionsOnly

Test-Case 'maintenance confirmation is required and fails closed' {
    $error1 = Assert-Throws { Assert-MaintenanceConfirmed -WritersArePaused $false } 'expected a failure without confirmation'
    Assert-True ($error1.Exception.Message -match 'WritersArePaused') 'error should name the confirmation switch'
    Assert-True ($error1.Exception.Message -match 'write gap') 'error should explain the write gap'
    Assert-True (Assert-MaintenanceConfirmed -WritersArePaused $true) 'confirmation should be accepted'
}

Test-Case 'connection identifiers are validated' {
    Assert-Throws { Assert-ValidIdentifier -Value 'public; DROP SCHEMA x' -Label 'schema' } 'expected identifier rejection' | Out-Null
    Assert-Throws { Assert-ValidIdentifier -Value '' -Label 'schema' } 'expected empty rejection' | Out-Null
    Assert-Throws { Assert-ValidHost -Value 'db.internal;rm -rf /' -Label 'host' } 'expected host rejection' | Out-Null
    Assert-Throws { Assert-ValidPort -Value 0 -Label 'port' } 'expected port rejection' | Out-Null
    Assert-Equal 'vibegraph_realtime' (Assert-ValidIdentifier -Value 'vibegraph_realtime' -Label 'schema') 'valid identifier'
    # A schema or database still has to be a plain identifier: those DO reach generated SQL.
    Assert-Throws { Assert-ValidIdentifier -Value 'postgres.projectref' -Label 'schema' } 'expected dotted schema rejection' | Out-Null
    Assert-Equal 'db.internal' (Assert-ValidHost -Value 'db.internal' -Label 'host') 'valid host'
}

Test-Case 'connection users accept the Supabase session pooler form' {
    # The session pooler requires <role>.<project-ref>; a bare role name only works on the direct
    # connection, which is IPv6-only on Supabase.
    Assert-Equal 'postgres.exampleprojectref' `
        (Assert-ValidRoleName -Value 'postgres.exampleprojectref' -Label 'Target user') 'pooler-qualified user'
    Assert-Equal 'vibegraph_runtime' (Assert-ValidRoleName -Value 'vibegraph_runtime' -Label 'user') 'plain role'

    Assert-Throws { Assert-ValidRoleName -Value '' -Label 'user' } 'expected empty rejection' | Out-Null
    Assert-Throws { Assert-ValidRoleName -Value 'postgres.a.b' -Label 'user' } 'expected multi-dot rejection' | Out-Null
    Assert-Throws { Assert-ValidRoleName -Value 'postgres;DROP ROLE x' -Label 'user' } 'expected injection rejection' | Out-Null
    Assert-Throws { Assert-ValidRoleName -Value 'post gres' -Label 'user' } 'expected whitespace rejection' | Out-Null
    Assert-Throws { Assert-ValidRoleName -Value 'postgres:pass' -Label 'user' } 'expected pgpass separator rejection' | Out-Null
}

Test-Case 'one pg_dump invocation carries all six table arguments' {
    $arguments = Get-DumpArguments -HostName 'db.internal' -Port 5432 -Database 'vibegraph' `
        -UserName 'migrator' -Schema 'public' -OutputFile 'C:\temp\dump.sql'
    $tableArguments = @($arguments | Where-Object { $_ -like '--table=*' })
    Assert-Equal 6 $tableArguments.Count 'expected six --table arguments'
    foreach ($name in @('feedback_reports', 'feedback_messages', 'request_events',
            'security_events', 'announcements', 'user_notifications')) {
        Assert-True ($tableArguments -contains "--table=public.$name") "missing --table for $name"
    }
    Assert-True ($arguments -contains '--data-only') 'dump must be data-only so the source schema is untouched'
    Assert-True ($arguments -contains '--no-password') 'dump must never prompt for or accept a command-line password'
}

Test-Case 'no password is passed as a native process argument' {
    # Synthetic fixture, not a credential: used only to assert it never reaches an argument list.
    $password = 'FIXTURE-NOT-A-REAL-CREDENTIAL'
    $dumpArguments = Get-DumpArguments -HostName 'db.internal' -Port 5432 -Database 'vibegraph' `
        -UserName 'migrator' -Schema 'public' -OutputFile 'C:\temp\dump.sql'
    $psqlArguments = Get-PsqlArguments -HostName 'db.internal' -Port 5432 -Database 'vibegraph' -UserName 'migrator'
    foreach ($argument in ($dumpArguments + $psqlArguments)) {
        Assert-True ($argument -notmatch [regex]::Escape($password)) 'password leaked into arguments'
        # --no-password is the flag that forbids a command-line password; anything else naming a
        # password would mean a credential is being passed as a process argument.
        Assert-True ($argument -eq '--no-password' -or $argument -notmatch '(?i)password') `
            "unexpected password-bearing argument: $argument"
    }
    Assert-True ($dumpArguments -contains '--no-password') 'pg_dump must use --no-password'
    Assert-True ($psqlArguments -contains '--no-password') 'psql must use --no-password'
    Assert-True ($psqlArguments -contains '--set=ON_ERROR_STOP=1') 'psql must stop on the first error'
}

Test-Case 'pgpass fields escape libpq separators' {
    Assert-Equal 'a\:b' (ConvertTo-PgPassField -Value 'a:b') 'colon must be escaped'
    Assert-Equal 'a\\b' (ConvertTo-PgPassField -Value 'a\b') 'backslash must be escaped'
}

Test-Case 'pgpass file is written restrictively and removed on success' {
    $directory = Join-Path ([System.IO.Path]::GetTempPath()) ('backfill-test-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $directory | Out-Null
    try {
        $secure = ConvertTo-SecureString 'pa:ss\word' -AsPlainText -Force
        $file = New-PgPassFile -Directory $directory -FileName 'source.pgpass' -HostName 'db.internal' `
            -Port 5432 -Database 'vibegraph' -UserName 'migrator' -SecurePassword $secure
        Assert-True (Test-Path -LiteralPath $file) 'pgpass file should exist'
        $content = Get-Content -LiteralPath $file -Raw
        Assert-True ($content -match 'db\.internal:5432:vibegraph:migrator:pa\\:ss\\\\word') 'pgpass content should be escaped'
        $acl = Get-Acl -LiteralPath $file
        Assert-True $acl.AreAccessRulesProtected 'pgpass file must not inherit permissions'
        Assert-Equal 1 @($acl.Access).Count 'pgpass file must grant exactly one identity'
        Assert-True (Remove-SensitiveFile -Path $file) 'cleanup should report removal'
        Assert-True (-not (Test-Path -LiteralPath $file)) 'pgpass file must be gone'
    }
    finally {
        Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Test-Case 'pgpass cleanup is safe when the file is already gone (failure path)' {
    $missing = Join-Path ([System.IO.Path]::GetTempPath()) ('never-created-' + [Guid]::NewGuid().ToString('N'))
    Assert-True (-not (Remove-SensitiveFile -Path $missing)) 'cleanup should be a no-op'
    Assert-True (-not (Remove-SensitiveFile -Path $null)) 'cleanup should tolerate a null path'
}

Test-Case 'a missing password fails closed when nobody can be prompted' {
    $previous = [Environment]::GetEnvironmentVariable('VIBEGRAPH_BACKFILL_SOURCE_PASSWORD')
    Remove-Item Env:VIBEGRAPH_BACKFILL_SOURCE_PASSWORD -ErrorAction SilentlyContinue
    # Force the automation path so the assertion holds whether or not a console is attached.
    $script:AllowInteractiveCredentialPrompt = $false
    try {
        Assert-True (-not (Test-InteractiveCredentialPrompt)) 'prompting must be disabled for automation'
        $failure = Assert-Throws {
            Resolve-BackfillCredential -UserName 'migrator' `
                -EnvironmentVariable 'VIBEGRAPH_BACKFILL_SOURCE_PASSWORD' -Label 'Source'
        } 'expected a failure without a password'
        Assert-True ($failure.Exception.Message -match 'VIBEGRAPH_BACKFILL_SOURCE_PASSWORD') 'error should name the variable'
        Assert-True ($failure.Exception.Message -match 'connection URL') 'error should reject URL-embedded passwords'
        Assert-True ($failure.Exception.Message -match 'interactively') 'error should mention the interactive option'
    }
    finally {
        $script:AllowInteractiveCredentialPrompt = $true
        if ($null -ne $previous) { $env:VIBEGRAPH_BACKFILL_SOURCE_PASSWORD = $previous }
    }
}

Test-Case 'an explicit credential never triggers a prompt' {
    $secure = ConvertTo-SecureString 'FIXTURE-NOT-A-REAL-CREDENTIAL' -AsPlainText -Force
    $credential = New-Object System.Management.Automation.PSCredential('migrator', $secure)
    $resolved = Resolve-BackfillCredential -Credential $credential -UserName 'ignored' `
        -EnvironmentVariable 'VIBEGRAPH_BACKFILL_SOURCE_PASSWORD' -Label 'Source'
    Assert-Equal 'migrator' $resolved.UserName 'the credential user wins'
    Assert-True ($null -ne $resolved.SecurePassword) 'the credential password is used'
}

Test-Case 'canonical export streams and never aggregates a whole table' {
    foreach ($table in $script:BackfillTables) {
        $statement = Get-CanonicalCopyStatement -Table $table -Schema 'public'
        Assert-True ($statement -like 'COPY (SELECT *') 'canonical export must use COPY'
        Assert-True ($statement -match 'TO STDOUT WITH \(FORMAT csv\)') 'canonical export must stream as CSV'
        Assert-True ($statement -notmatch 'string_agg') 'canonical export must not aggregate the table in memory'
        Assert-True ($statement -match 'ORDER BY "id"') 'canonical export must order by the primary key'
        Assert-True ($statement -match "AT TIME ZONE 'UTC'") 'timestamps must be normalized to UTC'
        Assert-True ($statement -match 'YYYY-MM-DD HH24:MI:SS\.US') 'timestamps must use fixed microsecond precision'
        Assert-True ($statement -match '~NULL~') 'nulls must use one documented representation'
    }
    $scriptText = Get-Content -LiteralPath $scriptPath -Raw
    Assert-True ($scriptText -match '--output=\$OutputFile') 'psql must write the canonical stream straight to disk'
}

Test-Case 'source-side statements are read-only' {
    foreach ($table in $script:BackfillTables) {
        foreach ($statement in @(
                (Get-CanonicalCopyStatement -Table $table -Schema 'public'),
                (Get-CountStatement -Table $table -Schema 'public' -ApplyStatefulFilter),
                (Get-TimestampBoundsStatement -Table $table -Schema 'public' -ApplyStatefulFilter))) {
            Assert-True ($statement -notmatch '(?i)\b(insert|update|delete|drop|alter|truncate)\b') `
                "source statement must not modify data: $statement"
        }
    }
}

Test-Case 'unread notifications are excluded identically on both sides' {
    $notifications = $script:BackfillTables | Where-Object { $_.Name -eq 'user_notifications' }
    Assert-True ($null -ne $notifications.StatefulFilter) 'user_notifications needs a stateful filter'

    $sourceCanonical = Get-CanonicalCopyStatement -Table $notifications -Schema 'public'
    Assert-True ($sourceCanonical -match 'WHERE read_at IS NOT NULL OR dismissed_at IS NOT NULL') `
        'the source canonical query must drop unread rows'
    $sourceCount = Get-CountStatement -Table $notifications -Schema 'public' -ApplyStatefulFilter
    Assert-True ($sourceCount -match 'WHERE read_at IS NOT NULL OR dismissed_at IS NOT NULL') `
        'the source count must drop unread rows'

    # The target keeps only stateful rows, so its canonical query needs no extra predicate to match.
    $targetCount = Get-CountStatement -Table $notifications -Schema 'vibegraph_realtime'
    Assert-True ($targetCount -notmatch 'WHERE') 'the target count must not filter again'

    $others = $script:BackfillTables | Where-Object { $_.Name -ne 'user_notifications' }
    foreach ($table in $others) {
        Assert-True ($null -eq $table.StatefulFilter) "$($table.Name) must not be filtered"
    }
}

Test-Case 'import runs in one transaction and never rewrites dumped row text' {
    $importScript = Get-ImportScript -Schema 'vibegraph_realtime' -DumpFile 'C:\temp\dump.sql'
    Assert-True ($importScript -match 'CREATE UNLOGGED TABLE "public"\."feedback_reports"') 'staging tables are created'
    Assert-True ($importScript -match '\\i ''C:/temp/dump\.sql''') 'the dump is included verbatim'
    Assert-True ($importScript -match 'INSERT INTO "vibegraph_realtime"\."feedback_reports"') 'rows move into the target schema'
    Assert-True ($importScript -match 'ON CONFLICT \("id"\) DO NOTHING') 'import stays idempotent for resume'
    Assert-True ($importScript -match 'FROM "public"\."user_notifications" WHERE read_at IS NOT NULL OR dismissed_at IS NOT NULL') `
        'unread notifications are dropped during the import'
    Assert-True ($importScript -match 'DROP TABLE "public"\."security_events";') 'staging tables are dropped'

    # Parent tables must be inserted before their children so the target foreign keys hold.
    $reportsAt = $importScript.IndexOf('INSERT INTO "vibegraph_realtime"."feedback_reports"')
    $messagesAt = $importScript.IndexOf('INSERT INTO "vibegraph_realtime"."feedback_messages"')
    $announcementsAt = $importScript.IndexOf('INSERT INTO "vibegraph_realtime"."announcements"')
    $notificationsAt = $importScript.IndexOf('INSERT INTO "vibegraph_realtime"."user_notifications"')
    Assert-True ($reportsAt -lt $messagesAt) 'feedback_reports must be inserted before feedback_messages'
    Assert-True ($announcementsAt -lt $notificationsAt) 'announcements must be inserted before user_notifications'

    $scriptText = Get-Content -LiteralPath $scriptPath -Raw
    Assert-True ($scriptText -match "'--single-transaction'") 'the import must run in a single transaction'
    Assert-True ($scriptText -notmatch "Replace\('INSERT INTO public") 'dumped row text must never be rewritten'
}

Test-Case 'realtime publication gate reads the live catalog' {
    $statement = Get-RealtimePublicationStatement
    Assert-True ($statement -match 'pg_publication_tables') 'the gate must query the catalog, not the migration SQL'
    Assert-True ($statement -match "pubname = 'supabase_realtime'") 'the gate must check the Supabase publication'
    Assert-True ($statement -match "'request_events'") 'request_events must be checked'
    Assert-True ($statement -match "'security_events'") 'security_events must be checked'
}

Test-Case 'the manifest is canonical and carries no secret' {
    $source = [pscustomobject]@{ HostName = 'db.internal'; Port = 5432; Database = 'vibegraph'; Schema = 'public'; UserName = 'migrator' }
    $target = [pscustomobject]@{ HostName = 'db.project.supabase.co'; Port = 5432; Database = 'postgres'; Schema = 'vibegraph_realtime'; UserName = 'operator' }
    $manifest = New-BackfillManifest -Source $source -Target $target `
        -DumpStartedAt ([datetime]'2026-08-09T00:00:00Z') -DumpCompletedAt ([datetime]'2026-08-09T00:05:00Z')

    Assert-Equal 2 $manifest.manifestVersion 'manifest version'
    Assert-Equal 1 $manifest.transformationVersion 'transformation version'
    Assert-True ($manifest.dumpStartedAt -ne $manifest.dumpCompletedAt) 'dump window must be recorded'
    $json = $manifest | ConvertTo-Json -Depth 8
    Assert-True ($json -notmatch '(?i)password') 'manifest must not contain a password field'
    Assert-True ($json -notmatch '(?i)pgpass') 'manifest must not reference a credential file'
    Assert-True ($json -notmatch 'exact snapshot') 'manifest must not overclaim snapshot semantics'
}

Test-Case 'resume refuses an incompatible manifest' {
    $source = [pscustomobject]@{ HostName = 'db.internal'; Port = 5432; Database = 'vibegraph'; Schema = 'public'; UserName = 'migrator' }
    $target = [pscustomobject]@{ HostName = 'db.project.supabase.co'; Port = 5432; Database = 'postgres'; Schema = 'vibegraph_realtime'; UserName = 'operator' }
    $manifest = New-BackfillManifest -Source $source -Target $target `
        -DumpStartedAt (Get-Date) -DumpCompletedAt (Get-Date)
    $manifest.tables = @($script:BackfillTables | ForEach-Object { [pscustomobject]@{ name = $_.Name } })

    Assert-True (Assert-ManifestCompatible -Manifest $manifest -Source $source -Target $target) 'matching manifest should pass'
    Assert-Throws { Assert-ManifestCompatible -Manifest $null -Source $source -Target $target } 'missing manifest must be refused' | Out-Null

    $wrongVersion = $manifest | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongVersion.manifestVersion = 1
    Assert-Throws { Assert-ManifestCompatible -Manifest $wrongVersion -Source $source -Target $target } 'old manifest version must be refused' | Out-Null

    $wrongSource = $manifest | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongSource.source.database = 'other_database'
    Assert-Throws { Assert-ManifestCompatible -Manifest $wrongSource -Source $source -Target $target } 'foreign source must be refused' | Out-Null

    $wrongTables = $manifest | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $wrongTables.tables = @([pscustomobject]@{ name = 'feedback_reports' })
    Assert-Throws { Assert-ManifestCompatible -Manifest $wrongTables -Source $source -Target $target } 'partial table set must be refused' | Out-Null
}

Test-Case 'checksums are deterministic over identical canonical bytes' {
    $directory = Join-Path ([System.IO.Path]::GetTempPath()) ('backfill-hash-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $directory | Out-Null
    try {
        $encoding = New-Object System.Text.UTF8Encoding($false)
        $rows = "1f4d,2026-08-09 00:00:00.000000,~NULL~`n2f4d,2026-08-09 00:00:01.000000,body`n"
        $first = Join-Path $directory 'source.csv'
        $second = Join-Path $directory 'target.csv'
        $different = Join-Path $directory 'other.csv'
        [System.IO.File]::WriteAllText($first, $rows, $encoding)
        [System.IO.File]::WriteAllText($second, $rows, $encoding)
        [System.IO.File]::WriteAllText($different, $rows.Replace('body', 'BODY'), $encoding)

        $firstHash = (Get-FileHash -LiteralPath $first -Algorithm SHA256).Hash
        $secondHash = (Get-FileHash -LiteralPath $second -Algorithm SHA256).Hash
        $differentHash = (Get-FileHash -LiteralPath $different -Algorithm SHA256).Hash
        Assert-Equal $firstHash $secondHash 'identical canonical bytes must hash identically'
        Assert-True ($firstHash -ne $differentHash) 'different rows must hash differently'
    }
    finally {
        Remove-Item -LiteralPath $directory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host ("Passed: {0}  Failed: {1}" -f $script:Passed, $script:Failed)
if ($script:Failed -gt 0) {
    $script:Failures | ForEach-Object { Write-Host ("  - {0}" -f $_) }
    exit 1
}
exit 0
