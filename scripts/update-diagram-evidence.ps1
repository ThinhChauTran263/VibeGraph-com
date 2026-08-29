#requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$Watch,
    [switch]$AcceptCurrentBaseline,
    [switch]$Stop,
    [switch]$SkipRuntime,
    [switch]$RunFastChecks,
    [ValidateRange(5, 3600)]
    [int]$IntervalSeconds = 20,
    [ValidateRange(1, 1000)]
    [int]$RuntimeEveryCycles = 6,
    [ValidateRange(1, 1000)]
    [int]$FastChecksEveryCycles = 6,
    [string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}

$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$OutputRoot = Join-Path $RepositoryRoot 'Diagram\diagram update\live'
$BaselinePath = Join-Path $OutputRoot 'AUDITED-BASELINE.json'
$CurrentStatePath = Join-Path $OutputRoot 'CURRENT-STATE.json'
$LatestReportPath = Join-Path $OutputRoot 'LATEST-EVIDENCE.md'
$HistoryPath = Join-Path $OutputRoot 'DRIFT-HISTORY.jsonl'
$LogPath = Join-Path $OutputRoot 'WATCHER.log'
$PidPath = Join-Path $OutputRoot 'watcher.pid'
$StatusPath = Join-Path $OutputRoot 'WATCHER-STATUS.json'

$script:ProcessMutex = $null
$script:ProcessMutexOwned = $false
$rootHash = [Security.Cryptography.SHA256]::Create()
try {
    $rootBytes = [Text.Encoding]::UTF8.GetBytes($RepositoryRoot.ToLowerInvariant())
    $MutexName = 'VibeGraph-Diagram-Evidence-' + [Convert]::ToHexString($rootHash.ComputeHash($rootBytes)).Substring(0, 24)
}
finally {
    $rootHash.Dispose()
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

function Write-AtomicText {
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Content
    )

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $temporaryPath = Join-Path $directory ('.tmp-' + [guid]::NewGuid().ToString('N'))
    try {
        [IO.File]::WriteAllText($temporaryPath, $Content, [Text.UTF8Encoding]::new($false))
        for ($attempt = 1; $attempt -le 5; $attempt++) {
            try {
                [IO.File]::Move($temporaryPath, $Path, $true)
                return
            }
            catch {
                if ($attempt -eq 5) {
                    throw
                }
                Start-Sleep -Milliseconds (50 * $attempt)
            }
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Write-Log {
    param([Parameter(Mandatory)][string]$Message)

    $line = '{0} {1}{2}' -f (Get-Date -Format o), $Message, [Environment]::NewLine
    Append-LockedText -Path $LogPath -Content $line
}

function Write-RefreshStatus {
    param(
        [Parameter(Mandatory)][string]$Mode,
        [Parameter(Mandatory)][string]$Status,
        [AllowNull()][object]$CycleId,
        [AllowNull()][object]$CycleNumber,
        [AllowNull()][object]$LastSuccessAt,
        [int]$ConsecutiveFailures = 0,
        [AllowNull()][object]$ErrorMessage,
        [AllowNull()][object]$WatcherPid
    )

    $payload = [ordered]@{
        schemaVersion = 1
        updatedAt = (Get-Date -Format o)
        repository = $RepositoryRoot
        mode = $Mode
        status = $Status
        watcherPid = $WatcherPid
        cycleNumber = $CycleNumber
        cycleId = $CycleId
        lastSuccessAt = $LastSuccessAt
        consecutiveFailures = $ConsecutiveFailures
        error = $ErrorMessage
        semanticReview = 'NOT_PERFORMED'
    }
    Write-AtomicText -Path $StatusPath -Content ($payload | ConvertTo-Json -Depth 10)
}

function Publish-RefreshStatus {
    param(
        [Parameter(Mandatory)][string]$Mode,
        [Parameter(Mandatory)][string]$Status,
        [AllowNull()][object]$CycleId,
        [AllowNull()][object]$CycleNumber,
        [AllowNull()][object]$LastSuccessAt,
        [int]$ConsecutiveFailures = 0,
        [AllowNull()][object]$ErrorMessage,
        [AllowNull()][object]$WatcherPid
    )

    try {
        Write-RefreshStatus @PSBoundParameters
    }
    catch {
        try { Write-Log "Could not publish watcher status: $($_.Exception.Message)" } catch { }
    }
}

function Append-LockedText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Content
    )

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $encoding = [Text.UTF8Encoding]::new($false)
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        $stream = $null
        try {
            $stream = [IO.FileStream]::new($Path, [IO.FileMode]::Append, [IO.FileAccess]::Write, [IO.FileShare]::Read)
            $bytes = $encoding.GetBytes($Content)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
            return
        }
        catch {
            if ($attempt -eq 5) {
                throw
            }
            Start-Sleep -Milliseconds (50 * $attempt)
        }
        finally {
            if ($null -ne $stream) {
                $stream.Dispose()
            }
        }
    }
}

function Enter-ProcessLock {
    param([ValidateRange(0, 60000)][int]$TimeoutMilliseconds = 0)

    if ($script:ProcessMutexOwned) {
        return
    }

    $created = $false
    try {
        $script:ProcessMutex = [Threading.Mutex]::new($false, $MutexName, [ref]$created)
        try {
            $acquired = $script:ProcessMutex.WaitOne($TimeoutMilliseconds)
        }
        catch [Threading.AbandonedMutexException] {
            # WaitOne grants ownership even when the previous owner crashed.
            $acquired = $true
            Write-Log "Recovered abandoned watcher mutex '$MutexName'."
        }
        if (-not $acquired) {
            throw "Another diagram evidence process already owns mutex '$MutexName'."
        }
        $script:ProcessMutexOwned = $true
    }
    catch {
        if ($null -ne $script:ProcessMutex) {
            if ($script:ProcessMutexOwned) {
                try { $script:ProcessMutex.ReleaseMutex() } catch { }
            }
            $script:ProcessMutex.Dispose()
            $script:ProcessMutex = $null
        }
        $script:ProcessMutexOwned = $false
        throw
    }
}

function Exit-ProcessLock {
    if ($null -eq $script:ProcessMutex) {
        return
    }
    try {
        if ($script:ProcessMutexOwned) {
            $script:ProcessMutex.ReleaseMutex()
        }
    }
    finally {
        $script:ProcessMutexOwned = $false
        $script:ProcessMutex.Dispose()
        $script:ProcessMutex = $null
    }
}

function Get-ProcessIdentity {
    param([Parameter(Mandatory)][int]$ProcessId)

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return $null
    }

    $startUtc = $null
    $startTicks = $null
    try {
        $startTime = $process.StartTime.ToUniversalTime()
        $startUtc = $startTime.ToString('o')
        $startTicks = $startTime.Ticks
    }
    catch {
        # Access to process start time can be denied for another user.
    }

    $commandLine = $null
    $creationDate = $null
    try {
        $cim = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
        $commandLine = [string]$cim.CommandLine
        $creationDate = [string]$cim.CreationDate
    }
    catch {
        # Command-line verification is intentionally fail-closed in Stop-Watcher.
    }

    return [ordered]@{
        pid = $ProcessId
        processStartUtc = $startUtc
        processStartTicks = $startTicks
        creationDate = $creationDate
        commandLine = $commandLine
    }
}

