$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker with Compose is required. Install and start Docker before running the demo.'
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.env'))) {
    throw 'Copy .env.example to .env and set DB_PASSWORD before starting.'
}
Push-Location $projectRoot
try {
    & docker compose up --build
    if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. Check the Docker output above.' }
} finally { Pop-Location }
