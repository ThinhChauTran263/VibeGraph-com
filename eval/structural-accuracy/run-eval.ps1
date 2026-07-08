<#
  VibeGraph - Structural accuracy eval (Phase 1)

  Measures the graph VibeGraph generates for a REAL Java repo against an INDEPENDENT
  ground truth counted directly from source (regex on .java files - NOT JavaParser, so
  the oracle is independent of the tool under test).

  Output: a Markdown table (report.md) + CSV (report.csv) for the thesis Evaluation chapter.

  Usage:
    ./run-eval.ps1 -RepoPath <abs path to repo> -Name <display name>

  Requires: backend on :8080, Neo4j up.
#>
param(
    [Parameter(Mandatory = $true)][string]$RepoPath,
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$ProjectId = "",
    [string]$Base = "http://localhost:8080",
    [string]$OutDir = "$PSScriptRoot"
)

$ErrorActionPreference = "Stop"

# --- 1. Independent ground truth from source (main source only; tests excluded) --------------
$mainSrc = Join-Path $RepoPath "src\main\java"
if (-not (Test-Path $mainSrc)) { $mainSrc = $RepoPath }
$javaFiles = Get-ChildItem -Recurse -Filter *.java $mainSrc -ErrorAction SilentlyContinue
$allText = ($javaFiles | Get-Content -Raw) -join "`n"
# Strip block comments, line comments, and string literals so the regex oracle never matches
# prose like "// this class can ..." (which otherwise inflates type counts with bogus names).
$allText = $allText -replace '(?s)/\*.*?\*/', ' '
$allText = $allText -replace '(?m)//[^\r\n]*', ' '
$allText = $allText -replace '"(\\.|[^"\\\r\n])*"', '""'

function CountMatches($text, $pattern) {
    return ([regex]::Matches($text, $pattern)).Count
}

$gtClasses    = CountMatches $allText '(?m)\b(?:public\s+|final\s+|abstract\s+|static\s+)*class\s+\w+'
$gtInterfaces = CountMatches $allText '(?m)\binterface\s+\w+'
$gtEnums      = CountMatches $allText '(?m)\benum\s+\w+'
$gtRecords    = CountMatches $allText '(?m)\brecord\s+\w+\s*\('
$gtTypes      = $gtClasses + $gtInterfaces + $gtEnums + $gtRecords

$gtEndpoints  = CountMatches $allText '@(?:Get|Post|Put|Delete|Patch)Mapping'
$gtReqMapping = CountMatches $allText '@RequestMapping'
$gtEntities   = CountMatches $allText '@Entity'
$gtControllers = CountMatches $allText '@(?:Rest)?Controller'

# --- 2. Import the repo into VibeGraph (or reuse) and wait for ANALYZED ----------------------
$projectId = $ProjectId
if (-not $projectId) {
    $body = @{ path = $RepoPath; name = $Name } | ConvertTo-Json -Compress
    $import = Invoke-RestMethod -Uri "$Base/api/projects/import-local" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 180
    $projectId = $import.data.id
    if (-not $projectId) { throw "Import did not return a project id" }
}

$graph = $null
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    try {
        $resp = Invoke-RestMethod -Uri "$Base/api/projects/$projectId/graph?nodeLimit=100000&edgeLimit=100000" -Method Get -TimeoutSec 180
        if ($resp.data -and $resp.data.nodes) { $graph = $resp.data; break }
    } catch { }
}
if (-not $graph) { throw "Graph not available after import for $projectId" }

# --- 3. Tool-side counts from the generated graph -------------------------------------------
$nodeByType = @{}
foreach ($n in $graph.nodes) {
    if (-not $n.type) { continue }
    if ($nodeByType.ContainsKey($n.type)) { $nodeByType[$n.type] = [int]$nodeByType[$n.type] + 1 }
    else { $nodeByType[$n.type] = 1 }
}
$edgeByType = @{}
foreach ($e in $graph.edges) {
    if (-not $e.type) { continue }
    if ($edgeByType.ContainsKey($e.type)) { $edgeByType[$e.type] = [int]$edgeByType[$e.type] + 1 }
    else { $edgeByType[$e.type] = 1 }
}
function NodeCount($t) { if ($nodeByType.ContainsKey($t)) { [int]$nodeByType[$t] } else { 0 } }
function EdgeCount($t) { if ($edgeByType.ContainsKey($t)) { [int]$edgeByType[$t] } else { 0 } }

# Name-level diff so a recall gap is inspectable (tool-miss vs oracle-overcount).
$toolTypeNames = New-Object System.Collections.Generic.HashSet[string]
foreach ($n in $graph.nodes) {
    if ($n.type -in @('Class', 'DBModel', 'Interface', 'Enum', 'Record') -and $n.name) {
        [void]$toolTypeNames.Add($n.name)
    }
}
$gtTypeNames = New-Object System.Collections.Generic.HashSet[string]
foreach ($mm in [regex]::Matches($allText, '(?m)\b(?:class|interface|enum)\s+(\w+)')) {
    [void]$gtTypeNames.Add($mm.Groups[1].Value)
}
$typesMissing = @($gtTypeNames | Where-Object { -not $toolTypeNames.Contains($_) } | Sort-Object)
$typesSpurious = @($toolTypeNames | Where-Object { -not $gtTypeNames.Contains($_) } | Sort-Object)