function Get-WatcherIdentity {
    param([Parameter(Mandatory)][int]$ProcessId)

    $identity = Get-ProcessIdentity -ProcessId $ProcessId
    if ($null -eq $identity) {
        return $null
    }

    $scriptPath = [IO.Path]::GetFullPath($PSCommandPath)
    $commandLineMatches = -not [string]::IsNullOrWhiteSpace($identity.commandLine) -and
        $identity.commandLine.IndexOf($scriptPath, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $identity.commandLine -match '(?i)(^|\s)["'']?-Watch(["'']?)(\s|$)'

    return [ordered]@{
        identity = $identity
        commandLineMatches = $commandLineMatches
        scriptPath = $scriptPath
    }
}

function Convert-ToUtcTicks {
    param([AllowNull()][object]$Value)

    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [DateTimeOffset]) {
        return $Value.UtcDateTime.Ticks
    }
    if ($Value -is [DateTime]) {
        return $Value.ToUniversalTime().Ticks
    }
    try {
        return [DateTimeOffset]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind).UtcDateTime.Ticks
    }
    catch {
        return $null
    }
}

function Invoke-External {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $RepositoryRoot
    )

    Push-Location $WorkingDirectory
    try {
        $output = @(& $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        $exitCode = $LASTEXITCODE
        return [ordered]@{
            exitCode = $exitCode
            output = $output
        }
    }
    catch {
        return [ordered]@{
            exitCode = -1
            output = @($_.Exception.Message)
        }
    }
    finally {
        Pop-Location
    }
}

function Get-RelativePath {
    param([Parameter(Mandatory)][string]$Path)

    return [IO.Path]::GetRelativePath($RepositoryRoot, $Path).Replace('\', '/')
}

function Get-DiagramFamilies {
    param([Parameter(Mandatory)][string]$RelativePath)

    $families = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $path = $RelativePath.Replace('\', '/')

    if ($path -like 'Diagram/diagram update/*') {
        [void]$families.Add('diagram-artifacts')
    }
    if ($path -match '(^|/)(db|migration)/|\.sql$|\.cypher$|/domain/|Repository\.java$') {
        [void]$families.Add('erd')
    }
    if ($path -match '/controller/|/web/|Controller\.java$|router/index\.ts$|/lib/api\.ts$') {
        [void]$families.Add('usecase')
        [void]$families.Add('activity')
    }
    if ($path -match '/service/|Service(Impl)?\.java$|Scheduler\.java$|Broadcaster\.java$|Filter\.java$') {
        [void]$families.Add('activity')
        [void]$families.Add('class')
    }
    if ($path -match 'docker-compose|Dockerfile|application.*\.ya?ml$|nginx|/config/|pom\.xml$|package\.json$') {
        [void]$families.Add('component-deployment')
    }
    if ($path -match '/parser/|/graph/|GraphVocabulary|Neo4j|UseCase.*\.java$') {
        [void]$families.Add('class')
    }
    if ($path -match 'vibegraph-web/src/(views|components|stores|composables)/') {
        [void]$families.Add('usecase')
        [void]$families.Add('activity')
        [void]$families.Add('class')
    }
    if ($path -match '(^|/)(test|tests|__tests__)/|Test\.java$|\.spec\.ts$|\.Tests\.ps1$') {
        [void]$families.Add('test-evidence')
    }
    if ($path -match '^task-final/|^update/docs/|^scripts/drills/') {
        [void]$families.Add('documentation-evidence')
    }
    if ($families.Count -eq 0) {
        [void]$families.Add('manual-triage')
    }

    return @($families | Sort-Object)
}

function Get-EvidenceFiles {
    $files = [Collections.Generic.Dictionary[string, IO.FileInfo]]::new([StringComparer]::OrdinalIgnoreCase)

    $roots = @(
        @{ path = 'src/main/java'; extensions = @('.java') },
        @{ path = 'src/main/resources'; extensions = @('.sql', '.cypher', '.yaml', '.yml', '.properties') },
        @{ path = 'src/test/java'; extensions = @('.java') },
        @{ path = 'src/test/resources'; extensions = @('.sql', '.cypher', '.yaml', '.yml', '.properties', '.json', '.xml', '.txt', '.csv') },
        @{ path = 'db'; extensions = @('.sql', '.cypher', '.md') },
        @{ path = 'vibegraph-web/src'; extensions = @('.ts', '.vue', '.json') },
        @{ path = 'task-final'; extensions = @('.md', '.csv', '.json') },
        @{ path = 'update/docs/Qwen'; extensions = @('.md', '.json') },
        @{ path = 'scripts/drills'; extensions = @('.md', '.conf', '.ps1') },
        @{ path = 'Diagram/diagram update'; extensions = @('.md', '.docx', '') }
    )

    foreach ($root in $roots) {
        $absoluteRoot = Join-Path $RepositoryRoot $root.path
        if (-not (Test-Path -LiteralPath $absoluteRoot)) {
            continue
        }

        foreach ($file in Get-ChildItem -LiteralPath $absoluteRoot -Recurse -File -Force) {
            $relativePath = Get-RelativePath $file.FullName
            if ($relativePath -like 'Diagram/diagram update/live/*') {
                continue
            }
            if ($root.extensions -contains $file.Extension.ToLowerInvariant() -or
                ($file.Extension.Length -eq 0 -and $root.extensions -contains '')) {
                $files[$relativePath] = $file
            }
        }
    }

    $singleFiles = @(
        'AGENTS.md',
        'RULES.md',
        'pom.xml',
        'docker-compose.yml',
        'Dockerfile',
        'vibegraph-web/package.json',
        'vibegraph-web/package-lock.json',
        'vibegraph-web/vite.config.ts',
        'vibegraph-web/vitest.config.ts',
        'vibegraph-web/eslint.config.ts',
        'vibegraph-web/tsconfig.json',
        'vibegraph-web/tsconfig.app.json',
        'vibegraph-web/tsconfig.node.json',
        'vibegraph-web/tsconfig.vitest.json',
        'vibegraph-web/Dockerfile',
        'vibegraph-web/nginx.conf.template',
        '.codex/config.toml',
        'scripts/update-diagram-evidence.ps1'
    )

    foreach ($relativePath in $singleFiles) {
        $absolutePath = Join-Path $RepositoryRoot $relativePath
        if (Test-Path -LiteralPath $absolutePath) {
            $file = Get-Item -LiteralPath $absolutePath
            $files[(Get-RelativePath $file.FullName)] = $file
        }
    }

    return @($files.GetEnumerator() | Sort-Object Key | ForEach-Object { $_.Value })
}

function Get-StableFileRecord {
    param([Parameter(Mandatory)][IO.FileInfo]$File)

    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            $before = Get-Item -LiteralPath $File.FullName
            $beforeLength = $before.Length
            $beforeWriteUtc = $before.LastWriteTimeUtc
            $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $before.FullName).Hash
            $after = Get-Item -LiteralPath $File.FullName
            if ($beforeLength -ne $after.Length -or $beforeWriteUtc -ne $after.LastWriteTimeUtc) {
                throw "File changed while hashing: $($before.FullName)"
            }
            $relativePath = Get-RelativePath $before.FullName
            return [ordered]@{
                path = $relativePath
                sha256 = $hash
                length = $after.Length
                lastWriteUtc = $after.LastWriteTimeUtc.ToString('o')
                potentialDiagramFamilies = @(Get-DiagramFamilies $relativePath)
                readError = $null
                stable = $true
            }
        }
        catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Milliseconds (100 * $attempt)
        }
    }

    $relativePath = Get-RelativePath $File.FullName
    return [ordered]@{
        path = $relativePath
        sha256 = $null
        length = $null
        lastWriteUtc = $null
        potentialDiagramFamilies = @(Get-DiagramFamilies $relativePath)
        readError = $lastError
        stable = $false
    }
}

