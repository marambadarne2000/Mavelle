$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$tools=Join-Path $root '.tools'
$null=New-Item -ItemType Directory -Path $tools -Force
$version='22.23.4'
$archive=Join-Path $tools 'node.zip'
$expected='cbc74b5e0982c9dbb41525fd8fb5dcd32fbee237863f7829072f4639a1ce5f20'
if(!(Test-Path -LiteralPath $archive)){
 Invoke-WebRequest "https://nodejs.org/dist/v$version/node-v$version-win-x64.zip" -OutFile $archive
}
if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected){throw 'Node archive checksum mismatch; execution refused.'}
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $tools 'node') -Force
Write-Host "Portable Node $version is ready in .tools/node; system installation unchanged."
