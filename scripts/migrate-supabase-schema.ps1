<#
.SYNOPSIS
    Applies the dedicated Supabase Flyway migrations without starting the application.

.DESCRIPTION
    Uses the migration credential only. The password is written to a temporary owner-only Maven
    settings file so it never appears in a native process argument or a committed config file.
    The settings file and temporary directory are removed in finally, on success and on failure.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseHost,
    [int]$Port = 5432,
    [Parameter(Mandatory = $true)][string]$Database,
    [Parameter(Mandatory = $true)][string]$MigrationUser,
    [System.Management.Automation.PSCredential]$MigrationCredential,
    [string]$TargetSchema = 'vibegraph_realtime',
    [ValidateSet('disable', 'allow', 'prefer', 'require', 'verify-ca', 'verify-full')]
    [string]$SslMode = 'require',
    [string]$FlywayVersion = '11.14.1',
    [string]$MavenCommand
)

$ErrorActionPreference = 'Stop'

$backfillScript = Join-Path $PSScriptRoot 'backfill-supabase-realtime.ps1'
if (-not (Test-Path -LiteralPath $backfillScript)) {
    throw 'backfill-supabase-realtime.ps1 not found next to this script.'
}
. $backfillScript -LoadFunctionsOnly

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw 'psql is required and must be available on PATH.'
}

Assert-ValidHost -Value $DatabaseHost -Label 'DatabaseHost' | Out-Null
Assert-ValidPort -Value $Port -Label 'Port' | Out-Null
Assert-ValidIdentifier -Value $Database -Label 'Database' | Out-Null
Assert-ValidIdentifier -Value $TargetSchema -Label 'TargetSchema' | Out-Null
if ($FlywayVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw 'FlywayVersion must use the numeric major.minor.patch format.'
}

$migration = Resolve-BackfillCredential -Credential $MigrationCredential -UserName $MigrationUser `
    -EnvironmentVariable 'VIBEGRAPH_SUPABASE_MIGRATION_PASSWORD' -Label 'Migration'

$repoRoot = Split-Path -Parent $PSScriptRoot
$migrationLocation = Join-Path $repoRoot 'src/main/resources/db/supabase'
if (-not (Test-Path -LiteralPath $migrationLocation)) {
    throw "Supabase Flyway migration directory not found: $migrationLocation"
}

if (-not $MavenCommand) {
    $wrapper = Join-Path $repoRoot 'mvnw.cmd'
    if (Test-Path -LiteralPath $wrapper) {
        $MavenCommand = $wrapper
    }
    elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
        $MavenCommand = 'mvn'
    }
    else {
        throw 'Maven wrapper (mvnw.cmd) or mvn must be available.'
    }
}

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'vibegraph-flyway-' + [System.Guid]::NewGuid().ToString('N'))
$settingsPath = $null

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null
    Protect-SensitiveFile -Path $temporaryRoot
    $settingsPath = Join-Path $temporaryRoot 'settings.xml'

    $plainPassword = ConvertFrom-SecureStringPlain -SecurePassword $migration.SecurePassword
    try {
        $escapedUser = [System.Security.SecurityElement]::Escape($migration.UserName)
        $escapedPassword = [System.Security.SecurityElement]::Escape($plainPassword)
        $settings = @"
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>flyway-db</id>
      <username>$escapedUser</username>
      <password>$escapedPassword</password>
    </server>
  </servers>
</settings>
"@
        $encoding = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($settingsPath, $settings, $encoding)
    }
    finally {
        $plainPassword = $null
        $escapedUser = $null
        $escapedPassword = $null
        $settings = $null
    }
    Protect-SensitiveFile -Path $settingsPath

    $jdbcUrl = "jdbc:postgresql://{0}:{1}/{2}?sslmode={3}" -f $DatabaseHost, $Port, $Database, $SslMode
    $location = 'filesystem:' + ($migrationLocation -replace '\\', '/')
    $arguments = @(
        '--batch-mode'
        '--no-transfer-progress'
        "--settings=$settingsPath"
        "org.flywaydb:flyway-maven-plugin:${FlywayVersion}:migrate"
        "-Dflyway.serverId=flyway-db"
        "-Dflyway.url=$jdbcUrl"
        "-Dflyway.schemas=$TargetSchema"
        "-Dflyway.defaultSchema=$TargetSchema"
        "-Dflyway.locations=$location"
        '-Dflyway.createSchemas=true'
        '-Dflyway.cleanDisabled=true'
        '-Dflyway.validateOnMigrate=true'
        '-Dflyway.connectRetries=3'
    )

    Write-Host "Applying Supabase Flyway migrations to $DatabaseHost/$Database (schema $TargetSchema)..."
    & $MavenCommand @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Supabase Flyway migration failed (exit code $LASTEXITCODE)."
    }
    Write-Host 'Supabase Flyway migration completed.'
}
finally {
    Remove-SensitiveFile -Path $settingsPath | Out-Null
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