function Get-GitState {
    $head = Invoke-External -FilePath 'git' -Arguments @('-C', $RepositoryRoot, 'rev-parse', 'HEAD')
    $branch = Invoke-External -FilePath 'git' -Arguments @('-C', $RepositoryRoot, 'rev-parse', '--abbrev-ref', 'HEAD')
    $status = Invoke-External -FilePath 'git' -Arguments @(
        '-C', $RepositoryRoot, 'status', '--short', '--untracked-files=all', '--', '.',
        ':(exclude)Diagram/diagram update/live/**'
    )

    return [ordered]@{
        head = if ($head.exitCode -eq 0) { $head.output -join '' } else { $null }
        branch = if ($branch.exitCode -eq 0) { $branch.output -join '' } else { $null }
        status = @($status.output)
        headExitCode = $head.exitCode
        branchExitCode = $branch.exitCode
        statusExitCode = $status.exitCode
    }
}

function Get-PlantUmlValidation {
    $diagramRoot = Join-Path $RepositoryRoot 'Diagram\diagram update'
    $sourceNames = @(
        'plantuml_usecase.md',
        'plantuml_activity.md',
        'plantuml_erd_component_class.md'
    )
    $results = @()

    foreach ($name in $sourceNames) {
        $path = Join-Path $diagramRoot $name
        if (-not (Test-Path -LiteralPath $path)) {
            $results += [ordered]@{ file = $name; passed = $false; detail = 'missing' }
            continue
        }
        $content = Get-Content -LiteralPath $path -Raw
        $startCount = ([regex]::Matches($content, '@startuml')).Count
        $endCount = ([regex]::Matches($content, '@enduml')).Count
        $results += [ordered]@{
            file = $name
            passed = $startCount -eq $endCount -and $startCount -gt 0
            detail = 'markers={0}/{1}' -f $startCount, $endCount
        }
    }

    $combinedPath = Join-Path $diagramRoot 'VibeGraph_All_PlantUML_Diagrams.md'
    if (-not (Test-Path -LiteralPath $combinedPath)) {
        $results += [ordered]@{ file = 'VibeGraph_All_PlantUML_Diagrams.md'; passed = $false; detail = 'missing' }
        return $results
    }

    $combined = Get-Content -LiteralPath $combinedPath -Raw
    $combinedStart = ([regex]::Matches($combined, '@startuml')).Count
    $combinedEnd = ([regex]::Matches($combined, '@enduml')).Count
    $exactCopies = $true
    $copyDetails = @()

    foreach ($name in $sourceNames) {
        $beginMarker = '<!-- canonical-copy-begin: {0} -->' -f $name
        $endMarker = '<!-- canonical-copy-end: {0} -->' -f $name
        $beginIndex = $combined.IndexOf($beginMarker, [StringComparison]::Ordinal)
        $endIndex = $combined.IndexOf($endMarker, [StringComparison]::Ordinal)
        if ($beginIndex -lt 0 -or $endIndex -le $beginIndex) {
            $exactCopies = $false
            $copyDetails += "$name=missing-marker"
            continue
        }

        $copyStart = $beginIndex + $beginMarker.Length
        $copy = $combined.Substring($copyStart, $endIndex - $copyStart).Trim()
        $source = (Get-Content -LiteralPath (Join-Path $diagramRoot $name) -Raw).Trim()
        $matches = $copy -ceq $source
        $exactCopies = $exactCopies -and $matches
        $copyDetails += "$name=$matches"
    }

    $results += [ordered]@{
        file = 'VibeGraph_All_PlantUML_Diagrams.md'
        passed = $combinedStart -eq $combinedEnd -and $combinedStart -eq 18 -and $exactCopies
        detail = 'markers={0}/{1}; exactCopies={2}' -f $combinedStart, $combinedEnd, ($copyDetails -join ',')
    }

    return $results
}

function Get-DrawIoValidation {
    $diagramRoot = Join-Path $RepositoryRoot 'Diagram\diagram update'
    $expectedPages = [ordered]@{
        '1.Usecase Diagram' = 10
        '2.Activity Diagram' = 6
        '3.ERD Diagram' = 2
        '4.1.Component_Deployment Diagram' = 1
        '4.2.Class Diagram' = 2
    }
    $results = @()

    foreach ($entry in $expectedPages.GetEnumerator()) {
        $path = Join-Path $diagramRoot $entry.Key
        if (-not (Test-Path -LiteralPath $path)) {
            $results += [ordered]@{ file = $entry.Key; passed = $false; detail = 'missing' }
            continue
        }

        try {
            [xml]$document = Get-Content -LiteralPath $path -Raw
            $diagrams = @($document.mxfile.diagram)
            $duplicateIds = 0
            $badEdges = 0

            foreach ($diagram in $diagrams) {
                $cells = @($diagram.SelectNodes('.//mxCell'))
                $idCounts = @{}
                foreach ($cell in $cells) {
                    $id = [string]$cell.id
                    if ([string]::IsNullOrWhiteSpace($id)) {
                        continue
                    }
                    $idCounts[$id] = 1 + ($idCounts[$id] ?? 0)
                }
                $duplicateIds += @($idCounts.GetEnumerator() | Where-Object Value -gt 1).Count

                foreach ($edge in $cells | Where-Object { [string]$_.edge -eq '1' }) {
                    $source = [string]$edge.source
                    $target = [string]$edge.target
                    if ((-not [string]::IsNullOrWhiteSpace($source) -and -not $idCounts.ContainsKey($source)) -or
                        (-not [string]::IsNullOrWhiteSpace($target) -and -not $idCounts.ContainsKey($target))) {
                        $badEdges++
                    }
                }
            }

            $passed = $diagrams.Count -eq $entry.Value -and $duplicateIds -eq 0 -and $badEdges -eq 0
            $results += [ordered]@{
                file = $entry.Key
                passed = $passed
                detail = 'pages={0}/{1}; duplicateIds={2}; badEdges={3}' -f $diagrams.Count, $entry.Value, $duplicateIds, $badEdges
            }
        }
        catch {
            $results += [ordered]@{ file = $entry.Key; passed = $false; detail = $_.Exception.Message }
        }
    }

    return $results
}

