param([Parameter(Mandatory)][string]$Manifest)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Recovery-Support.ps1')
$context=Get-RecoveryContext
$backup=Get-VerifiedBackup $Manifest $context
$database='recovery_'+[guid]::NewGuid().ToString('N')
if($database -notmatch '^recovery_[a-f0-9]{32}$' -or $database -eq $context.config.database){throw 'Unsafe recovery target.'}
$previous=$env:PGPASSWORD
$created=$false
$restored=$false
try{
 $env:PGPASSWORD=$context.config.adminPassword
 & (Join-Path $context.bin 'createdb.exe') -w -h 127.0.0.1 -p $context.config.port -U maramigo_local_admin -O $context.config.user -T template0 $database
 if($LASTEXITCODE -ne 0){throw 'Cannot create new recovery database.'}
 $created=$true
 $env:PGPASSWORD=$context.config.password
 # PostgreSQL templates include public; the archive recreates it. This target
 # was created above, has a generated name and has never served the application.
 & (Join-Path $context.bin 'psql.exe') -X -w -h 127.0.0.1 -p $context.config.port -U $context.config.user -d $database -v ON_ERROR_STOP=1 -q -c 'DROP SCHEMA public'
 if($LASTEXITCODE -ne 0){throw 'Cannot prepare the newly generated recovery database.'}
 & (Join-Path $context.bin 'pg_restore.exe') -w -h 127.0.0.1 -p $context.config.port -U $context.config.user -d $database --no-owner --no-acl --exit-on-error --single-transaction $backup.archive
 if($LASTEXITCODE -ne 0){throw 'Restore failed; partial recovery database will be removed.'}
 $restored=$true
 Write-Host 'Restored into a newly generated recovery database. Normal application was not redirected.'
 [pscustomobject]@{Database=$database;Manifest=$backup.path}
}finally{
 if($created -and !$restored){
  $env:PGPASSWORD=$context.config.adminPassword
  & (Join-Path $context.bin 'dropdb.exe') -w -h 127.0.0.1 -p $context.config.port -U maramigo_local_admin $database
  if($LASTEXITCODE -ne 0){Write-Warning "Failed to remove generated recovery database $database"}
 }
 $env:PGPASSWORD=$previous
}
