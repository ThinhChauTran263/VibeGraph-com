<#
.SYNOPSIS
    Backfills the six realtime tables from the primary PostgreSQL database into the Supabase schema.

.DESCRIPTION
    This script only moves data. It never stops or starts the application: pausing writers is a
    deployment-orchestration task and the operator must confirm it explicitly with -WritersArePaused.

    The maintenance window must stay open for the WHOLE sequence below, not just the dump:

        pause every writer
          -> dump
          -> import
          -> semantic verification
          -> Realtime publication verification
          -> enable Supabase
          -> application smoke test
          -> resume writers

    A single pg_dump invocation exports all six tables, which gives one internally consistent
    snapshot ACROSS the tables. It does NOT close the write gap on its own: anything written to the
    source after the dump starts is simply not in it. Only the maintenance window (or a delta pass,
    or dual-write) prevents lost rows.

    Passwords are never accepted inside a connection URL and never passed as a native process
    argument. They are written to a temporary PGPASSFILE with a restrictive ACL and deleted in the
    finally block, on success and on failure alike.

.PARAMETER WritersArePaused
    Explicit operator confirmation that HTTP writers, scheduled jobs and every backend instance are
    paused. The script fails closed without it.

.PARAMETER Resume
    Allows a non-empty target, but only when -ManifestPath points at a manifest that matches this
    source, target schema and table set.

.PARAMETER LoadFunctionsOnly
    Dot-source the script for testing without executing the backfill.

.EXAMPLE
    $env:VIBEGRAPH_BACKFILL_SOURCE_PASSWORD = (Read-Host -AsSecureString | ConvertFrom-SecureString)
    .\backfill-supabase-realtime.ps1 -SourceHost db.internal -SourceDatabase vibegraph `
        -SourceUser vibegraph_migration -TargetHost db.project.supabase.co `
        -TargetDatabase postgres -TargetUser postgres_migration -WritersArePaused
#>
[CmdletBinding()]
param(
    [string]$SourceHost,
    [int]$SourcePort = 5432,
    [string]$SourceDatabase,
    [string]$SourceUser,
    [System.Management.Automation.PSCredential]$SourceCredential,

    [string]$TargetHost,
    [int]$TargetPort = 5432,
    [string]$TargetDatabase,
    [string]$TargetUser,
    [System.Management.Automation.PSCredential]$TargetCredential,

    [string]$SourceSchema = 'public',
    [string]$TargetSchema = 'vibegraph_realtime',
    [string]$ManifestPath,

    [switch]$WritersArePaused,
    [switch]$Resume,
    [switch]$LoadFunctionsOnly
)

$ErrorActionPreference = 'Stop'

$script:ManifestVersion = 2
$script:TransformationVersion = 1
$script:SourcePasswordVariable = 'VIBEGRAPH_BACKFILL_SOURCE_PASSWORD'
$script:TargetPasswordVariable = 'VIBEGRAPH_BACKFILL_TARGET_PASSWORD'
$script:NullSentinel = '~NULL~'
$script:TimestampFormat = 'YYYY-MM-DD HH24:MI:SS.US'
$script:RealtimePublication = 'supabase_realtime'

