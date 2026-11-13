param([string]$OutputName='mavelle-portfolio-source.zip')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if($OutputName -notmatch '^[A-Za-z0-9_-]+\.zip$'){throw 'Choose a plain .zip filename.'}
Push-Location $root
try {
 if(git status --porcelain){throw 'Commit the reviewed source before packaging; private/unreviewed files are not exported.'}
 python scripts/check_repository.py
 if($LASTEXITCODE -ne 0){throw 'Source policy failed.'}
 $destination=Join-Path $root 'artifacts'
 $null=New-Item -ItemType Directory -Path $destination -Force
 $output=Join-Path $destination $OutputName
 git archive --format=zip --output=$output HEAD
 if($LASTEXITCODE -ne 0){throw 'Source archive failed.'}
 Write-Host 'Created a tracked-source-only archive under artifacts; runtime data, media uploads, credentials and local tools are excluded.'
 Get-Item -LiteralPath $output | Select-Object FullName,Length
} finally {Pop-Location}