function Get-DocxValidation {
    $path = Join-Path $RepositoryRoot 'Diagram\diagram update\3.VibeGraph_ProjectReportDocument(Updated).docx'
    if (-not (Test-Path -LiteralPath $path)) {
        return [ordered]@{ file = '3.VibeGraph_ProjectReportDocument(Updated).docx'; passed = $false; detail = 'missing' }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    try {
        $archive = [IO.Compression.ZipFile]::OpenRead($path)
        try {
            $names = @($archive.Entries.FullName)
            $required = @('[Content_Types].xml', 'word/document.xml', 'word/styles.xml')
            $missing = @($required | Where-Object { $names -notcontains $_ })
            return [ordered]@{
                file = '3.VibeGraph_ProjectReportDocument(Updated).docx'
                passed = $missing.Count -eq 0
                detail = if ($missing.Count -eq 0) { 'valid OOXML package; entries=' + $names.Count } else { 'missing=' + ($missing -join ',') }
            }
        }
        finally {
            $archive.Dispose()
        }
    }
    catch {
        return [ordered]@{ file = '3.VibeGraph_ProjectReportDocument(Updated).docx'; passed = $false; detail = $_.Exception.Message }
    }
}

function Get-EvidenceReferenceValidation {
    $diagramRoot = Join-Path $RepositoryRoot 'Diagram\diagram update'
    $liveRoot = Join-Path $diagramRoot 'live'
    $documents = @(
        Get-ChildItem -LiteralPath $diagramRoot -Recurse -File -Filter '*.md' |
            Where-Object { -not $_.FullName.StartsWith($liveRoot, [StringComparison]::OrdinalIgnoreCase) }
    )
    $pattern = '(?<path>(?:src|vibegraph-web|Diagram|task-final|update|scripts)[A-Za-z0-9_./\\ ()-]*\.(?:java|ts|vue|yaml|yml|sql|cypher|md|ps1|conf)):(?<start>\d+)(?:-(?<end>\d+))?'
    $checked = 0
    $errors = @()

    foreach ($document in $documents) {
        $content = Get-Content -LiteralPath $document.FullName -Raw
        foreach ($match in [regex]::Matches($content, $pattern)) {
            $checked++
            $relativePath = $match.Groups['path'].Value.Replace('/', [IO.Path]::DirectorySeparatorChar)
            $sourcePath = Join-Path $RepositoryRoot $relativePath
            if (-not (Test-Path -LiteralPath $sourcePath)) {
                $errors += 'missing {0} referenced by {1}' -f $match.Groups['path'].Value, (Get-RelativePath $document.FullName)
                continue
            }
            $lineCount = (Get-Content -LiteralPath $sourcePath).Count
            $endLine = if ($match.Groups['end'].Success) { [int]$match.Groups['end'].Value } else { [int]$match.Groups['start'].Value }
            if ($endLine -gt $lineCount) {
                $errors += 'out-of-range {0}:{1} (lines={2})' -f $match.Groups['path'].Value, $endLine, $lineCount
            }
        }
    }

    return [ordered]@{
        file = 'repo-relative numeric evidence references'
        passed = $errors.Count -eq 0
        detail = if ($errors.Count -eq 0) { "checked=$checked" } else { ($errors | Select-Object -First 10) -join '; ' }
    }
}

function Get-ArtifactValidation {
    $items = @()
    $items += @(Get-PlantUmlValidation)
    $items += @(Get-DrawIoValidation)
    $items += @(Get-DocxValidation)
    $items += @(Get-EvidenceReferenceValidation)

    return [ordered]@{
        passed = @($items | Where-Object { -not $_.passed }).Count -eq 0
        items = $items
    }
}

function Get-EvidenceCollectionValidation {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Files)

    $readFailures = @($Files | Where-Object { $_.readError -or -not $_.stable -or [string]::IsNullOrWhiteSpace([string]$_.sha256) })
    return [ordered]@{
        passed = @($Files).Count -gt 0 -and $readFailures.Count -eq 0
        fileCount = @($Files).Count
        readFailures = @($readFailures | ForEach-Object {
            [ordered]@{ path = $_.path; readError = $_.readError; stable = $_.stable }
        })
    }
}

function Get-GitStateValidation {
    param([Parameter(Mandatory)][object]$Git)

    return [ordered]@{
        passed = $Git.headExitCode -eq 0 -and $Git.branchExitCode -eq 0 -and $Git.statusExitCode -eq 0 -and
            -not [string]::IsNullOrWhiteSpace([string]$Git.head) -and
            -not [string]::IsNullOrWhiteSpace([string]$Git.branch)
        detail = 'head={0}; branch={1}; statusExitCode={2}' -f $Git.headExitCode, $Git.branchExitCode, $Git.statusExitCode
    }
}

function Get-RuntimeSnapshot {
    if ($SkipRuntime) {
        return [ordered]@{ skipped = $true; observedAt = (Get-Date -Format o) }
    }

    $snapshot = [ordered]@{
        skipped = $false
        reused = $false
        observedAt = (Get-Date -Format o)
        containers = @()
        postgres = $null
        neo4j = $null
        errors = @()
    }

    foreach ($containerName in @('vibegraph-postgres', 'vibegraph-neo4j')) {
        $inspect = Invoke-External -FilePath 'docker' -Arguments @(
            'inspect', '-f', '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{.Config.Image}}', $containerName
        )
        if ($inspect.exitCode -eq 0 -and $inspect.output.Count -gt 0) {
            $parts = $inspect.output[0].Split('|')
            $snapshot.containers += [ordered]@{
                name = $containerName
                status = $parts[0]
                health = $parts[1]
                image = $parts[2]
            }
        }
        else {
            $snapshot.errors += "$containerName inspect failed: $($inspect.output -join ' ')"
        }
    }

    $postgresScript = @'
psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "
SELECT 'domain_tables=' || count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history';
SELECT 'all_public_tables=' || count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';
SELECT 'successful_migrations=' || count(*) FROM flyway_schema_history WHERE success;
SELECT 'foreign_keys=' || count(*) FROM information_schema.table_constraints WHERE constraint_type='FOREIGN KEY' AND table_schema='public';
SELECT 'indexes=' || count(*) FROM pg_indexes WHERE schemaname='public';
"
'@
    $postgres = Invoke-External -FilePath 'docker' -Arguments @('exec', 'vibegraph-postgres', 'sh', '-lc', $postgresScript)
    if ($postgres.exitCode -eq 0) {
        $values = [ordered]@{}
        foreach ($line in $postgres.output) {
            if ($line -match '^(?<key>[a-z_]+)=(?<value>\d+)$') {
                $values[$Matches.key] = [int64]$Matches.value
            }
        }
        $snapshot.postgres = $values
    }
    else {
        $snapshot.errors += 'PostgreSQL query failed: ' + ($postgres.output -join ' ')
    }

    $neo4jScript = @'
u=${NEO4J_AUTH%%/*}; p=${NEO4J_AUTH#*/}; /var/lib/neo4j/bin/cypher-shell -u "$u" -p "$p" --format plain "MATCH (n) RETURN count(n); MATCH ()-[r]->() RETURN count(r);"
'@
    $neo4j = Invoke-External -FilePath 'docker' -Arguments @('exec', 'vibegraph-neo4j', 'bash', '-lc', $neo4jScript)
    if ($neo4j.exitCode -eq 0) {
        $numbers = @($neo4j.output | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int64]$_ })
        if ($numbers.Count -ge 2) {
            $snapshot.neo4j = [ordered]@{ nodes = $numbers[0]; relationships = $numbers[1] }
        }
        else {
            $snapshot.errors += 'Neo4j query returned an unexpected result.'
        }
    }
    else {
        $snapshot.errors += 'Neo4j query failed: ' + ($neo4j.output -join ' ')
    }

    $requiredPostgresKeys = @('domain_tables', 'all_public_tables', 'successful_migrations', 'foreign_keys', 'indexes')
    $missingPostgresKeys = @($requiredPostgresKeys | Where-Object { $null -eq $snapshot.postgres -or -not $snapshot.postgres.Contains($_) })
    if ($missingPostgresKeys.Count -gt 0) {
        $snapshot.errors += 'PostgreSQL query omitted keys: ' + ($missingPostgresKeys -join ', ')
    }
    $snapshot.validationPassed = $snapshot.errors.Count -eq 0 -and
        @($snapshot.containers | Where-Object { $_.status -ne 'running' -or ($_.health -and $_.health -notin @('healthy', 'none')) }).Count -eq 0 -and
        $null -ne $snapshot.postgres -and $null -ne $snapshot.neo4j
    return $snapshot
}

