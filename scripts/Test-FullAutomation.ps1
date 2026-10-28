param([ValidateSet('chrome','edge')][string]$Browser='chrome',[switch]$Visible)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Node-Runtime.ps1')
Enable-ProjectNode
$jar=Join-Path $projectRoot 'backend/target/maramigo-api-0.1.0.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Build the backend first with Start-Local.ps1 -Restart -Build.'}
$newest=(Get-ChildItem (Join-Path $projectRoot 'backend/src/main') -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
$pomModified=(Get-Item (Join-Path $projectRoot 'backend/pom.xml')).LastWriteTime
if($pomModified -gt $newest){$newest=$pomModified}
if($newest -gt (Get-Item $jar).LastWriteTime){throw 'Backend JAR is stale. Rebuild with Start-Local.ps1 -Restart -Build before full E2E.'}
& (Join-Path $PSScriptRoot 'Start-Database.ps1')
$config=Get-Content (Join-Path $projectRoot '.runtime/local.json') -Raw|ConvertFrom-Json
$schema='e2e_'+[guid]::NewGuid().ToString('N')
if($schema -notmatch '^e2e_[a-f0-9]{32}$'){throw 'Invalid isolated schema identifier'}
$run=Join-Path $projectRoot ('.runtime/'+$schema)
$null=New-Item -ItemType Directory -Path $run
function Free-Port { $listener=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,0);$listener.Start();try{return $listener.LocalEndpoint.Port}finally{$listener.Stop()} }
$apiPort=Free-Port;$webPort=Free-Port;while($webPort -eq $apiPort){$webPort=Free-Port}
$adminEmail=$schema+'@example.test';$adminPassword=[guid]::NewGuid().ToString('N')+'-E2E!'
$changes=@{PGPASSWORD=$config.password;DB_URL="jdbc:postgresql://127.0.0.1:$($config.port)/$($config.database)?currentSchema=$schema";DB_USER=$config.user;DB_PASSWORD=$config.password;SPRING_PROFILES_ACTIVE='demo';SERVER_ADDRESS='127.0.0.1';SERVER_PORT="$apiPort";SESSION_COOKIE_SECURE='false';DEMO_ADMIN_EMAIL=$adminEmail;DEMO_ADMIN_PASSWORD=$adminPassword;MARAMIGO_LOGIN_LIMIT='200';MARAMIGO_API_TARGET="http://127.0.0.1:$apiPort";E2E_ADMIN_EMAIL=$adminEmail;E2E_ADMIN_PASSWORD=$adminPassword;E2E_ISOLATED_SCHEMA=$schema;SE_CACHE_PATH=(Join-Path $projectRoot '.tools/selenium');SE_AVOID_STATS='true'}
$changes['SPRING_APPLICATION_JSON']=@{spring=@{flyway=@{'default-schema'=$schema};jpa=@{properties=@{'hibernate.default_schema'=$schema}}};maramigo=@{'login-limit'=200}}|ConvertTo-Json -Depth 8 -Compress
$changes['MARAMIGO_VITE_CACHE']=Join-Path $run 'vite-cache'
$saved=@{};foreach($key in $changes.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,[string]$changes[$key],'Process')}
$psql=Join-Path $projectRoot '.tools/postgresql/pgsql/bin/psql.exe'
$backend=$null;$frontend=$null;$created=$false
try{
 & $psql -h 127.0.0.1 -p $config.port -U $config.user -d $config.database -v ON_ERROR_STOP=1 -c "CREATE SCHEMA $schema" | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Could not create isolated E2E schema'};$created=$true
 $backend=Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar',('"'+$jar+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'backend.log') -RedirectStandardError (Join-Path $run 'backend-error.log')
 $ready=$false;for($i=0;$i -lt 60;$i++){try{if((Invoke-RestMethod "http://127.0.0.1:$apiPort/actuator/health" -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};if($backend.HasExited){break};Start-Sleep -Seconds 1}
 if(!$ready){throw 'Isolated backend startup failed. Inspect the .runtime/e2e_* logs.'}
 $bootstrapReady=$false
 for($attempt=0;$attempt -lt 40;$attempt++){
  $bootstrap=& $psql -h 127.0.0.1 -p $config.port -U $config.user -d $config.database -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM $schema.users WHERE email='$adminEmail'"
  if($LASTEXITCODE -ne 0){throw 'Could not verify the isolated administrator schema'}
  if(($bootstrap -join '').Trim() -eq '1'){$bootstrapReady=$true;break}
  Start-Sleep -Milliseconds 250
 }
 if(!$bootstrapReady){throw 'Isolated administrator was not created in the expected schema'}
 $vite=Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'
 $frontend=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @(('"'+$vite+'"'),'--host','127.0.0.1','--port',"$webPort",'--strictPort') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'frontend.log') -RedirectStandardError (Join-Path $run 'frontend-error.log')
 $ready=$false;for($i=0;$i -lt 25;$i++){try{if((Invoke-WebRequest "http://127.0.0.1:$webPort/" -TimeoutSec 2).StatusCode -eq 200){$ready=$true;break}}catch{};if($frontend.HasExited){break};Start-Sleep -Seconds 1}
 if(!$ready){throw 'Isolated frontend startup failed'}
 $maven=Join-Path $projectRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd';if(!(Test-Path $maven)){$maven=(Get-Command mvn.cmd).Source}
 $headless=if($Visible){'false'}else{'true'}
 Push-Location (Join-Path $projectRoot 'automation')
 try{& $maven '-Dmaven.repo.local=../.tools/repository' '-Pfull-e2e' '-DreportDirectory=target/full-reports' "-DbaseUrl=http://127.0.0.1:$webPort" "-Dbrowser=$Browser" "-Dheadless=$headless" test;$result=$LASTEXITCODE}finally{Pop-Location}
 if($result -ne 0){throw 'Full E2E failed. See automation/target/full-reports and target/screenshots.'}
 Write-Host 'PASS: isolated full E2E. The normal demo application and data were not used.'
}finally{
 foreach($process in @($frontend,$backend)){if($null -ne $process -and !$process.HasExited){Stop-Process -Id $process.Id -ErrorAction SilentlyContinue;$null=$process.WaitForExit(10000)}}
 if($created){& $psql -h 127.0.0.1 -p $config.port -U $config.user -d $config.database -v ON_ERROR_STOP=1 -c "DROP SCHEMA $schema CASCADE" | Out-Null;if($LASTEXITCODE -ne 0){Write-Warning "Cleanup failed for isolated schema $schema"}else{Write-Host 'Isolated schema removed.'}}
 foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
}