$toolClasses    = NodeCount 'Class'
$toolDbModels   = NodeCount 'DBModel'
$toolInterfaces = NodeCount 'Interface'
$toolEnums      = NodeCount 'Enum'
$toolRecords    = NodeCount 'Record'
$toolMethods    = NodeCount 'Method'
$toolEndpoints  = NodeCount 'APIEndpoint'

$toolCalls      = EdgeCount 'CALLS'
$toolHasMethod  = EdgeCount 'HAS_METHOD'
$toolHandles    = EdgeCount 'HANDLES_ROUTE'
$toolInjects    = EdgeCount 'INJECTS'

# --- 4. Metrics (recall vs independent ground truth) ----------------------------------------
function Recall($tool, $gt) { if ($gt -le 0) { return "n/a" } return [math]::Round(100.0 * [math]::Min($tool, $gt) / $gt, 1) }

$toolClassLike = $toolClasses + $toolDbModels + $toolRecords
$gtClassLike   = $gtClasses + $gtRecords
$toolTypesAll  = $toolClassLike + $toolInterfaces + $toolEnums
$typeRecall    = Recall $toolTypesAll $gtTypes
$endpointRecall = Recall $toolEndpoints $gtEndpoints
$entityMatch    = Recall $toolDbModels $gtEntities
$classRecall    = Recall $toolClassLike $gtClassLike
$ifaceRecall    = Recall $toolInterfaces $gtInterfaces
$enumRecall     = Recall $toolEnums $gtEnums

# --- 5. Emit report -------------------------------------------------------------------------
$stamp = Get-Date -Format "yyyy-MM-dd HH:mm"
$md = @()
$md += "# Structural accuracy - $Name"
$md += ""
$md += "Generated: $stamp  |  Project id: ``$projectId``  |  Source files (main): $($javaFiles.Count)"
$md += ""
$md += "Ground truth is counted directly from .java source with regex (independent of the"
$md += "JavaParser engine under test). Recall = min(tool, gt) / gt. Approximate oracle - see caveats."
$md += ""
$md += "## Nodes"
$md += ""
$md += "| Element | Tool | Ground truth (source) | Recall % |"
$md += "|---|---:|---:|---:|"
$md += "| Types (class+iface+enum+record) | $toolTypesAll | $gtTypes | $typeRecall |"
$md += "| - Class (incl. DBModel) | $toolClassLike | $gtClassLike | $classRecall |"
$md += "| - Interface | $toolInterfaces | $gtInterfaces | $ifaceRecall |"
$md += "| - Enum | $toolEnums | $gtEnums | $enumRecall |"
$md += "| Entities (Entity to DBModel) | $toolDbModels | $gtEntities | $entityMatch |"
$md += "| REST/MVC endpoints | $toolEndpoints | $gtEndpoints | $endpointRecall |"
$md += "| Methods | $toolMethods | (not counted in P1) | n/a |"
$md += ""
$md += "Context: Controller annotations in source = $gtControllers; RequestMapping occurrences = $gtReqMapping."
$md += ""
$md += "## Edges (tool distribution)"
$md += ""
$md += "| Edge type | Count |"
$md += "|---|---:|"
$md += "| CALLS (method-call resolution) | $toolCalls |"
$md += "| HAS_METHOD | $toolHasMethod |"
$md += "| HANDLES_ROUTE | $toolHandles |"
$md += "| INJECTS | $toolInjects |"
$md += ""
$md += "## Caveats (threats to validity)"
$md += "- Ground truth is regex-derived on comment/string-stripped source; the NAME-level diff"
$md += "  below (0 missing / 0 spurious = exact) is the authoritative signal, not raw counts."
$md += "- Method-call PRECISION requires stratified manual sampling (Phase 2), not counted here."
$md += "- Endpoint count uses the 5 verb-mapping annotations; class-level RequestMapping excluded."
$md += "- INJECTS reflects only captured dependency edges; constructor injection may be under-counted."
$md += ""
$md += "## Type-name diff (diagnosis)"
$md += ""
$md += "In source but NOT in tool graph ($($typesMissing.Count)): " + ($typesMissing -join ", ")
$md += ""
$md += "In tool graph but NOT matched in source-regex ($($typesSpurious.Count)): " + ($typesSpurious -join ", ")
$md -join "`n" | Out-File -Encoding utf8 (Join-Path $OutDir "report.md")

$csv = @()
$csv += "element,tool,ground_truth,recall_pct"
$csv += "types,$toolTypesAll,$gtTypes,$typeRecall"
$csv += "class_incl_dbmodel,$toolClassLike,$gtClassLike,$classRecall"
$csv += "interface,$toolInterfaces,$gtInterfaces,$ifaceRecall"
$csv += "enum,$toolEnums,$gtEnums,$enumRecall"
$csv += "entity,$toolDbModels,$gtEntities,$entityMatch"
$csv += "endpoints,$toolEndpoints,$gtEndpoints,$endpointRecall"
$csv -join "`n" | Out-File -Encoding utf8 (Join-Path $OutDir "report.csv")

Write-Output "OK projectId=$projectId typesTool=$toolTypesAll typesGt=$gtTypes endpointsTool=$toolEndpoints endpointsGt=$gtEndpoints methods=$toolMethods calls=$toolCalls"
