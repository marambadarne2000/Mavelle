function Enable-ProjectNode {
 $root=Split-Path -Parent $PSScriptRoot
 $portable=Join-Path $root '.tools/node/node-v22.23.4-win-x64'
 if(Test-Path -LiteralPath (Join-Path $portable 'node.exe')){
  $env:PATH=$portable+[IO.Path]::PathSeparator+$env:PATH
 }
 $command=Get-Command node -ErrorAction Stop
 $raw=& $command.Source --version
 if($LASTEXITCODE -ne 0){throw 'Node version check failed.'}
 $version=[version]$raw.Trim().TrimStart('v')
 if(!(($version.Major -eq 22 -and $version.Minor -ge 13) -or $version.Major -ge 24)){
  throw 'Use Node 22.13+ or Node 24+. On Windows run scripts/Setup-PortableNode.ps1; system Node will not be replaced.'
 }
}
