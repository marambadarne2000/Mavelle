param([ValidateSet('chrome','edge')][string]$Browser='chrome',[switch]$Visible,[switch]$Full)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
if($Full){& (Join-Path $PSScriptRoot 'Test-FullAutomation.ps1') -Browser $Browser -Visible:$Visible;return}
$env:SE_CACHE_PATH=Join-Path $projectRoot '.tools/selenium'
$env:SE_AVOID_STATS='true'
$maven=Join-Path $projectRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd'
if(!(Test-Path $maven)){$maven=(Get-Command mvn.cmd -ErrorAction Stop).Source}
$headless=if($Visible){'false'}else{'true'}
Push-Location (Join-Path $projectRoot 'automation')
try{& $maven '-Dmaven.repo.local=../.tools/repository' "-Dbrowser=$Browser" "-Dheadless=$headless" test;if($LASTEXITCODE -ne 0){throw 'Selenium verification failed. See automation/target/surefire-reports and target/screenshots.'}}
finally{Pop-Location}
