$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Recovery-Support.ps1')
$context=Get-RecoveryContext
$id=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'_'+[guid]::NewGuid().ToString('N')
$directory=Join-Path $context.root ".runtime/backups/$id"
$null=New-Item -ItemType Directory -Path $directory
$archive=Join-Path $directory 'application.dump'
$previous=$env:PGPASSWORD
try{
 $env:PGPASSWORD=$context.config.password
 & (Join-Path $context.bin 'pg_dump.exe') -w -h 127.0.0.1 -p $context.config.port -U $context.config.user -d $context.config.database --format=custom --schema=public --no-owner --no-acl --file=$archive
 if($LASTEXITCODE -ne 0){throw 'Backup failed; no valid manifest was produced.'}
 $manifest=[ordered]@{formatVersion=1;createdAt=[DateTime]::UtcNow.ToString('o');database=$context.config.database;schema='public';archive='application.dump';sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash}
 $manifestPath=Join-Path $directory 'manifest.json'
 $manifest|ConvertTo-Json|Set-Content -LiteralPath $manifestPath -Encoding utf8
 Write-Host 'Backup created in ignored .runtime/backups. Contains private application data; keep local.'
 [pscustomobject]@{Manifest=$manifestPath}
}finally{$env:PGPASSWORD=$previous}
