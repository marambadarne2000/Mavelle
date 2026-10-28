param([switch]$Build, [switch]$Restart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Node-Runtime.ps1')
Enable-ProjectNode
if ($Restart) { & (Join-Path $PSScriptRoot 'Stop-Local.ps1') }
function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}
if ((Test-Port 8080) -or (Test-Port 5173)) { throw 'An application port is in use. Use -Restart to stop this project before rebuilding, or free the occupied port.' }
& (Join-Path $PSScriptRoot 'Start-Database.ps1')
$config = Get-Content -LiteralPath (Join-Path $projectRoot '.runtime\local.json') -Raw | ConvertFrom-Json
if (-not $config.PSObject.Properties['demoAdminPassword']) {
    $randomBytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $config | Add-Member -NotePropertyName demoAdminEmail -NotePropertyValue 'maram@example.test'
    $config | Add-Member -NotePropertyName demoAdminPassword -NotePropertyValue ([Convert]::ToHexString($randomBytes).ToLowerInvariant())
    $config | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot '.runtime\local.json') -Encoding utf8
}
$jar = Join-Path $projectRoot 'backend\target\maramigo-api-0.1.0.jar'
if ($Build -or -not (Test-Path -LiteralPath $jar)) {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath = if ($maven) { $maven.Source } else { Join-Path $projectRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd' }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & $mavenPath '-Dmaven.repo.local=../.tools/repository' package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally { Pop-Location }
}
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($config.port)/$($config.database)"
$env:DB_USER = $config.user
$env:DB_PASSWORD = $config.password
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SESSION_COOKIE_SECURE = 'false'
$env:DEMO_ADMIN_EMAIL = $config.demoAdminEmail
$env:DEMO_ADMIN_PASSWORD = $config.demoAdminPassword
$runtimePath = Join-Path $projectRoot '.runtime'
$backend = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', $jar) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimePath 'backend.log') -RedirectStandardError (Join-Path $runtimePath 'backend-error.log')
$backend.Id | Set-Content -LiteralPath (Join-Path $runtimePath 'backend.pid')
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try { $health = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2; if ($health.status -eq 'UP') { $ready = $true; break } } catch { }
    if ($backend.HasExited) { break }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'Backend did not become healthy. See .runtime/backend*.log.' }
$vite = Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js'
$frontend = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @($vite, '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimePath 'frontend.log') -RedirectStandardError (Join-Path $runtimePath 'frontend-error.log')
$frontend.Id | Set-Content -LiteralPath (Join-Path $runtimePath 'frontend.pid')
$frontendReady = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try { $page = Invoke-WebRequest 'http://127.0.0.1:5173' -TimeoutSec 2; if ($page.StatusCode -eq 200) { $frontendReady = $true; break } } catch { }
    if ($frontend.HasExited) { break }
    Start-Sleep -Seconds 1
}
if (-not $frontendReady) { throw 'Frontend startup failed. Check .runtime/frontend*.log.' }
Write-Host 'Backend healthy. Storefront: http://127.0.0.1:5173'
