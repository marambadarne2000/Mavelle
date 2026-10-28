$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Recovery-Support.ps1')
$root=Split-Path -Parent $PSScriptRoot
$directory=Join-Path $root ('.runtime/backups/guard_'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $directory
$manifest=Join-Path $directory 'manifest.json'
$archive=Join-Path $directory 'application.dump'
$context=@{root=$root}
function Assert-Rejected([scriptblock]$Operation){
 $rejected=$false
 try{& $Operation|Out-Null}catch{$rejected=$true}
 if(!$rejected){throw 'Invalid restore input was accepted.'}
}
try{
 Set-Content -LiteralPath $archive -Value 'synthetic guard fixture, not a real database archive'
 $data=@{formatVersion=1;schema='public';archive='application.dump';sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash}
 $data|ConvertTo-Json|Set-Content -LiteralPath $manifest
 $null=Get-VerifiedBackup $manifest $context
 Add-Content -LiteralPath $archive -Value 'changed after checksum'
 Assert-Rejected {Get-VerifiedBackup $manifest $context}
 $data.archive='../another.dump'
 $data|ConvertTo-Json|Set-Content -LiteralPath $manifest
 Assert-Rejected {Get-VerifiedBackup $manifest $context}
 Assert-Rejected {Get-VerifiedBackup (Join-Path $root 'manifest.json') $context}
 Write-Host 'PASS: restore refuses a modified archive, archive path escape and manifest outside the local backup directory.'
}finally{
 Remove-Item -LiteralPath $manifest,$archive -ErrorAction SilentlyContinue
 Remove-Item -LiteralPath $directory
}
