<#
  VibeGraph - stop the local dev stack started by dev-up.ps1.
  Kills the backend (Spring Boot on :8080) and frontend (Vite on :5173),
  and optionally stops the Neo4j container (keep it running by default so the
  graph data volume stays warm between demos).

  Usage:  powershell -ExecutionPolicy Bypass -File scripts/dev-down.ps1 [-StopNeo4j]
#>
param(
    [switch]$StopNeo4j
)

function KillPort($port, $label) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "    no listener on :$port ($label)"
        return
    }
    foreach ($procId in ($conns.OwningProcess | Select-Object -Unique)) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "    stopped $label (pid $procId on :$port)"
        } catch {
            Write-Host "    could not stop pid $procId on :$port : $($_.Exception.Message)"
        }
    }
}

Write-Host "==> stopping frontend (:5173)"
KillPort 5173 "frontend"
Write-Host "==> stopping backend (:8080)"
KillPort 8080 "backend"

if ($StopNeo4j) {
    Write-Host "==> stopping Neo4j container"
    docker stop vibegraph-neo4j | Out-Null
    Write-Host "    neo4j stopped."
} else {
    Write-Host "==> leaving Neo4j running (pass -StopNeo4j to stop it)"
}
Write-Host "done."