function Get-FastChecks {
    param([int]$CycleNumber = 0)

    if (-not $RunFastChecks -or ($Watch -and $CycleNumber % $FastChecksEveryCycles -ne 0)) {
        return [ordered]@{ skipped = $true; observedAt = (Get-Date -Format o); checks = @() }
    }

    $checks = @()
    $backend = Invoke-External -FilePath (Join-Path $RepositoryRoot 'mvnw.cmd') -Arguments @('-q', '-DskipTests', 'compile')
    $checks += [ordered]@{
        name = 'backend compile'
        passed = $backend.exitCode -eq 0
        exitCode = $backend.exitCode
        outputTail = @($backend.output | Select-Object -Last 20)
    }

    $frontend = Invoke-External -FilePath 'npm' -Arguments @('run', 'type-check') -WorkingDirectory (Join-Path $RepositoryRoot 'vibegraph-web')
    $checks += [ordered]@{
        name = 'frontend type-check'
        passed = $frontend.exitCode -eq 0
        exitCode = $frontend.exitCode
        outputTail = @($frontend.output | Select-Object -Last 20)
    }

    return [ordered]@{
        skipped = $false
        observedAt = (Get-Date -Format o)
        checks = $checks
    }
}

function Convert-FilesToMap {
    param([object[]]$Files)

    $map = @{}
    foreach ($file in @($Files)) {
        $map[[string]$file.path] = $file
    }
    return $map
}

function Get-FileDrift {
    param(
        [object[]]$BaselineFiles,
        [object[]]$CurrentFiles
    )

    if ($null -eq $BaselineFiles) {
        return @()
    }

    $baselineMap = Convert-FilesToMap $BaselineFiles
    $currentMap = Convert-FilesToMap $CurrentFiles
    $paths = @($baselineMap.Keys + $currentMap.Keys | Sort-Object -Unique)
    $drift = @()

    foreach ($path in $paths) {
        if (-not $baselineMap.ContainsKey($path)) {
            $drift += [ordered]@{
                change = 'added'
                path = $path
                baselineSha256 = $null
                currentSha256 = $currentMap[$path].sha256
                potentialDiagramFamilies = @($currentMap[$path].potentialDiagramFamilies)
            }
        }
        elseif (-not $currentMap.ContainsKey($path)) {
            $drift += [ordered]@{
                change = 'removed'
                path = $path
                baselineSha256 = $baselineMap[$path].sha256
                currentSha256 = $null
                potentialDiagramFamilies = @($baselineMap[$path].potentialDiagramFamilies)
            }
        }
        elseif ([string]$baselineMap[$path].sha256 -cne [string]$currentMap[$path].sha256) {
            $drift += [ordered]@{
                change = 'modified'
                path = $path
                baselineSha256 = $baselineMap[$path].sha256
                currentSha256 = $currentMap[$path].sha256
                potentialDiagramFamilies = @($currentMap[$path].potentialDiagramFamilies)
            }
        }
    }

    return $drift
}

function Get-StateFingerprint {
    param([object[]]$Files)

    $lines = @($Files | Sort-Object path | ForEach-Object { '{0}|{1}' -f $_.path, $_.sha256 })
    $bytes = [Text.Encoding]::UTF8.GetBytes($lines -join "`n")
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString($sha.ComputeHash($bytes))
    }
    finally {
        $sha.Dispose()
    }
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    }
    catch {
        Write-Log "Could not read JSON ${Path}: $($_.Exception.Message)"
        return $null
    }
}

function New-CurrentState {
    param([object]$PreviousState, [bool]$CollectRuntime, [int]$CycleNumber)

    $cycleId = [guid]::NewGuid().ToString('N')
    $captureStartedAt = (Get-Date -Format o)
    $git = Get-GitState
    $files = @(Get-EvidenceFiles | ForEach-Object { Get-StableFileRecord $_ })
    $initialFingerprint = Get-StateFingerprint $files
    $runtime = if ($CollectRuntime) {
        Get-RuntimeSnapshot
    }
    elseif ($null -ne $PreviousState -and $null -ne $PreviousState.runtime) {
        $reusedRuntime = [ordered]@{}
        foreach ($property in $PreviousState.runtime.PSObject.Properties) {
            $reusedRuntime[$property.Name] = $property.Value
        }
        $reusedRuntime.reused = $true
        $reusedRuntime.reusedFromObservedAt = $PreviousState.runtime.observedAt
        $reusedRuntime
    }
    else {
        [ordered]@{ skipped = $true; reused = $false; observedAt = (Get-Date -Format o); reason = 'not scheduled yet' }
    }

    $fastChecks = Get-FastChecks -CycleNumber $CycleNumber
    $artifactValidation = Get-ArtifactValidation
    $finalGit = Get-GitState
    $finalFiles = @(Get-EvidenceFiles | ForEach-Object { Get-StableFileRecord $_ })
    $finalFingerprint = Get-StateFingerprint $finalFiles
    $gitConsistent = $git.head -eq $finalGit.head -and $git.branch -eq $finalGit.branch -and
        (@($git.status) -join "`n") -ceq (@($finalGit.status) -join "`n")
    $consistency = [ordered]@{
        passed = $initialFingerprint -eq $finalFingerprint -and $gitConsistent
        initialFingerprint = $initialFingerprint
        finalFingerprint = $finalFingerprint
        gitConsistent = $gitConsistent
        scope = 'capture window only; later repository changes are future drift'
    }
    $observedAt = (Get-Date -Format o)

    return [ordered]@{
        schemaVersion = 2
        cycleId = $cycleId
        cycleNumber = $CycleNumber
        captureStartedAt = $captureStartedAt
        observedAt = $observedAt
        repository = $RepositoryRoot
        branch = $finalGit.branch
        head = $finalGit.head
        worktreeStatus = @($finalGit.status)
        gitValidation = Get-GitStateValidation $finalGit
        fingerprint = $finalFingerprint
        files = $finalFiles
        evidenceCollection = Get-EvidenceCollectionValidation $finalFiles
        consistency = $consistency
        artifactValidation = $artifactValidation
        runtime = $runtime
        fastChecks = $fastChecks
        semanticReview = [ordered]@{
            status = 'NOT_PERFORMED'
            approved = $false
            detail = 'The watcher performs mechanical evidence capture and structural checks only.'
        }
    }
}

function Get-RepositoryCapture {
    $git = Get-GitState
    $files = @(Get-EvidenceFiles | ForEach-Object { Get-StableFileRecord $_ })
    return [ordered]@{
        capturedAt = (Get-Date -Format o)
        head = $git.head
        branch = $git.branch
        worktreeStatus = @($git.status)
        gitValidation = Get-GitStateValidation $git
        fingerprint = Get-StateFingerprint $files
        evidenceCollection = Get-EvidenceCollectionValidation $files
    }
}

function Test-StateStillCurrent {
    param([Parameter(Mandatory)][object]$State)

    $checks = @()
    foreach ($attempt in 1..2) {
        $capture = Get-RepositoryCapture
        $gitMatches = $capture.head -eq $State.head -and $capture.branch -eq $State.branch -and
            (@($capture.worktreeStatus) -join "`n") -ceq (@($State.worktreeStatus) -join "`n")
        $checks += [ordered]@{
            attempt = $attempt
            capturedAt = $capture.capturedAt
            fingerprint = $capture.fingerprint
            fingerprintMatches = $capture.fingerprint -eq $State.fingerprint
            gitMatches = $gitMatches
            evidencePassed = $capture.evidenceCollection.passed
            gitPassed = $capture.gitValidation.passed
        }
    }

    return [ordered]@{
        passed = @($checks | Where-Object {
            -not $_.fingerprintMatches -or -not $_.gitMatches -or -not $_.evidencePassed -or -not $_.gitPassed
        }).Count -eq 0
        checks = $checks
    }
}

function Write-HistoryEvent {
    param([Parameter(Mandatory)][object]$Event)

    $json = $Event | ConvertTo-Json -Depth 20 -Compress
    Append-LockedText -Path $HistoryPath -Content ($json + [Environment]::NewLine)
}

