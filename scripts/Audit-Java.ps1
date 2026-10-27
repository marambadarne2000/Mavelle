$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$maven=Get-Command mvn -ErrorAction SilentlyContinue
$mavenPath=if($maven){$maven.Source}else{Join-Path $projectRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd'}
$repositoryArgs=if($env:GITHUB_ACTIONS -eq 'true'){@()}else{@("-Dmaven.repo.local=$projectRoot/.tools/repository")}
$inventories=@()
foreach($module in @('backend','automation')){
 $inventory=Join-Path $projectRoot "$module/target/dependency-audit.json"
 if(Test-Path -LiteralPath $inventory){Remove-Item -LiteralPath $inventory}
 & $mavenPath -B -f (Join-Path $projectRoot "$module/pom.xml") @repositoryArgs 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree' '-DoutputType=json' "-DoutputFile=$inventory"
 if($LASTEXITCODE -ne 0){throw "Cannot resolve $module dependencies; audit incomplete."}
 $inventories+=$inventory
}
& python (Join-Path $PSScriptRoot 'audit_java.py') --inventory @inventories --reviews (Join-Path $projectRoot 'security/dependency-reviews.json') --report (Join-Path $projectRoot 'artifacts/security/java-osv.json')
if($LASTEXITCODE -ne 0){throw 'Java dependency review failed; see artifacts/security/java-osv.json.'}
