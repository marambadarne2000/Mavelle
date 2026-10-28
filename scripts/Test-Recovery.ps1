$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Recovery-Support.ps1')
$context=Get-RecoveryContext
$previousPassword=$env:PGPASSWORD
$previousOptions=$env:PGOPTIONS
$database=$null
$backend=$null
$savedEnvironment=@{}
$report=[ordered]@{checkedAt=[DateTime]::UtcNow.ToString('o');status='failed';databaseRemoved=$false}
$run=Join-Path $context.root ('.runtime/recovery/'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $run
try{
 $env:PGPASSWORD=$context.config.password
 $env:PGOPTIONS='-c timezone=UTC'
 $before=Get-DatabaseFingerprint $context.config.database $context
 $backup=& (Join-Path $PSScriptRoot 'Backup-Local.ps1')
 $restore=& (Join-Path $PSScriptRoot 'Restore-Backup.ps1') -Manifest $backup.Manifest
 $database=$restore.Database
 $restored=Get-DatabaseFingerprint $database $context
 $after=Get-DatabaseFingerprint $context.config.database $context
 if($before -ne $after){throw 'Source changed during verification. Backup remains valid, but equality is inconclusive; rerun while no one edits or orders.'}
 if($before -ne $restored){throw 'Restored data, sequences or constraints differ from source.'}
 $probe=@'
BEGIN;
DO $probe$
DECLARE customer_id bigint; constraint_name text;
BEGIN
 INSERT INTO users(email,password_hash,role) VALUES ('recovery-'||gen_random_uuid()||'@example.test','synthetic-not-a-login','CUSTOMER') RETURNING id INTO customer_id;
 BEGIN
  INSERT INTO users(email,password_hash,role) VALUES ('recovery-'||gen_random_uuid()||'@example.test','synthetic-not-a-login','INVALID');
  RAISE EXCEPTION 'Role CHECK did not reject invalid value';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS constraint_name = CONSTRAINT_NAME;
  IF constraint_name <> 'users_role_check' THEN RAISE EXCEPTION 'Unexpected role constraint'; END IF;
 END;
 BEGIN
  INSERT INTO orders(order_number,user_id,total_price,status,shipping,idempotency_key,request_fingerprint) VALUES ('REC-'||gen_random_uuid(),customer_id,10,'INVALID','{}','probe-status','probe');
  RAISE EXCEPTION 'Status CHECK did not reject invalid value';
 EXCEPTION WHEN check_violation THEN
  GET STACKED DIAGNOSTICS constraint_name = CONSTRAINT_NAME;
  IF constraint_name <> 'orders_status_check' THEN RAISE EXCEPTION 'Unexpected status constraint'; END IF;
 END;
 BEGIN
  INSERT INTO orders(order_number,user_id,total_price,status,shipping,idempotency_key,request_fingerprint) VALUES ('REC-'||gen_random_uuid(),-1,10,'PENDING','{}','probe-owner','probe');
  RAISE EXCEPTION 'Foreign key did not reject missing user';
 EXCEPTION WHEN foreign_key_violation THEN
  GET STACKED DIAGNOSTICS constraint_name = CONSTRAINT_NAME;
  IF constraint_name <> 'orders_user_id_fkey' THEN RAISE EXCEPTION 'Unexpected ownership constraint'; END IF;
 END;
END;
$probe$;
ROLLBACK;
'@
 $probe | & (Join-Path $context.bin 'psql.exe') -X -w -h 127.0.0.1 -p $context.config.port -U $context.config.user -d $database -v ON_ERROR_STOP=1 -q
 if($LASTEXITCODE -ne 0){throw 'Restored database write/constraint probes failed.'}
 $parsed=$restored|ConvertFrom-Json
 $report.tablesChecked=@($parsed.tables.PSObject.Properties).Count
 $report.rowsChecked=($parsed.tables.PSObject.Properties.Value.rows|Measure-Object -Sum).Sum
 $report.backupManifest=$backup.Manifest
 $report.constraintProbes=3
 $jar=Join-Path $context.root 'backend/target/maramigo-api-0.1.0.jar'
 if(!(Test-Path -LiteralPath $jar)){throw 'Build the backend before verifying application recovery.'}
 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
 $listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
 $changes=@{DB_URL="jdbc:postgresql://127.0.0.1:$($context.config.port)/$database";DB_USER=$context.config.user;DB_PASSWORD=$context.config.password;SERVER_PORT=[string]$port;SERVER_ADDRESS='127.0.0.1';SPRING_PROFILES_ACTIVE='demo';SPRING_APPLICATION_JSON='{}';SESSION_COOKIE_SECURE='false';DEMO_ADMIN_EMAIL=$context.config.demoAdminEmail;DEMO_ADMIN_PASSWORD=$context.config.demoAdminPassword}
 foreach($key in $changes.Keys){$savedEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,[string]$changes[$key],'Process')}
 $backend=Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar',$jar) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'backend.log') -RedirectStandardError (Join-Path $run 'backend-error.log')
 $healthy=$false
 for($attempt=0;$attempt -lt 60;$attempt++){
  try{$health=Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2;if($health.status -eq 'UP'){$healthy=$true;break}}catch{}
  if($backend.HasExited){break}
  Start-Sleep -Seconds 1
 }
 if(!$healthy){throw 'Recovered application did not become healthy; inspect the private recovery logs.'}
 $catalog=Invoke-RestMethod "http://127.0.0.1:$port/api/products" -TimeoutSec 10
 if($null -eq $catalog.items -or $catalog.total -lt 0){throw 'Recovered catalog API returned invalid data.'}
 $report.apiHealthVerified=$true
 $report.catalogVerified=$true
 $report.status='passed'
 Write-Host 'PASS: rows, migration history, sequences and constraint metadata match; restored database rejects three invalid writes.'
 Write-Host 'PASS: separate Java server started against the restored database; health and catalog API responded.'
}finally{
 try{
  if($backend -and !$backend.HasExited){$backend|Stop-Process;$backend.WaitForExit(10000)|Out-Null}
  if($database){
   if($database -notmatch '^recovery_[a-f0-9]{32}$' -or $database -eq $context.config.database){throw 'Unsafe cleanup target refused.'}
   $env:PGPASSWORD=$context.config.adminPassword
   & (Join-Path $context.bin 'dropdb.exe') -w -h 127.0.0.1 -p $context.config.port -U maramigo_local_admin $database
   if($LASTEXITCODE -ne 0){throw 'Could not remove generated recovery database.'}
   $report.databaseRemoved=$true
  }
 }finally{
  foreach($key in $savedEnvironment.Keys){[Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process')}
  $report|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding utf8
  $env:PGPASSWORD=$previousPassword
  $env:PGOPTIONS=$previousOptions
 }
 Write-Host 'Private verification report saved under .runtime/recovery; backup retained under .runtime/backups.'
}