# Tables in dependency order: parents before children, so the final inserts satisfy the foreign
# keys in the target schema without deferring them.
$script:BackfillTables = @(
    [pscustomobject]@{
        Name = 'feedback_reports'
        KeyColumns = @('id')
        TimestampColumn = 'created_at'
        StatefulFilter = $null
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'status'; Kind = 'text' }
            [pscustomobject]@{ Name = 'category'; Kind = 'text' }
            [pscustomobject]@{ Name = 'title'; Kind = 'text' }
            [pscustomobject]@{ Name = 'created_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'closed_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'delete_after'; Kind = 'timestamp' }
        )
    }
    [pscustomobject]@{
        Name = 'feedback_messages'
        KeyColumns = @('id')
        TimestampColumn = 'created_at'
        StatefulFilter = $null
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'report_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'sender_user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'sender_role'; Kind = 'text' }
            [pscustomobject]@{ Name = 'body'; Kind = 'text' }
            [pscustomobject]@{ Name = 'created_at'; Kind = 'timestamp' }
        )
    }
    [pscustomobject]@{
        Name = 'announcements'
        KeyColumns = @('id')
        TimestampColumn = 'created_at'
        StatefulFilter = $null
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'type'; Kind = 'text' }
            [pscustomobject]@{ Name = 'severity'; Kind = 'text' }
            [pscustomobject]@{ Name = 'target'; Kind = 'text' }
            [pscustomobject]@{ Name = 'title'; Kind = 'text' }
            [pscustomobject]@{ Name = 'body'; Kind = 'text' }
            [pscustomobject]@{ Name = 'starts_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'ends_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'dismissible'; Kind = 'text' }
            [pscustomobject]@{ Name = 'active'; Kind = 'text' }
            [pscustomobject]@{ Name = 'created_by_user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'created_at'; Kind = 'timestamp' }
        )
    }
    [pscustomobject]@{
        Name = 'user_notifications'
        KeyColumns = @('id')
        TimestampColumn = 'created_at'
        # The new model treats a missing row as unread, so unread rows are not migrated. The same
        # predicate is applied to the source when verifying, otherwise the counts could never match.
        StatefulFilter = 'read_at IS NOT NULL OR dismissed_at IS NOT NULL'
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'announcement_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'read_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'dismissed_at'; Kind = 'timestamp' }
            [pscustomobject]@{ Name = 'created_at'; Kind = 'timestamp' }
        )
    }
    [pscustomobject]@{
        Name = 'request_events'
        KeyColumns = @('id')
        TimestampColumn = 'occurred_at'
        StatefulFilter = $null
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'api_key_ref'; Kind = 'text' }
            [pscustomobject]@{ Name = 'ip_address'; Kind = 'text' }
            [pscustomobject]@{ Name = 'route'; Kind = 'text' }
            [pscustomobject]@{ Name = 'http_method'; Kind = 'text' }
            [pscustomobject]@{ Name = 'status'; Kind = 'text' }
            [pscustomobject]@{ Name = 'event_type'; Kind = 'text' }
            [pscustomobject]@{ Name = 'occurred_at'; Kind = 'timestamp' }
        )
    }
    [pscustomobject]@{
        Name = 'security_events'
        KeyColumns = @('id')
        TimestampColumn = 'created_at'
        StatefulFilter = $null
        Columns = @(
            [pscustomobject]@{ Name = 'id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'event_type'; Kind = 'text' }
            [pscustomobject]@{ Name = 'severity'; Kind = 'text' }
            [pscustomobject]@{ Name = 'subject_user_id'; Kind = 'text' }
            [pscustomobject]@{ Name = 'api_key_ref'; Kind = 'text' }
            [pscustomobject]@{ Name = 'source'; Kind = 'text' }
            [pscustomobject]@{ Name = 'description'; Kind = 'text' }
            [pscustomobject]@{ Name = 'created_at'; Kind = 'timestamp' }
        )
    }
)

# Raw telemetry must never be broadcast to browsers through Supabase Realtime.
$script:TelemetryTables = @('request_events', 'security_events')

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

function Assert-ValidIdentifier {
    param([string]$Value, [string]$Label)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is required."
    }
    if ($Value -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "$Label must be a plain PostgreSQL identifier (got an unsupported value)."
    }
    return $Value
}

function Assert-ValidRoleName {
    param([string]$Value, [string]$Label)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is required."
    }
    # A connection user is never interpolated into SQL: it only reaches PGPASSFILE (escaped) and a
    # --username= argument. Supabase's session pooler requires the qualified form
    # <role>.<project-ref>, so exactly one dot-suffix is allowed; everything that could be a shell
    # or pgpass metacharacter still is not.
    if ($Value -notmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)?$') {
        throw ("$Label must be a role name, optionally qualified for the Supabase session pooler " +
            'as <role>.<project-ref> (got an unsupported value).')
    }
    return $Value
}

function Assert-ValidHost {
    param([string]$Value, [string]$Label)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is required."
    }
    if ($Value -notmatch '^[A-Za-z0-9._-]+$') {
        throw "$Label must be a hostname or IP address without separators or shell metacharacters."
    }
    return $Value
}

function Assert-ValidPort {
    param([int]$Value, [string]$Label)

    if ($Value -lt 1 -or $Value -gt 65535) {
        throw "$Label must be between 1 and 65535."
    }
    return $Value
}

function Assert-MaintenanceConfirmed {
    param([bool]$WritersArePaused)

    if (-not $WritersArePaused) {
        throw @'
Refusing to run: -WritersArePaused was not supplied.

Before re-running, confirm that ALL of the following are paused and stay paused until the cutover
is verified and you resume them yourself:
  * HTTP writers (the backend API)
  * scheduled jobs (retention, feedback cleanup, telemetry flush)
  * every backend instance / replica
  * any other process that can write the six source tables

A single pg_dump gives one snapshot across the tables, but it does not close the write gap.
'@
    }
    return $true
}

# ---------------------------------------------------------------------------
# Credentials
# ---------------------------------------------------------------------------

