param([switch]$Integration,[switch]$Automation)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Node-Runtime.ps1')
Enable-ProjectNode
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$localMaven = Join-Path $projectRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
if ($mavenCommand) { $mavenPath = $mavenCommand.Source }
elseif (Test-Path -LiteralPath $localMaven) { $mavenPath = $localMaven }
else { throw 'Maven is missing. Install Maven 3.6.3+ and reopen PowerShell.' }
if ($Integration -and -not $env:TEST_DB_URL) {
    $configPath = Join-Path $projectRoot '.runtime\local.json'
    if (Test-Path -LiteralPath $configPath) {
        & (Join-Path $PSScriptRoot 'Start-Database.ps1')
        $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
        $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$($config.port)/$($config.database)"
        $env:TEST_DB_USER = $config.user
        $env:TEST_DB_PASSWORD = $config.password
    }
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    # Direct test goals avoid replacing a JAR that Windows may have locked for the running demo.
    if ($Integration) { & $mavenPath '-Dmaven.repo.local=../.tools/repository' test failsafe:integration-test failsafe:verify -Pintegration }
    else { & $mavenPath '-Dmaven.repo.local=../.tools/repository' test }
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend basket model tests failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend verification failed.' }
} finally { Pop-Location }
if($Automation){ & (Join-Path $PSScriptRoot 'Test-Automation.ps1') }
