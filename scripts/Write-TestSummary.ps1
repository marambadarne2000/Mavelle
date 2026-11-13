param([string]$ReportPath='automation/target/full-reports')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$reports=@(Get-ChildItem (Join-Path $projectRoot $ReportPath) -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
$lines=@('## Selenium results','','| Suite | Tests | Failures | Errors | Skipped |','|---|---:|---:|---:|---:|')
foreach($file in $reports){
 [xml]$report=Get-Content -LiteralPath $file.FullName -Raw
 $suite=$report.testsuite
 $name=([string]$suite.name).Replace('|','/').Replace('<','&lt;').Replace('>','&gt;')
 $lines+="| $name | $($suite.tests) | $($suite.failures) | $($suite.errors) | $($suite.skipped) |"
}
if(!$reports.Count){$lines+='No completed Selenium report was produced. Inspect the job steps for setup or startup failures.'}
$text=$lines -join [Environment]::NewLine
if($env:GITHUB_STEP_SUMMARY){Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Value $text -Encoding utf8}else{Write-Output $text}