function New-LatestReport {
    param(
        [Parameter(Mandatory)][object]$State,
        [object]$Baseline,
        [object[]]$BaselineDrift,
        [object[]]$CycleDrift
    )

    $baselineText = if ($null -eq $Baseline) {
        'NOT ACCEPTED'
    }
    elseif ($Baseline.acceptedAt -is [DateTime]) {
        $Baseline.acceptedAt.ToString('o')
    }
    else {
        [string]$Baseline.acceptedAt
    }
    $failedValidations = @($State.artifactValidation.items | Where-Object { -not $_.passed })
    $lines = [Collections.Generic.List[string]]::new()

    $lines.Add('# Live Diagram Evidence Status')
    $lines.Add('')
    $lines.Add('- Evidence cycle ID: `' + $State.cycleId + '`')
    $lines.Add('- Last checked: `' + $State.observedAt + '`')
    $lines.Add('- Repository HEAD: `' + $State.head + '`')
    $lines.Add('- Branch: `' + $State.branch + '`')
    $lines.Add('- Explicitly accepted evidence baseline: `' + $baselineText + '`')
    $lines.Add('- Drift from audited baseline: **' + $BaselineDrift.Count + ' file(s)**')
    $lines.Add('- Structural artifact checks: **' + $(if ($State.artifactValidation.passed) { 'STRUCTURAL PASS' } else { 'STRUCTURAL FAIL' }) + '**')
    $lines.Add('- Evidence reads: **' + $(if ($State.evidenceCollection.passed) { 'CAPTURE PASS' } else { 'CAPTURE FAIL' }) + '**')
    $lines.Add('- Whole-cycle consistency: **' + $(if ($State.consistency.passed) { 'CAPTURE PASS' } else { 'CAPTURE FAIL' }) + '**')
    $lines.Add('- Git evidence: **' + $(if ($State.gitValidation.passed) { 'CAPTURE PASS' } else { 'CAPTURE FAIL' }) + '**')
    $lines.Add('- Semantic review: **' + $State.semanticReview.status + '** (never performed by this watcher)')
    $lines.Add('')
    $lines.Add('Every PASS label in this report is mechanical and scoped to the named check. This report')
    $lines.Add('is generated evidence, not a semantic approval. Potential diagram-family')
    $lines.Add('labels are triage hints only. The watcher never edits canonical PlantUML, diagrams.net,')
    $lines.Add('DOCX, production code, migrations, or the accepted baseline unless explicitly invoked with')
    $lines.Add('`-AcceptCurrentBaseline`.')
    $lines.Add('')
    $lines.Add('## Drift requiring audit')
    $lines.Add('')
    if ($null -eq $Baseline) {
        $lines.Add('No accepted baseline exists. Run the script once with `-AcceptCurrentBaseline` only after')
        $lines.Add('the current source, database, and diagrams have been reviewed.')
    }
    elseif ($BaselineDrift.Count -eq 0) {
        $lines.Add('No evidence-scope file differs from the accepted audited baseline.')
    }
    else {
        $lines.Add('| Change | Path | Potential diagram families |')
        $lines.Add('| --- | --- | --- |')
        foreach ($item in $BaselineDrift) {
            $families = @($item.potentialDiagramFamilies) -join ', '
            $lines.Add('| ' + $item.change + ' | `' + $item.path + '` | ' + $families + ' |')
        }
    }

    $lines.Add('')
    $lines.Add('## Changes since previous watcher cycle')
    $lines.Add('')
    if ($CycleDrift.Count -eq 0) {
        $lines.Add('No evidence-scope file changed since the previous successful cycle.')
    }
    else {
        $lines.Add('| Change | Path | Potential diagram families |')
        $lines.Add('| --- | --- | --- |')
        foreach ($item in $CycleDrift) {
            $families = @($item.potentialDiagramFamilies) -join ', '
            $lines.Add('| ' + $item.change + ' | `' + $item.path + '` | ' + $families + ' |')
        }
    }

    $lines.Add('')
    $lines.Add('## Artifact validation')
    $lines.Add('')
    $lines.Add('| Artifact/check | Structural result | Detail |')
    $lines.Add('| --- | --- | --- |')
    foreach ($item in $State.artifactValidation.items) {
        $detail = ([string]$item.detail).Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ')
        $lines.Add('| `' + $item.file + '` | ' + $(if ($item.passed) { 'STRUCTURAL PASS' } else { 'STRUCTURAL FAIL' }) + ' | ' + $detail + ' |')
    }

    $lines.Add('')
    $lines.Add('## Runtime snapshot')
    $lines.Add('')
    if ($State.runtime.skipped) {
        $lines.Add('Runtime collection is skipped or not scheduled for this cycle.')
    }
    else {
        $lines.Add('- Observed at: `' + $State.runtime.observedAt + '`')
        if ($State.runtime.reused) {
            $lines.Add('- Runtime data is reused from the prior collection; it is not newly queried this cycle.')
        }
        foreach ($container in @($State.runtime.containers)) {
            $lines.Add('- `' + $container.name + '`: status=' + $container.status + ', health=' + $container.health + ', image=' + $container.image)
        }
        if ($null -ne $State.runtime.postgres) {
            $lines.Add('- PostgreSQL: domain tables=' + $State.runtime.postgres.domain_tables +
                ', migrations=' + $State.runtime.postgres.successful_migrations +
                ', FKs=' + $State.runtime.postgres.foreign_keys +
                ', indexes=' + $State.runtime.postgres.indexes)
        }
        if ($null -ne $State.runtime.neo4j) {
            $lines.Add('- Neo4j: nodes=' + $State.runtime.neo4j.nodes + ', relationships=' + $State.runtime.neo4j.relationships)
        }
        foreach ($runtimeError in @($State.runtime.errors)) {
            $lines.Add('- Runtime error: ' + $runtimeError)
        }
    }

    $lines.Add('')
    $lines.Add('## Fast checks')
    $lines.Add('')
    if ($State.fastChecks.skipped) {
        $lines.Add('Fast build checks are disabled for the watcher. Use `-RunFastChecks` for a one-shot')
        $lines.Add('backend compile and frontend type-check.')
    }
    else {
        foreach ($check in @($State.fastChecks.checks)) {
            $lines.Add('- ' + $check.name + ': ' + $(if ($check.passed) { 'COMMAND PASS' } else { 'COMMAND FAIL' }) + ' (exit ' + $check.exitCode + ')')
        }
    }

    $lines.Add('')
    $lines.Add('## Current worktree')
    $lines.Add('')
    $lines.Add('```text')
    foreach ($statusLine in @($State.worktreeStatus)) {
        $lines.Add($statusLine)
    }
    $lines.Add('```')
    $lines.Add('')
    $lines.Add('## Files')
    $lines.Add('')
    $lines.Add('- Rolling JSON state: `CURRENT-STATE.json`')
    $lines.Add('- Fixed accepted baseline: `AUDITED-BASELINE.json`')
    $lines.Add('- Change-only event history: `DRIFT-HISTORY.jsonl`')
    $lines.Add('- Watcher heartbeat/status: `WATCHER-STATUS.json`')
    $lines.Add('- Watcher log/PID: `WATCHER.log`, `watcher.pid`')

    $fastChecksFailed = -not $State.fastChecks.skipped -and @($State.fastChecks.checks | Where-Object { -not $_.passed }).Count -gt 0
    if ($failedValidations.Count -gt 0 -or -not $State.evidenceCollection.passed -or -not $State.consistency.passed -or -not $State.gitValidation.passed -or
        (-not $State.runtime.skipped -and -not $State.runtime.validationPassed) -or $fastChecksFailed) {
        $lines.Add('')
        $lines.Add('**Do not accept a new baseline while any evidence, consistency, Git, artifact, or runtime validation is failing.**')
    }

    return ($lines -join [Environment]::NewLine) + [Environment]::NewLine
}

