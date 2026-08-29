#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$DiagramRoot = Join-Path $RepositoryRoot 'Diagram\diagram update'
$OutputPath = Join-Path $DiagramRoot 'VibeGraph_All_PlantUML_Diagrams.md'
$sources = [ordered]@{
    'plantuml_usecase.md' = 'use cases'
    'plantuml_activity.md' = 'activities'
    'plantuml_erd_component_class.md' = 'ERD, components and classes'
}

$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# VibeGraph - All Verified PlantUML Diagrams')
$lines.Add('')
$lines.Add('This generated file is the complete combined mirror of the three canonical verified')
$lines.Add('PlantUML sources. Edit the canonical file for a diagram family, then rerun')
$lines.Add('`scripts/sync-diagram-plantuml.ps1`. Evidence baselines and old-to-current decisions are')
$lines.Add('recorded in `BASELINE-MANIFEST.md` and `changes/`.')
$lines.Add('')

foreach ($entry in $sources.GetEnumerator()) {
    $path = Join-Path $DiagramRoot $entry.Key
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing canonical source: $path"
    }
    $content = (Get-Content -LiteralPath $path -Raw).TrimEnd()
    $lines.Add('---')
    $lines.Add('')
    $lines.Add("## Canonical copy: $($entry.Value)")
    $lines.Add('')
    $lines.Add("<!-- canonical-copy-begin: $($entry.Key) -->")
    $lines.Add($content)
    $lines.Add("<!-- canonical-copy-end: $($entry.Key) -->")
    $lines.Add('')
}

[IO.File]::WriteAllText($OutputPath, ($lines -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
Write-Output "Synchronized combined PlantUML mirror: $OutputPath"
