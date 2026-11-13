$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$specs=@(
 @{name='actionlint';version='1.7.12';repo='rhysd/actionlint';windows='windows_amd64.zip';linux='linux_amd64.tar.gz';windowsHash='6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9';linuxHash='8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8'},
 @{name='gitleaks';version='8.30.1';repo='gitleaks/gitleaks';windows='windows_x64.zip';linux='linux_x64.tar.gz';windowsHash='d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e';linuxHash='551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb'}
)
foreach($spec in $specs){
 $suffix=if($IsWindows){$spec.windows}else{$spec.linux};$expected=if($IsWindows){$spec.windowsHash}else{$spec.linuxHash}
 $folder=Join-Path $projectRoot ('.tools/quality/'+$spec.name);$null=New-Item -ItemType Directory -Path $folder -Force
 $archiveName="$($spec.name)_$($spec.version)_$suffix";$archive=Join-Path $folder $archiveName
 if(!(Test-Path -LiteralPath $archive)){Invoke-WebRequest "https://github.com/$($spec.repo)/releases/download/v$($spec.version)/$archiveName" -OutFile $archive}
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected){throw "$($spec.name) checksum mismatch; refusing to extract or run it."}
 if($IsWindows){Expand-Archive -LiteralPath $archive -DestinationPath $folder -Force}else{& tar -xzf $archive -C $folder $spec.name;if($LASTEXITCODE -ne 0){throw 'Tool extraction failed'}}
 Write-Host "$($spec.name) $($spec.version) verified and ready."
}