function Invoke-RefreshCycle {
    param([int]$CycleNumber)

    $previousState = Read-JsonFile $CurrentStatePath
    $collectRuntime = -not $SkipRuntime -and ($CycleNumber -eq 0 -or $CycleNumber % $RuntimeEveryCycles -eq 0)
    $state = New-CurrentState -PreviousState $previousState -CollectRuntime $collectRuntime -CycleNumber $CycleNumber
    $historyEvents = [Collections.Generic.List[object]]::new()
    $previousBaselineExisted = $false
    $previousBaselineContent = $null
    $baselineWritten = $false
    $outputCommitComplete = $false

    try {
        if ($AcceptCurrentBaseline) {
            $runtimeValid = $SkipRuntime -or (-not $state.runtime.skipped -and $state.runtime.validationPassed)
            $fastChecksValid = -not $RunFastChecks -or (-not $state.fastChecks.skipped -and @($state.fastChecks.checks | Where-Object { -not $_.passed }).Count -eq 0)
            if (-not $state.artifactValidation.passed -or -not $state.evidenceCollection.passed -or
                -not $state.consistency.passed -or -not $state.gitValidation.passed -or -not $runtimeValid -or -not $fastChecksValid) {
                throw 'Refusing to accept baseline because one or more evidence gates are failing.'
            }

            $preWriteGuard = Test-StateStillCurrent $state
            if (-not $preWriteGuard.passed) {
                throw 'Refusing to accept baseline because repository evidence changed after capture.'
            }

            $previousBaselineExisted = Test-Path -LiteralPath $BaselinePath
            if ($previousBaselineExisted) {
                $previousBaselineContent = [IO.File]::ReadAllText($BaselinePath)
                if ($null -eq (Read-JsonFile $BaselinePath)) {
                    throw "Refusing to replace unreadable baseline: $BaselinePath"
                }
            }

            $baseline = [ordered]@{
                schemaVersion = 2
                acceptedAt = (Get-Date -Format o)
                cycleId = $state.cycleId
                repository = $state.repository
                branch = $state.branch
                head = $state.head
                fingerprint = $state.fingerprint
                files = $state.files
                artifactValidation = $state.artifactValidation
                evidenceCollection = $state.evidenceCollection
                consistency = $state.consistency
                gitValidation = $state.gitValidation
                runtime = $state.runtime
                semanticReview = $state.semanticReview
                acceptanceGuard = [ordered]@{
                    policy = 'two independent captures before and after atomic replacement'
                    preWrite = $preWriteGuard
                }
            }
            Write-AtomicText -Path $BaselinePath -Content ($baseline | ConvertTo-Json -Depth 30)
            $baselineWritten = $true

            $postWriteGuard = Test-StateStillCurrent $state
            if (-not $postWriteGuard.passed) {
                throw 'Baseline acceptance overlapped a repository change; restoring the previous baseline.'
            }

            $historyEvents.Add([ordered]@{
                event = 'baseline-accepted'
                observedAt = $state.observedAt
                acceptedAt = $baseline.acceptedAt
                cycleId = $state.cycleId
                head = $state.head
                fingerprint = $state.fingerprint
                semanticReview = 'NOT_PERFORMED'
                postWriteGuard = $postWriteGuard
            })
        }

        $baselineExists = Test-Path -LiteralPath $BaselinePath
        $baselineState = Read-JsonFile $BaselinePath
        if ($baselineExists -and $null -eq $baselineState) {
            throw "Accepted baseline exists but is unreadable: $BaselinePath"
        }

        $baselineDrift = if ($null -eq $baselineState) { @() } else { @(Get-FileDrift $baselineState.files $state.files) }
        $cycleDrift = if ($null -eq $previousState) { @() } else { @(Get-FileDrift $previousState.files $state.files) }

        if ($cycleDrift.Count -gt 0) {
            $historyEvents.Add([ordered]@{
                event = 'evidence-drift'
                observedAt = $state.observedAt
                cycleId = $state.cycleId
                head = $state.head
                fingerprint = $state.fingerprint
                changes = $cycleDrift
            })
        }

        $report = New-LatestReport -State $state -Baseline $baselineState -BaselineDrift $baselineDrift -CycleDrift $cycleDrift
        Write-AtomicText -Path $LatestReportPath -Content $report
        Write-AtomicText -Path $CurrentStatePath -Content ($state | ConvertTo-Json -Depth 30)
        $outputCommitComplete = $true

        $refreshWarnings = [Collections.Generic.List[string]]::new()
        foreach ($event in $historyEvents) {
            try {
                Write-HistoryEvent $event
            }
            catch {
                $refreshWarnings.Add("History append failed: $($_.Exception.Message)")
            }
        }

        try {
            Write-Log ('refresh complete: cycleId={0}, fingerprint={1}, baselineDrift={2}, cycleDrift={3}, structuralValidation={4}, semanticReview=NOT_PERFORMED' -f
                $state.cycleId, $state.fingerprint, $baselineDrift.Count, $cycleDrift.Count, $state.artifactValidation.passed)
        }
        catch {
            $refreshWarnings.Add("Completion log append failed: $($_.Exception.Message)")
        }
        $state['refreshWarnings'] = @($refreshWarnings)
        return $state
    }
    catch {
        $failure = $_
        $failureMessage = $failure.Exception.Message
        if ($baselineWritten -and -not $outputCommitComplete) {
            try {
                if ($previousBaselineExisted) {
                    Write-AtomicText -Path $BaselinePath -Content $previousBaselineContent
                }
                else {
                    Remove-Item -LiteralPath $BaselinePath -Force -ErrorAction SilentlyContinue
                }
            }
            catch {
                throw "Refresh failed: $failureMessage Baseline rollback also failed: $($_.Exception.Message)"
            }
        }
        throw $failure
    }
}