# Tests set this to $false so the missing-password path stays deterministic wherever they run.
$script:AllowInteractiveCredentialPrompt = $true

function Test-InteractiveCredentialPrompt {
    if (-not $script:AllowInteractiveCredentialPrompt) {
        return $false
    }
    # Automation must fail closed rather than block on a prompt nobody can answer.
    return [Environment]::UserInteractive -and -not [Console]::IsInputRedirected
}

function Resolve-BackfillCredential {
    param(
        [System.Management.Automation.PSCredential]$Credential,
        [string]$UserName,
        [string]$EnvironmentVariable,
        [string]$Label
    )

    if ($Credential) {
        return [pscustomobject]@{
            UserName = $Credential.UserName
            SecurePassword = $Credential.Password
        }
    }

    Assert-ValidRoleName -Value $UserName -Label "$Label user" | Out-Null
    $raw = [Environment]::GetEnvironmentVariable($EnvironmentVariable)
    if ([string]::IsNullOrEmpty($raw)) {
        # An environment variable only lives in the shell that set it, and `pwsh -File` starts a
        # child process, so a forgotten export is the most common way to hit this. Ask the operator
        # directly instead: the value is typed without echo and never reaches the environment,
        # the process list or disk.
        if (Test-InteractiveCredentialPrompt) {
            $prompted = Read-Host -Prompt "$Label password for $UserName" -AsSecureString
            if ($prompted -and $prompted.Length -gt 0) {
                $prompted.MakeReadOnly()
                return [pscustomobject]@{
                    UserName = $UserName
                    SecurePassword = $prompted
                }
            }
        }
        throw ("$Label password is missing. Re-run interactively to be prompted, supply " +
            '-{0}Credential, or set the {1} environment variable from your secret store. ' +
            'Passwords are never accepted inside a connection URL.') -f $Label, $EnvironmentVariable
    }
    $secure = New-Object System.Security.SecureString
    foreach ($character in $raw.ToCharArray()) {
        $secure.AppendChar($character)
    }
    $secure.MakeReadOnly()
    return [pscustomobject]@{
        UserName = $UserName
        SecurePassword = $secure
    }
}

function ConvertFrom-SecureStringPlain {
    param([System.Security.SecureString]$SecurePassword)

    $pointer = [System.Runtime.InteropServices.Marshal]::SecureStringToGlobalAllocUnicode($SecurePassword)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringUni($pointer)
    }
    finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeGlobalAllocUnicode($pointer)
    }
}

function ConvertTo-PgPassField {
    param([string]$Value)

    # libpq treats ':' as a field separator and '\' as an escape character.
    return $Value.Replace('\', '\\').Replace(':', '\:')
}

function New-PgPassFile {
    param(
        [string]$Directory,
        [string]$FileName,
        [string]$HostName,
        [int]$Port,
        [string]$Database,
        [string]$UserName,
        [System.Security.SecureString]$SecurePassword
    )

    $path = Join-Path $Directory $FileName
    $plain = ConvertFrom-SecureStringPlain -SecurePassword $SecurePassword
    try {
        $line = '{0}:{1}:{2}:{3}:{4}' -f (ConvertTo-PgPassField $HostName), $Port,
            (ConvertTo-PgPassField $Database), (ConvertTo-PgPassField $UserName),
            (ConvertTo-PgPassField $plain)
        # libpq rejects a pgpass file it cannot parse; a BOM would break the first field.
        $encoding = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($path, $line + "`n", $encoding)
    }
    finally {
        $plain = $null
    }
    Protect-SensitiveFile -Path $path
    return $path
}

function Protect-SensitiveFile {
    param([string]$Path)

    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $isDirectory = (Get-Item -LiteralPath $Path).PSIsContainer
    # A directory has to pass its owner-only rule down, otherwise files created inside it end up
    # with an empty DACL and become unreadable to the script that just wrote them.
    if ($isDirectory) {
        $inheritance = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    }
    else {
        $inheritance = [System.Security.AccessControl.InheritanceFlags]::None
    }
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) {
        $acl.RemoveAccessRuleAll($rule) | Out-Null
    }
    $ownerOnly = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $identity.User, 'FullControl', $inheritance,
        [System.Security.AccessControl.PropagationFlags]::None, 'Allow')
    $acl.AddAccessRule($ownerOnly)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function Remove-SensitiveFile {
    param([string]$Path)

    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) {
        return $false
    }
    try {
        $length = (Get-Item -LiteralPath $Path).Length
        if ($length -gt 0) {
            $zeros = New-Object byte[] $length
            [System.IO.File]::WriteAllBytes($Path, $zeros)
        }
    }
    catch {
        # Overwriting is best-effort hardening; deletion below is what actually matters.
    }
    Remove-Item -LiteralPath $Path -Force
    return $true
}

