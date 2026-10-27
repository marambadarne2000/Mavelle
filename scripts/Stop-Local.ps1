$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($component in @('frontend', 'backend')) {
    $pidPath = Join-Path $projectRoot ".runtime\$component.pid"
    if (Test-Path -LiteralPath $pidPath) {
        $processId = [int](Get-Content -LiteralPath $pidPath)
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
        # A stored PID may be reused. Only stop a process belonging to this project's path.
        if ($process -and $process.CommandLine -and $process.CommandLine.Contains($projectRoot)) {
            Stop-Process -Id $processId
        }
    }
}
$binPath = Join-Path $projectRoot '.tools\postgresql\pgsql\bin\pg_ctl.exe'
$dataPath = Join-Path $projectRoot '.runtime\postgres-data'
if ((Test-Path -LiteralPath $binPath) -and (Test-Path -LiteralPath (Join-Path $dataPath 'PG_VERSION'))) {
    & $binPath -D $dataPath status *> $null
    if ($LASTEXITCODE -eq 0) { & $binPath -D $dataPath -m fast -w stop }
}
Write-Host 'Local processes stopped. Database files were preserved.'
