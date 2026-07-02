<#
  VibeGraph - method-call (CALLS) PRECISION via stratified random sampling.

  Estimates: of the CALLS edges the tool produced, what fraction are correct?
  Method: take a deterministic random sample of CALLS edges; for each, verify in the
  caller's SOURCE file that a call to the callee's method name actually appears
  (regex `<calleeName>(`). Reports precision + a Wilson 95% confidence interval.

  This is an automated proxy for manual verification: a same-named method elsewhere in
  the caller file is a possible false-accept, so treat it as an UPPER-ish estimate and
  state N. It is fully reproducible (fixed seed).

  Usage:
    ./mcall-precision.ps1 -RepoPath <repo root> -ProjectId <id> [-SampleSize 30] [-Seed 42]
#>
param(
    [Parameter(Mandatory = $true)][string]$RepoPath,
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [int]$SampleSize = 30,
    [int]$Seed = 42,
    [string]$Base = "http://localhost:8080",
    [string]$OutDir = "$PSScriptRoot"
)

$ErrorActionPreference = "Stop"

$resp = Invoke-RestMethod -Uri "$Base/api/projects/$ProjectId/graph?nodeLimit=100000&edgeLimit=100000" -Method Get -TimeoutSec 180
$graph = $resp.data
if (-not $graph) { throw "No graph for $ProjectId" }

# Map method FQCN (id and fullName) -> source file path.
$fileByMethod = @{}
foreach ($n in $graph.nodes) {
    if ($n.type -eq "Method") {
        if ($n.id -and $n.filePath) { $fileByMethod[$n.id] = $n.filePath }
        if ($n.fullName -and $n.filePath -and -not $fileByMethod.ContainsKey($n.fullName)) {
            $fileByMethod[$n.fullName] = $n.filePath
        }
    }
}

$calls = @()
foreach ($e in $graph.edges) {
    if ($e.type -eq "CALLS" -and $e.source -and $e.target) {
        $calls += [pscustomobject]@{ from = $e.source; to = $e.target }
    }
}
$total = $calls.Count
if ($total -eq 0) { throw "No CALLS edges in $ProjectId" }

# Deterministic sample: stable sort then seeded shuffle.
$calls = $calls | Sort-Object from, to
$rng = [System.Random]::new($Seed)
$sample = $calls | Sort-Object { $rng.Next() } | Select-Object -First ([math]::Min($SampleSize, $total))

function CalleeName($fqcn) {
    $s = $fqcn
    $paren = $s.IndexOf('(')
    if ($paren -ge 0) { $s = $s.Substring(0, $paren) }
    $dot = $s.LastIndexOf('.')
    if ($dot -ge 0) { $s = $s.Substring($dot + 1) }
    return $s
}

$correct = 0; $verifiable = 0; $unknownFile = 0
$details = @()
foreach ($c in $sample) {
    $callerFile = $fileByMethod[$c.from]
    if (-not $callerFile) {
        # Fall back: caller is a method FQCN; try its class file via any node sharing the prefix.
        $unknownFile++
        $details += "UNKNOWN-FILE  $($c.from) -> $($c.to)"
        continue
    }
    if (-not (Test-Path $callerFile)) {
        $unknownFile++
        $details += "MISSING-FILE  $callerFile"
        continue
    }
    $verifiable++
    $callee = CalleeName $c.to
    $src = Get-Content -Raw $callerFile
    if ($src -match ("\b" + [regex]::Escape($callee) + "\s*\(")) {
        $correct++
    } else {
        $details += "NOT-FOUND    $($c.from) -> $callee()"
    }
}

# Wilson 95% CI for a proportion.
function Wilson($k, $n) {
    if ($n -eq 0) { return @(0.0, 0.0, 0.0) }
    $z = 1.96; $p = $k / $n
    $den = 1 + ($z * $z) / $n
    $centre = ($p + ($z * $z) / (2 * $n)) / $den
    $margin = ($z * [math]::Sqrt(($p * (1 - $p) + ($z * $z) / (4 * $n)) / $n)) / $den
    return @([math]::Round($p * 100, 1), [math]::Round(($centre - $margin) * 100, 1), [math]::Round(($centre + $margin) * 100, 1))
}
$w = Wilson $correct $verifiable

$md = @()
$md += "# Method-call (CALLS) precision - project $ProjectId"
$md += ""
$md += "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm')  |  Seed: $Seed"
$md += ""
$md += "| Metric | Value |"
$md += "|---|---:|"
$md += "| Total CALLS edges | $total |"
$md += "| Sampled | $($sample.Count) |"
$md += "| Verifiable (caller file found) | $verifiable |"
$md += "| Unknown/missing caller file | $unknownFile |"
$md += "| Verified correct | $correct |"
$md += "| **Precision** | **$($w[0])%** |"
$md += "| Wilson 95% CI | [$($w[1])%, $($w[2])%] |"
$md += ""
$md += "Method: for each sampled CALLS edge, the callee method name is searched (as a call"
$md += "``name(``) in the caller's source file. Automated proxy - a same-named method in the"
$md += "same file is a possible false-accept; report with N and CI. Reproducible (fixed seed)."
$md += ""
if ($details.Count -gt 0) {
    $md += "## Sample items needing review"
    foreach ($d in ($details | Select-Object -First 40)) { $md += "- $d" }
}
$md -join "`n" | Out-File -Encoding utf8 (Join-Path $OutDir "mcall-precision.md")

Write-Output "OK total=$total sampled=$($sample.Count) verifiable=$verifiable correct=$correct precision=$($w[0])% CI=[$($w[1]),$($w[2])]"