# ---------------------------------------------------------------------------
# SQL generation
# ---------------------------------------------------------------------------

function Get-CanonicalColumnExpression {
    param([pscustomobject]$Column)

    if ($Column.Kind -eq 'timestamp') {
        return "coalesce(to_char(`"$($Column.Name)`" AT TIME ZONE 'UTC', '$script:TimestampFormat'), '$script:NullSentinel')"
    }
    return "coalesce(`"$($Column.Name)`"::text, '$script:NullSentinel')"
}

function Get-CanonicalCopyStatement {
    param([pscustomobject]$Table, [string]$Schema)

    $expressions = @()
    foreach ($column in $Table.Columns) {
        $expressions += (Get-CanonicalColumnExpression -Column $column)
    }
    $order = ($Table.KeyColumns | ForEach-Object { "`"$_`"" }) -join ', '
    $where = ''
    if ($Table.StatefulFilter) {
        $where = " WHERE $($Table.StatefulFilter)"
    }
    # COPY ... TO STDOUT streams row by row. psql writes it straight to a file with --output, so
    # neither the server, psql nor PowerShell ever holds the whole table in memory. string_agg over
    # a table would do exactly that and is deliberately avoided.
    return ('COPY (SELECT {0} FROM "{1}"."{2}"{3} ORDER BY {4}) TO STDOUT WITH (FORMAT csv)' -f
        ($expressions -join ', '), $Schema, $Table.Name, $where, $order)
}

function Get-CountStatement {
    param([pscustomobject]$Table, [string]$Schema, [switch]$ApplyStatefulFilter)

    $where = ''
    if ($ApplyStatefulFilter -and $Table.StatefulFilter) {
        $where = " WHERE $($Table.StatefulFilter)"
    }
    return ('SELECT count(*) FROM "{0}"."{1}"{2}' -f $Schema, $Table.Name, $where)
}

function Get-TimestampBoundsStatement {
    param([pscustomobject]$Table, [string]$Schema, [switch]$ApplyStatefulFilter)

    $where = ''
    if ($ApplyStatefulFilter -and $Table.StatefulFilter) {
        $where = " WHERE $($Table.StatefulFilter)"
    }
    return ("SELECT coalesce(to_char(min(`"{0}`") AT TIME ZONE 'UTC', '{1}'), '{2}') || '|' || " +
        "coalesce(to_char(max(`"{0}`") AT TIME ZONE 'UTC', '{1}'), '{2}') FROM `"{3}`".`"{4}`"{5}") -f
        $Table.TimestampColumn, $script:TimestampFormat, $script:NullSentinel, $Schema, $Table.Name, $where
}

function Get-DumpArguments {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$Database,
        [string]$UserName,
        [string]$Schema,
        [string]$OutputFile
    )

    # One invocation, six --table arguments: one internally consistent snapshot across the tables.
    # --no-password guarantees pg_dump never prompts and never reads a password from the command
    # line; the credential comes from PGPASSFILE.
    $arguments = @(
        "--host=$HostName"
        "--port=$Port"
        "--dbname=$Database"
        "--username=$UserName"
        '--no-password'
        '--data-only'
        '--column-inserts'
        '--no-owner'
        '--no-privileges'
    )
    foreach ($table in $script:BackfillTables) {
        $arguments += "--table=$Schema.$($table.Name)"
    }
    $arguments += "--file=$OutputFile"
    return $arguments
}

function Get-PsqlArguments {
    param(
        [string]$HostName,
        [int]$Port,
        [string]$Database,
        [string]$UserName
    )

    return @(
        "--host=$HostName"
        "--port=$Port"
        "--dbname=$Database"
        "--username=$UserName"
        '--no-password'
        '--no-psqlrc'
        '--quiet'
        '--set=ON_ERROR_STOP=1'
    )
}

