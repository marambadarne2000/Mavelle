param([switch]$SkipBrowser)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Node-Runtime.ps1')
Enable-ProjectNode
Push-Location $projectRoot
try{
 & (Join-Path $PSScriptRoot 'Setup-QualityTools.ps1')
 $suffix=if($IsWindows){'.exe'}else{''}
 & (Join-Path $projectRoot ".tools/quality/actionlint/actionlint$suffix") -color
 if($LASTEXITCODE -ne 0){throw 'Workflow validation failed.'}
 python scripts/check_repository.py
 if($LASTEXITCODE -ne 0){throw 'Repository publication policy failed.'}
 python scripts/test_repository_policy.py
 if($LASTEXITCODE -ne 0){throw 'Repository policy regressions failed.'}
 & (Join-Path $PSScriptRoot 'Test-RecoveryGuards.ps1')
 & (Join-Path $projectRoot ".tools/quality/gitleaks/gitleaks$suffix") dir . --config .gitleaks.toml --redact=100 --report-format=json --report-path=.tools/gitleaks-report.json
 if($LASTEXITCODE -ne 0){throw 'Secret scanning failed; inspect the redacted local report.'}
 foreach($command in @('lint','typecheck')){& npm --prefix frontend run $command;if($LASTEXITCODE -ne 0){throw "Frontend $command failed."}}
 & npm --prefix frontend audit --registry=https://registry.npmjs.org --audit-level=moderate --fetch-timeout=15000 --fetch-retries=1 --fetch-retry-mintimeout=1000 --fetch-retry-maxtimeout=2000
 if($LASTEXITCODE -ne 0){throw 'Dependency audit failed.'}
 python scripts/test_java_audit.py
 if($LASTEXITCODE -ne 0){throw 'Java audit regression tests failed.'}
 & (Join-Path $PSScriptRoot 'Audit-Java.ps1')
 & (Join-Path $PSScriptRoot 'Check.ps1') -Integration
 if(!$SkipBrowser){& (Join-Path $PSScriptRoot 'Test-Automation.ps1') -Full; & (Join-Path $PSScriptRoot 'Write-TestSummary.ps1')}
 else{Write-Host 'Browser verification was explicitly skipped; previous browser reports are not counted.'}
 Write-Host 'PASS: local CI rehearsal. This does not certify a GitHub-hosted workflow run.'
}finally{Pop-Location}
