$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Mail-Settings.ps1')
if(!$IsWindows){Write-Host 'Windows DPAPI checks are Windows-only.';return}
$root=Split-Path -Parent $PSScriptRoot
$directory=Join-Path $root ('.runtime/mail-settings-check-'+[guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $directory
try{
 $path=Join-Path $directory 'settings.clixml'
 if((Get-MavelleMailEnvironment -SettingsPath $path).Count -ne 0){throw 'Missing settings should preserve existing environment behavior.'}
 $secret=ConvertTo-SecureString 'Synthetic-local-test-only' -AsPlainText -Force
 $credential=[Management.Automation.PSCredential]::new('synthetic@gmail.com',$secret)
 $settings=[pscustomobject]@{Version=1;Host='smtp.gmail.com';Port=587;From='synthetic@gmail.com';PublicUrl='http://127.0.0.1:5173';Credential=$credential}
 $settings|Export-Clixml -LiteralPath $path
 if((Get-Content -LiteralPath $path -Raw).Contains('Synthetic-local-test-only')){throw 'Credential was not protected.'}
 $values=Get-MavelleMailEnvironment -SettingsPath $path
 if($values.ACCOUNT_MAIL_MODE -ne 'smtp' -or $values.ACCOUNT_MAIL_PASSWORD -ne 'Synthetic-local-test-only' -or $values.ACCOUNT_MAIL_HOST -ne 'smtp.gmail.com' -or $values.ACCOUNT_PUBLIC_URL -ne 'http://127.0.0.1:5173'){throw 'Protected settings did not round trip.'}
 $values.Clear()
 foreach($badUrl in @('http://external.example','https://example.test/#token','https://name@example.test')){
  $settings.PublicUrl=$badUrl;$settings|Export-Clixml -LiteralPath $path;$rejected=$false
  try{$discard=Get-MavelleMailEnvironment -SettingsPath $path}catch{$rejected=$true}
  if(!$rejected){throw 'Unsafe account-link URL was accepted.'}
 }
 $settings.PublicUrl='https://example.test';$settings.Port=465;$settings|Export-Clixml -LiteralPath $path;$rejected=$false
 try{$discard=Get-MavelleMailEnvironment -SettingsPath $path}catch{$rejected=$true}
 if(!$rejected){throw 'Unsupported implicit-TLS port was accepted.'}
 Write-Host 'PASS: DPAPI credential protection, safe link URLs, supported TLS port and missing-settings behavior. No real email was sent.'
}finally{
 $resolved=[IO.Path]::GetFullPath($directory);$allowed=[IO.Path]::GetFullPath((Join-Path $root '.runtime'))+[IO.Path]::DirectorySeparatorChar
 if(!$resolved.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $resolved) -notmatch '^mail-settings-check-[a-f0-9]{32}$'){throw 'Unsafe test cleanup path refused.'}
 Get-ChildItem -LiteralPath $resolved -File|ForEach-Object{Remove-Item -LiteralPath $_.FullName}
 Remove-Item -LiteralPath $resolved
}
