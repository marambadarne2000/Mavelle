$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Node-Runtime.ps1')
Enable-ProjectNode
$run=Join-Path $projectRoot ('.tools/clean-checkouts/'+[guid]::NewGuid().ToString('N'))
$source=Join-Path $run 'source'
$null=New-Item -ItemType Directory -Path $source
$report=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');status='failed';kind='fresh-source-build';dependencyCacheReused=$true;startsApplication=$false}
Push-Location $projectRoot
try{
 $revision=(& git rev-parse HEAD).Trim()
 if($LASTEXITCODE -ne 0){throw 'Git revision unavailable.'}
 $report.revision=$revision
 $report.nodeVersion=(& node --version).Trim()
 $archive=Join-Path $run 'source.tar'
 & git archive --format=tar --output=$archive HEAD
 if($LASTEXITCODE -ne 0){throw 'Source export failed.'}
 & tar.exe -xf $archive -C $source
 if($LASTEXITCODE -ne 0){throw 'Source extraction failed.'}
 foreach($private in @('.runtime','.tools','frontend/node_modules','backend/target','frontend/dist')){
  if(Test-Path -LiteralPath (Join-Path $source $private)){throw 'Export contains private/generated files.'}
 }
 $maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
 $mavenPath=if($maven){$maven.Source}else{Join-Path $projectRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd'}
 & $mavenPath -B -f (Join-Path $source 'backend/pom.xml') "-Dmaven.repo.local=$projectRoot/.tools/repository" package *> (Join-Path $run 'backend-build.log')
 if($LASTEXITCODE -ne 0){throw "Fresh-source Java build failed; inspect $run/backend-build.log"}
 & npm.cmd --prefix (Join-Path $source 'frontend') ci --registry=https://registry.npmjs.org --no-audit --no-fund *> (Join-Path $run 'npm-install.log')
 if($LASTEXITCODE -ne 0){throw "Fresh-source npm install failed; inspect $run/npm-install.log"}
 foreach($check in @('lint','typecheck','test','build')){
  & npm.cmd --prefix (Join-Path $source 'frontend') run $check *> (Join-Path $run "frontend-$check.log")
  if($LASTEXITCODE -ne 0){throw "Fresh-source frontend $check failed; inspect $run/frontend-$check.log"}
 }
 if(!(Test-Path -LiteralPath (Join-Path $source 'backend/target/maramigo-api-0.1.0.jar')) -or !(Test-Path -LiteralPath (Join-Path $source 'frontend/dist/index.html'))){throw 'Expected build outputs missing.'}
 $report.status='passed'
 Write-Host 'PASS: committed source built with fresh target/node_modules/dist, installed tools and an existing dependency cache.'
 Write-Host 'This is not certification of a new machine or an end-to-end deployment. No normal runtime/configuration was copied.'
}finally{
 $report|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding utf8
 Pop-Location
 Write-Host "Clean-source evidence: $run/result.json"
}