function Stop-Watcher {
    if (-not (Test-Path -LiteralPath $PidPath)) {
        Publish-RefreshStatus -Mode 'watch' -Status 'stopped' -CycleId $null -CycleNumber $null `
            -LastSuccessAt $null -WatcherPid $null
        Write-Output 'No watcher PID file exists.'
        return
    }

    $pidText = (Get-Content -LiteralPath $PidPath -Raw).Trim()
    $pidRecord = $null
    try { $pidRecord = $pidText | ConvertFrom-Json } catch { throw "Invalid watcher PID record: $PidPath" }
    if ($null -eq $pidRecord.pid -or [string]$pidRecord.pid -notmatch '^\d+$') {
        throw "Invalid watcher PID record: $PidPath"
    }
    $watcherPid = [int]$pidRecord.pid
    $identity = Get-WatcherIdentity -ProcessId $watcherPid
    if ($null -eq $identity) {
        Write-Output "Watcher PID $watcherPid is not running."
        Remove-Item -LiteralPath $PidPath -Force -ErrorAction SilentlyContinue
        Publish-RefreshStatus -Mode 'watch' -Status 'stopped' -CycleId $null -CycleNumber $null `
            -LastSuccessAt $null -WatcherPid $watcherPid
        return
    }
    $startMatches = if ([string]$pidRecord.creationDate -and [string]$identity.identity.creationDate) {
        [string]$pidRecord.creationDate -eq [string]$identity.identity.creationDate
    }
    elseif ($null -ne $pidRecord.processStartTicks -and $null -ne $identity.identity.processStartTicks) {
        [int64]$pidRecord.processStartTicks -eq [int64]$identity.identity.processStartTicks
    }
    elseif ($null -ne $pidRecord.processStartUtc -and $null -ne $identity.identity.processStartTicks) {
        $recordTicks = Convert-ToUtcTicks $pidRecord.processStartUtc
        $null -ne $recordTicks -and
            [Math]::Abs([int64]$recordTicks - [int64]$identity.identity.processStartTicks) -le [TimeSpan]::FromSeconds(2).Ticks
    }
    else {
        $false
    }
    if (-not $identity.commandLineMatches -or -not $startMatches) {
        throw "Refusing to stop PID $watcherPid because process identity does not match this watcher."
    }
    Stop-Process -Id $watcherPid
    Wait-Process -Id $watcherPid -Timeout 10 -ErrorAction SilentlyContinue
    Write-Output "Stopped diagram evidence watcher PID $watcherPid."
    Remove-Item -LiteralPath $PidPath -Force -ErrorAction SilentlyContinue
    Publish-RefreshStatus -Mode 'watch' -Status 'stopped' -CycleId $null -CycleNumber $null `
        -LastSuccessAt $null -WatcherPid $watcherPid
}

if ($Stop) {
    # The watcher owns the singleton mutex for its lifetime; stop validates identity
    # directly so it can terminate that owner instead of waiting on its mutex.
    Stop-Watcher
    exit 0
}

if ($Watch -and $AcceptCurrentBaseline) {
    throw 'Run -AcceptCurrentBaseline as a one-shot command before starting -Watch.'
}

if ($Watch) {
    Enter-ProcessLock
    try {
        $identity = Get-ProcessIdentity -ProcessId $PID
        Write-AtomicText -Path $PidPath -Content (@{
            pid = $PID
            processStartUtc = $identity.processStartUtc
            processStartTicks = $identity.processStartTicks
            creationDate = $identity.creationDate
            scriptPath = [IO.Path]::GetFullPath($PSCommandPath)
            startedAt = (Get-Date -Format o)
        } | ConvertTo-Json -Compress)
        Write-Log "watcher started: PID=$PID intervalSeconds=$IntervalSeconds runtimeEveryCycles=$RuntimeEveryCycles fastChecksEveryCycles=$FastChecksEveryCycles"
        $cycle = 0
        $consecutiveFailures = 0
        $lastSuccessAt = $null
        $lastCycleId = $null
        $existingState = Read-JsonFile $CurrentStatePath
        if ($null -ne $existingState) {
            $lastSuccessAt = if ($existingState.observedAt -is [DateTime]) {
                $existingState.observedAt.ToString('o')
            }
            else {
                [string]$existingState.observedAt
            }
            $lastCycleId = [string]$existingState.cycleId
        }
        Publish-RefreshStatus -Mode 'watch' -Status 'starting' -CycleId $lastCycleId -CycleNumber $null `
            -LastSuccessAt $lastSuccessAt -WatcherPid $PID
        while ($true) {
            $state = $null
            Publish-RefreshStatus -Mode 'watch' -Status 'refreshing' -CycleId $lastCycleId -CycleNumber $cycle `
                -LastSuccessAt $lastSuccessAt -ConsecutiveFailures $consecutiveFailures -WatcherPid $PID
            try {
                $state = Invoke-RefreshCycle -CycleNumber $cycle
                $consecutiveFailures = 0
                $lastSuccessAt = [string]$state.observedAt
                $lastCycleId = [string]$state.cycleId
                $refreshWarnings = @($state.refreshWarnings)
                $completionStatus = if ($refreshWarnings.Count -eq 0) { 'refresh-complete' } else { 'refresh-complete-with-warnings' }
                $warningText = if ($refreshWarnings.Count -eq 0) { $null } else { $refreshWarnings -join '; ' }
                Publish-RefreshStatus -Mode 'watch' -Status $completionStatus -CycleId $lastCycleId -CycleNumber $cycle `
                    -LastSuccessAt $lastSuccessAt -ErrorMessage $warningText -WatcherPid $PID
            }
            catch {
                $consecutiveFailures++
                $failureMessage = $_.Exception.Message
                try { Write-Log "refresh failed (consecutive=$consecutiveFailures); CURRENT-STATE commit marker remains at the prior cycle; reject any report cycle mismatch: $failureMessage" } catch { }
                Publish-RefreshStatus -Mode 'watch' -Status 'refresh-failed' -CycleId $lastCycleId -CycleNumber $cycle `
                    -LastSuccessAt $lastSuccessAt -ConsecutiveFailures $consecutiveFailures -ErrorMessage $failureMessage -WatcherPid $PID
            }
            $cycle++
            $sleepSeconds = if ($null -ne $state -and -not $state.consistency.passed) { 1 } else { $IntervalSeconds }
            Start-Sleep -Seconds $sleepSeconds
        }
    }
    finally {
        $pidRecord = if (Test-Path -LiteralPath $PidPath) { Read-JsonFile $PidPath } else { $null }
        if ($null -ne $pidRecord -and [string]$pidRecord.pid -eq [string]$PID) {
            Remove-Item -LiteralPath $PidPath -Force -ErrorAction SilentlyContinue
        }
        Publish-RefreshStatus -Mode 'watch' -Status 'stopped' -CycleId $lastCycleId -CycleNumber $cycle `
            -LastSuccessAt $lastSuccessAt -ConsecutiveFailures $consecutiveFailures -WatcherPid $PID
        Write-Log "watcher stopped: PID=$PID"
        Exit-ProcessLock
    }
}
else {
    Enter-ProcessLock
    try {
        $lastCommittedState = Read-JsonFile $CurrentStatePath
        $lastCommittedCycleId = if ($null -eq $lastCommittedState) { $null } else { [string]$lastCommittedState.cycleId }
        $lastCommittedAt = if ($null -eq $lastCommittedState) {
            $null
        }
        elseif ($lastCommittedState.observedAt -is [DateTime]) {
            $lastCommittedState.observedAt.ToString('o')
        }
        else {
            [string]$lastCommittedState.observedAt
        }
        Publish-RefreshStatus -Mode 'one-shot' -Status 'refreshing' -CycleId $lastCommittedCycleId -CycleNumber 0 `
            -LastSuccessAt $lastCommittedAt -WatcherPid $null
        $state = Invoke-RefreshCycle -CycleNumber 0
        $refreshWarnings = @($state.refreshWarnings)
        $completionStatus = if ($refreshWarnings.Count -eq 0) { 'refresh-complete' } else { 'refresh-complete-with-warnings' }
        $warningText = if ($refreshWarnings.Count -eq 0) { $null } else { $refreshWarnings -join '; ' }
        Publish-RefreshStatus -Mode 'one-shot' -Status $completionStatus -CycleId $state.cycleId -CycleNumber 0 `
            -LastSuccessAt ([string]$state.observedAt) -ErrorMessage $warningText -WatcherPid $null
        Write-Output ('Diagram evidence refreshed at {0}. StructuralValidation={1}. SemanticReview=NOT_PERFORMED. Report={2}' -f
            $state.observedAt, $state.artifactValidation.passed, $LatestReportPath)
    }
    catch {
        $failureMessage = $_.Exception.Message
        Publish-RefreshStatus -Mode 'one-shot' -Status 'refresh-failed' -CycleId $lastCommittedCycleId -CycleNumber 0 `
            -LastSuccessAt $lastCommittedAt -ConsecutiveFailures 1 -ErrorMessage $failureMessage -WatcherPid $null
        throw
    }
    finally {
        Exit-ProcessLock
    }
}