function Get-ImportScript {
    param([string]$Schema, [string]$DumpFile)

    # The dump writes schema-qualified INSERT INTO public.<table> statements. Rewriting that text
    # would risk corrupting row data that happens to contain the same prefix, so instead the rows
    # land in unlogged staging tables that really are called public.<table>, and are then copied
    # into the target schema. Everything runs in one transaction, so a failure leaves nothing
    # behind and the staging tables never become visible.
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.AppendLine('SET client_min_messages TO warning;')
    foreach ($table in $script:BackfillTables) {
        [void]$builder.AppendLine(
            ('CREATE UNLOGGED TABLE "public"."{0}" (LIKE "{1}"."{0}" INCLUDING DEFAULTS);' -f $table.Name, $Schema))
    }
    [void]$builder.AppendLine(("\i '{0}'" -f ($DumpFile -replace '\\', '/')))
    foreach ($table in $script:BackfillTables) {
        $columns = ($table.Columns | ForEach-Object { "`"$($_.Name)`"" }) -join ', '
        $where = ''
        if ($table.StatefulFilter) {
            [void]$builder.AppendLine(
                ('-- Unread rows are not migrated: a missing row now means unread.'))
            $where = " WHERE $($table.StatefulFilter)"
        }
        [void]$builder.AppendLine(
            ('INSERT INTO "{0}"."{1}" ({2}) SELECT {2} FROM "public"."{1}"{3} ON CONFLICT ("id") DO NOTHING;' -f
                $Schema, $table.Name, $columns, $where))
    }
    foreach ($table in $script:BackfillTables) {
        [void]$builder.AppendLine(('DROP TABLE "public"."{0}";' -f $table.Name))
    }
    return $builder.ToString()
}

function Get-RealtimePublicationStatement {
    $names = ($script:TelemetryTables | ForEach-Object { "'$_'" }) -join ', '
    return ("SELECT coalesce(string_agg(schemaname || '.' || tablename, ','), '') " +
        "FROM pg_publication_tables WHERE pubname = '{0}' AND tablename IN ({1})") -f
        $script:RealtimePublication, $names
}

# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------

function New-BackfillManifest {
    param(
        [pscustomobject]$Source,
        [pscustomobject]$Target,
        [datetime]$DumpStartedAt,
        [datetime]$DumpCompletedAt
    )

    return [pscustomobject]@{
        manifestVersion = $script:ManifestVersion
        transformationVersion = $script:TransformationVersion
        dumpStartedAt = $DumpStartedAt.ToUniversalTime().ToString('o')
        dumpCompletedAt = $DumpCompletedAt.ToUniversalTime().ToString('o')
        # Identity only: never a password and never a full connection URL.
        source = [pscustomobject]@{
            host = $Source.HostName; port = $Source.Port
            database = $Source.Database; schema = $Source.Schema; user = $Source.UserName
        }
        target = [pscustomobject]@{
            host = $Target.HostName; port = $Target.Port
            database = $Target.Database; schema = $Target.Schema; user = $Target.UserName
        }
        tables = @()
        verification = [pscustomobject]@{ status = 'PENDING'; failures = @() }
        realtimePublication = [pscustomobject]@{ status = 'PENDING'; offendingTables = @() }
    }
}

function Assert-ManifestCompatible {
    param([pscustomobject]$Manifest, [pscustomobject]$Source, [pscustomobject]$Target)

    $failures = @()
    if ($null -eq $Manifest) {
        $failures += 'manifest is missing'
    }
    else {
        if ($Manifest.manifestVersion -ne $script:ManifestVersion) {
            $failures += "manifest version $($Manifest.manifestVersion) does not match $($script:ManifestVersion)"
        }
        if ($Manifest.transformationVersion -ne $script:TransformationVersion) {
            $failures += 'transformation version does not match'
        }
        if ($Manifest.source.host -ne $Source.HostName -or $Manifest.source.port -ne $Source.Port -or
            $Manifest.source.database -ne $Source.Database -or $Manifest.source.schema -ne $Source.Schema) {
            $failures += 'manifest was produced from a different source database'
        }
        if ($Manifest.target.schema -ne $Target.Schema -or $Manifest.target.database -ne $Target.Database -or
            $Manifest.target.host -ne $Target.HostName) {
            $failures += 'manifest was produced for a different target'
        }
        $manifestTables = @($Manifest.tables | ForEach-Object { $_.name }) | Sort-Object
        $expectedTables = @($script:BackfillTables | ForEach-Object { $_.Name }) | Sort-Object
        if (($manifestTables -join ',') -ne ($expectedTables -join ',')) {
            $failures += 'manifest table set does not match this script'
        }
    }
    if ($failures.Count -gt 0) {
        throw ("Refusing to resume: {0}. Never import into a target whose state is unknown." -f ($failures -join '; '))
    }
    return $true
}

function Save-BackfillManifest {
    param([pscustomobject]$Manifest, [string]$Path)

    $json = $Manifest | ConvertTo-Json -Depth 8
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $json, $encoding)
    return $Path
}

function Read-BackfillManifest {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json)
}

# ---------------------------------------------------------------------------
# Native command helpers
# ---------------------------------------------------------------------------

function Invoke-NativeCommand {
    param([string]$Command, [string[]]$Arguments, [string]$FailureMessage)

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)."
    }
}

function Invoke-PsqlScalar {
    param([string[]]$ConnectionArguments, [string]$Statement)

    $output = & psql @ConnectionArguments '--no-align' '--tuples-only' '--command' $Statement
    if ($LASTEXITCODE -ne 0) {
        throw "psql query failed (exit code $LASTEXITCODE)."
    }
    return (@($output) -join '').Trim()
}

function Export-CanonicalTable {
    param([string[]]$ConnectionArguments, [string]$Statement, [string]$OutputFile)

    # --output makes psql write the COPY stream straight to disk.
    & psql @ConnectionArguments '--no-align' '--tuples-only' "--output=$OutputFile" '--command' $Statement
    if ($LASTEXITCODE -ne 0) {
        throw "psql canonical export failed (exit code $LASTEXITCODE)."
    }
    return (Get-FileHash -LiteralPath $OutputFile -Algorithm SHA256).Hash.ToLowerInvariant()
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

function Invoke-SupabaseBackfill {
    param(
        [string]$SourceHost, [int]$SourcePort, [string]$SourceDatabase, [string]$SourceUser,
        [System.Management.Automation.PSCredential]$SourceCredential,
        [string]$TargetHost, [int]$TargetPort, [string]$TargetDatabase, [string]$TargetUser,
        [System.Management.Automation.PSCredential]$TargetCredential,
        [string]$SourceSchema, [string]$TargetSchema, [string]$ManifestPath,
        [bool]$WritersArePaused, [bool]$Resume
    )

    Assert-MaintenanceConfirmed -WritersArePaused $WritersArePaused | Out-Null

    foreach ($command in @('pg_dump', 'psql')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "$command is required and must be available on PATH."
        }
    }

    $source = [pscustomobject]@{
        HostName = (Assert-ValidHost -Value $SourceHost -Label 'SourceHost')
        Port = (Assert-ValidPort -Value $SourcePort -Label 'SourcePort')
        Database = (Assert-ValidIdentifier -Value $SourceDatabase -Label 'SourceDatabase')
        Schema = (Assert-ValidIdentifier -Value $SourceSchema -Label 'SourceSchema')
        UserName = $null
    }
    $target = [pscustomobject]@{
        HostName = (Assert-ValidHost -Value $TargetHost -Label 'TargetHost')
        Port = (Assert-ValidPort -Value $TargetPort -Label 'TargetPort')
        Database = (Assert-ValidIdentifier -Value $TargetDatabase -Label 'TargetDatabase')
        Schema = (Assert-ValidIdentifier -Value $TargetSchema -Label 'TargetSchema')
        UserName = $null
    }

    $sourceCredentialResolved = Resolve-BackfillCredential -Credential $SourceCredential `
        -UserName $SourceUser -EnvironmentVariable $script:SourcePasswordVariable -Label 'Source'
    $targetCredentialResolved = Resolve-BackfillCredential -Credential $TargetCredential `
        -UserName $TargetUser -EnvironmentVariable $script:TargetPasswordVariable -Label 'Target'
    $source.UserName = $sourceCredentialResolved.UserName
    $target.UserName = $targetCredentialResolved.UserName

    if (-not $ManifestPath) {
        $ManifestPath = Join-Path (Get-Location) ('supabase-backfill-manifest-{0}.json' -f
            (Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss'))
    }

    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
        'vibegraph-supabase-backfill-' + [System.Guid]::NewGuid().ToString('N'))
    $sourcePassFile = $null
    $targetPassFile = $null
    $previousPassFile = [Environment]::GetEnvironmentVariable('PGPASSFILE')
    $previousEncoding = [Environment]::GetEnvironmentVariable('PGCLIENTENCODING')

    try {
        New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
        Protect-SensitiveFile -Path $temporaryRoot
        $env:PGCLIENTENCODING = 'UTF8'

        $sourcePassFile = New-PgPassFile -Directory $temporaryRoot -FileName 'source.pgpass' `
            -HostName $source.HostName -Port $source.Port -Database $source.Database `
            -UserName $source.UserName -SecurePassword $sourceCredentialResolved.SecurePassword
        $targetPassFile = New-PgPassFile -Directory $temporaryRoot -FileName 'target.pgpass' `
            -HostName $target.HostName -Port $target.Port -Database $target.Database `
            -UserName $target.UserName -SecurePassword $targetCredentialResolved.SecurePassword

        $sourceConnection = Get-PsqlArguments -HostName $source.HostName -Port $source.Port `
            -Database $source.Database -UserName $source.UserName
        $targetConnection = Get-PsqlArguments -HostName $target.HostName -Port $target.Port `
            -Database $target.Database -UserName $target.UserName

        # ---- Preflight -----------------------------------------------------
        $env:PGPASSFILE = $targetPassFile
        $existingManifest = Read-BackfillManifest -Path $ManifestPath
        $nonEmpty = @()
        foreach ($table in $script:BackfillTables) {
            $count = [int64](Invoke-PsqlScalar -ConnectionArguments $targetConnection `
                -Statement (Get-CountStatement -Table $table -Schema $target.Schema))
            if ($count -gt 0) {
                $nonEmpty += "$($table.Name)=$count"
            }
            $staging = [int64](Invoke-PsqlScalar -ConnectionArguments $targetConnection -Statement (
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = '$($table.Name)'"))
            if ($staging -gt 0) {
                throw ("Refusing to run: the target already has a public.{0} table, which would " +
                    'collide with the import staging table. Resolve it manually first.') -f $table.Name
            }
        }
        if ($nonEmpty.Count -gt 0) {
            if (-not $Resume) {
                throw ("Refusing to run: the target schema {0} already contains rows ({1}). " +
                    'Re-run with -Resume and a matching -ManifestPath, or start from an empty target. ' +
                    'Conflict-tolerant import is never applied to a target whose state is unknown.') -f
                    $target.Schema, ($nonEmpty -join ', ')
            }
            Assert-ManifestCompatible -Manifest $existingManifest -Source $source -Target $target | Out-Null
            Write-Host "Resume mode: target is non-empty and the manifest matches. Import stays idempotent."
        }

        # ---- Dump ----------------------------------------------------------
        $env:PGPASSFILE = $sourcePassFile
        $dumpFile = Join-Path $temporaryRoot 'backfill.sql'
        $dumpStartedAt = Get-Date
        Write-Host "Exporting $($script:BackfillTables.Count) tables in one pg_dump invocation..."
        Invoke-NativeCommand -Command 'pg_dump' -FailureMessage 'pg_dump failed' -Arguments (
            Get-DumpArguments -HostName $source.HostName -Port $source.Port -Database $source.Database `
                -UserName $source.UserName -Schema $source.Schema -OutputFile $dumpFile)
        $dumpCompletedAt = Get-Date

        $manifest = New-BackfillManifest -Source $source -Target $target `
            -DumpStartedAt $dumpStartedAt -DumpCompletedAt $dumpCompletedAt

        # ---- Source measurements -------------------------------------------
        $tableReports = @()
        foreach ($table in $script:BackfillTables) {
            $sourceCount = [int64](Invoke-PsqlScalar -ConnectionArguments $sourceConnection `
                -Statement (Get-CountStatement -Table $table -Schema $source.Schema -ApplyStatefulFilter))
            $bounds = (Invoke-PsqlScalar -ConnectionArguments $sourceConnection `
                -Statement (Get-TimestampBoundsStatement -Table $table -Schema $source.Schema -ApplyStatefulFilter)) -split '\|'
            $checksumFile = Join-Path $temporaryRoot ('source-{0}.csv' -f $table.Name)
            $sourceChecksum = Export-CanonicalTable -ConnectionArguments $sourceConnection `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $source.Schema) `
                -OutputFile $checksumFile
            $tableReports += [pscustomobject]@{
                name = $table.Name
                filterApplied = [bool]$table.StatefulFilter
                sourceCount = $sourceCount
                sourceMinTimestamp = $bounds[0]
                sourceMaxTimestamp = $bounds[1]
                targetCount = $null
                targetMinTimestamp = $null
                targetMaxTimestamp = $null
                sourceChecksum = $sourceChecksum
                targetChecksum = $null
            }
        }

        # ---- Import --------------------------------------------------------
        $env:PGPASSFILE = $targetPassFile
        $importFile = Join-Path $temporaryRoot 'import.sql'
        $encoding = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($importFile, (Get-ImportScript -Schema $target.Schema -DumpFile $dumpFile), $encoding)
        Write-Host "Importing into $($target.Schema) in a single transaction..."
        Invoke-NativeCommand -Command 'psql' -FailureMessage 'Supabase import failed; the target transaction was rolled back' `
            -Arguments ($targetConnection + @('--single-transaction', "--file=$importFile"))

        # ---- Verification ---------------------------------------------------
        $failures = @()
        foreach ($report in $tableReports) {
            $table = $script:BackfillTables | Where-Object { $_.Name -eq $report.name }
            $report.targetCount = [int64](Invoke-PsqlScalar -ConnectionArguments $targetConnection `
                -Statement (Get-CountStatement -Table $table -Schema $target.Schema))
            $targetBounds = (Invoke-PsqlScalar -ConnectionArguments $targetConnection `
                -Statement (Get-TimestampBoundsStatement -Table $table -Schema $target.Schema)) -split '\|'
            $report.targetMinTimestamp = $targetBounds[0]
            $report.targetMaxTimestamp = $targetBounds[1]
            $targetFile = Join-Path $temporaryRoot ('target-{0}.csv' -f $table.Name)
            # The target already excludes unread rows, so the same canonical query applies to both
            # sides and the checksums are directly comparable.
            $report.targetChecksum = Export-CanonicalTable -ConnectionArguments $targetConnection `
                -Statement (Get-CanonicalCopyStatement -Table $table -Schema $target.Schema) `
                -OutputFile $targetFile

            if ($report.sourceCount -ne $report.targetCount) {
                $failures += "$($report.name): count $($report.sourceCount) != $($report.targetCount)"
            }
            if ($report.sourceChecksum -ne $report.targetChecksum) {
                $failures += "$($report.name): canonical checksum mismatch"
            }
        }

        $orphanMessages = [int64](Invoke-PsqlScalar -ConnectionArguments $targetConnection -Statement (
            'SELECT count(*) FROM "{0}"."feedback_messages" m LEFT JOIN "{0}"."feedback_reports" r ' +
            'ON r."id" = m."report_id" WHERE r."id" IS NULL') -f $target.Schema)
        if ($orphanMessages -gt 0) {
            $failures += "feedback_messages: $orphanMessages rows without a feedback_reports parent"
        }
        $orphanNotifications = [int64](Invoke-PsqlScalar -ConnectionArguments $targetConnection -Statement (
            'SELECT count(*) FROM "{0}"."user_notifications" n LEFT JOIN "{0}"."announcements" a ' +
            'ON a."id" = n."announcement_id" WHERE a."id" IS NULL') -f $target.Schema)
        if ($orphanNotifications -gt 0) {
            $failures += "user_notifications: $orphanNotifications rows without an announcements parent"
        }

        $manifest.tables = $tableReports
        $manifest.verification = [pscustomobject]@{
            status = $(if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' })
            failures = $failures
        }

        # ---- Realtime publication gate --------------------------------------
        # Read the live catalog with the operator credential. Migration SQL is not evidence: a
        # publication can be changed from the Supabase dashboard afterwards.
        $published = Invoke-PsqlScalar -ConnectionArguments $targetConnection `
            -Statement (Get-RealtimePublicationStatement)
        $offending = @()
        if ($published) {
            $offending = $published -split ','
        }
        $manifest.realtimePublication = [pscustomobject]@{
            status = $(if ($offending.Count -eq 0) { 'PASS' } else { 'FAIL' })
            offendingTables = $offending
        }

        Save-BackfillManifest -Manifest $manifest -Path $ManifestPath | Out-Null
        Write-Host "Manifest written to $ManifestPath"

        if ($failures.Count -gt 0) {
            throw ("Cutover verification FAILED: {0}. The source tables were not modified." -f ($failures -join '; '))
        }
        if ($offending.Count -gt 0) {
            throw ("Cutover verification FAILED: raw telemetry tables are published to " +
                "'{0}': {1}. Remove them from the publication before enabling Supabase.") -f
                $script:RealtimePublication, ($offending -join ', ')
        }

        Write-Host 'Backfill and verification passed. The source tables were not modified.'
        Write-Host 'Keep every writer paused until you have enabled Supabase and finished the smoke test.'
        return $manifest
    }
    finally {
        Remove-SensitiveFile -Path $sourcePassFile | Out-Null
        Remove-SensitiveFile -Path $targetPassFile | Out-Null
        if (Test-Path -LiteralPath $temporaryRoot) {
            Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
        }
        if ($null -eq $previousPassFile) {
            Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue
        }
        else {
            $env:PGPASSFILE = $previousPassFile
        }
        if ($null -eq $previousEncoding) {
            Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue
        }
        else {
            $env:PGCLIENTENCODING = $previousEncoding
        }
    }
}

if (-not $LoadFunctionsOnly) {
    Invoke-SupabaseBackfill -SourceHost $SourceHost -SourcePort $SourcePort `
        -SourceDatabase $SourceDatabase -SourceUser $SourceUser -SourceCredential $SourceCredential `
        -TargetHost $TargetHost -TargetPort $TargetPort -TargetDatabase $TargetDatabase `
        -TargetUser $TargetUser -TargetCredential $TargetCredential `
        -SourceSchema $SourceSchema -TargetSchema $TargetSchema -ManifestPath $ManifestPath `
        -WritersArePaused ([bool]$WritersArePaused) -Resume ([bool]$Resume)
}
