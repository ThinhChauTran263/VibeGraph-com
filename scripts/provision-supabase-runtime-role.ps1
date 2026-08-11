<#
.SYNOPSIS
    Provisions the least-privilege Supabase runtime role and verifies it.

.DESCRIPTION
    Runs scripts/supabase-runtime-role.sql with the migration (DDL) credential, then prints the
    verification rows. Passwords are never accepted inside a connection URL and never passed as a
    native process argument: they go into a temporary PGPASSFILE with an owner-only ACL that is
    deleted in the finally block, on success and on failure alike.

    Supply credentials through -MigrationCredential / -RuntimeCredential, or through these
    environment variables from your secret store:

        VIBEGRAPH_SUPABASE_MIGRATION_PASSWORD   the DDL credential's password
        VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD     the password to set on the runtime role

.EXAMPLE
    $env:VIBEGRAPH_SUPABASE_MIGRATION_PASSWORD = '...'
    $env:VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD   = '...'
    .\scripts\provision-supabase-runtime-role.ps1 `
        -DatabaseHost db.your-project-ref.supabase.co `
        -Database postgres -MigrationUser postgres
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseHost,
    [int]$Port = 5432,
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$MigrationUser,
    [System.Management.Automation.PSCredential]$MigrationCredential,
    [string]$RuntimeRole = 'vibegraph_runtime',
    [System.Management.Automation.PSCredential]$RuntimeCredential,
    [string]$TargetSchema = 'vibegraph_realtime',
    [switch]$RequireTables
)

$ErrorActionPreference = 'Stop'

$backfillScript = Join-Path $PSScriptRoot 'backfill-supabase-realtime.ps1'
if (-not (Test-Path -LiteralPath $backfillScript)) {
    throw "backfill-supabase-realtime.ps1 not found next to this script."
}
# Reuse the validated credential, pgpass and identifier helpers rather than duplicating them.
. $backfillScript -LoadFunctionsOnly

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw 'psql is required and must be available on PATH. Install the PostgreSQL client tools.'
}

Assert-ValidHost -Value $DatabaseHost -Label 'DatabaseHost' | Out-Null
Assert-ValidPort -Value $Port -Label 'Port' | Out-Null
Assert-ValidIdentifier -Value $Database -Label 'Database' | Out-Null
Assert-ValidIdentifier -Value $RuntimeRole -Label 'RuntimeRole' | Out-Null
Assert-ValidIdentifier -Value $TargetSchema -Label 'TargetSchema' | Out-Null

$migration = Resolve-BackfillCredential -Credential $MigrationCredential -UserName $MigrationUser `
    -EnvironmentVariable 'VIBEGRAPH_SUPABASE_MIGRATION_PASSWORD' -Label 'Migration'
$runtime = Resolve-BackfillCredential -Credential $RuntimeCredential -UserName $RuntimeRole `
    -EnvironmentVariable 'VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD' -Label 'Runtime'

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'vibegraph-provision-' + [System.Guid]::NewGuid().ToString('N'))
$passFile = $null
$previousPassFile = [Environment]::GetEnvironmentVariable('PGPASSFILE')
$runtimePasswordVariable = 'VIBEGRAPH_SUPABASE_RUNTIME_PASSWORD'
$previousRuntimePassword = [Environment]::GetEnvironmentVariable($runtimePasswordVariable)

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    Protect-SensitiveFile -Path $temporaryRoot

    $passFile = New-PgPassFile -Directory $temporaryRoot -FileName 'migration.pgpass' `
        -HostName $DatabaseHost -Port $Port -Database $Database `
        -UserName $migration.UserName -SecurePassword $migration.SecurePassword
    $env:PGPASSFILE = $passFile

    # Keep the runtime password out of psql's command-line arguments.
    $runtimePlain = ConvertFrom-SecureStringPlain -SecurePassword $runtime.SecurePassword
    Set-Item "Env:$runtimePasswordVariable" $runtimePlain
    try {
        $arguments = @(
            "--host=$DatabaseHost"
            "--port=$Port"
            "--dbname=$Database"
            "--username=$($migration.UserName)"
            '--no-password'
            '--no-psqlrc'
            '--set=ON_ERROR_STOP=1'
            "--variable=runtime_role=$RuntimeRole"
            "--variable=db_name=$Database"
            "--variable=target_schema=$TargetSchema"
            "--variable=require_tables=$($RequireTables.IsPresent.ToString().ToLowerInvariant())"
            "--file=$(Join-Path $PSScriptRoot 'supabase-runtime-role.sql')"
        )
        & psql @arguments
        if ($LASTEXITCODE -ne 0) {
            throw ("Provisioning/verification failed (psql exit code {0}). Changes before COMMIT " +
                "may already be present; inspect the target before retrying.") -f $LASTEXITCODE
        }
    }
    finally {
        $runtimePlain = $null
        if ($null -eq $previousRuntimePassword) {
            Remove-Item "Env:$runtimePasswordVariable" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$runtimePasswordVariable" $previousRuntimePassword
        }
    }

    Write-Host ''
    Write-Host "Runtime role '$RuntimeRole' provisioned on $DatabaseHost/$Database."
    if ($RequireTables) {
        Write-Host 'Every verification row above must read True, and the publication gate must be empty.'
    }
    else {
        Write-Host 'Role/schema verification passed. Table checks may be PENDING until Flyway creates the tables.'
        Write-Host 'After migration/backfill, re-run with -RequireTables for the strict table privilege gate.'
    }
    Write-Host ''
    Write-Host 'Next: set these in the backend environment (the password from your secret store):'
    $runtimeConnectionUser = $RuntimeRole
    if ($DatabaseHost -match '(?i)\.pooler\.supabase\.com$') {
        if ($MigrationUser -match '^[^.]+\.(?<projectRef>[A-Za-z0-9]+)$') {
            $runtimeConnectionUser = "$RuntimeRole.$($Matches['projectRef'])"
        }
        else {
            Write-Warning 'Session Pooler detected, but MigrationUser is not qualified; use <runtime-role>.<project-ref> for SUPABASE_DB_USER.'
        }
    }
    Write-Host "  SUPABASE_DB_USER=$runtimeConnectionUser"
    Write-Host "  SUPABASE_MIGRATION_DB_USER=$MigrationUser"
    Write-Host "  SUPABASE_DB_SCHEMA=$TargetSchema"
    Write-Host '  SUPABASE_REQUIRE_SEPARATE_CREDENTIALS=true'
}
finally {
    Remove-SensitiveFile -Path $passFile | Out-Null
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
    if ($null -eq $previousPassFile) {
        Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSFILE = $previousPassFile
    }
    if ($null -eq $previousRuntimePassword) {
        Remove-Item "Env:$runtimePasswordVariable" -ErrorAction SilentlyContinue
    }
    else {
        Set-Item "Env:$runtimePasswordVariable" $previousRuntimePassword
    }
}
