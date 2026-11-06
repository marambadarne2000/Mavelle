param([string]$Sender,[string]$Recipient,[string]$PublicUrl='http://127.0.0.1:5173',[switch]$Restart)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$null=New-Item -ItemType Directory -Path (Join-Path $root '.runtime') -Force
$statusPath=Join-Path $root '.runtime/mail-setup-status.json'
$script:smtpAccepted=$false
function Save-MailSetupStatus([string]$phase,[string]$reason=''){[ordered]@{phase=$phase;reason=$reason;smtpAccepted=$script:smtpAccepted;updatedAt=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath $statusPath -Encoding utf8}
Save-MailSetupStatus 'waiting-for-input'
try{
 if(!$IsWindows){throw 'This local setup uses Windows DPAPI and must run on Windows.'}
 Write-Host 'Mavelle - Gmail SMTP setup' -ForegroundColor Yellow
 Write-Host 'Use a Google App Password, not your normal account password.'
 Write-Host 'Google requires 2-Step Verification for App Passwords: https://myaccount.google.com/apppasswords'
 Write-Host 'The password input below is hidden and is never saved as plain text.'
 if(!$Sender){$Sender=(Read-Host 'Gmail sender address').Trim()}
 $from=[Net.Mail.MailAddress]::new($Sender)
 if($from.Address -ne $Sender -or $from.Host -notin @('gmail.com','googlemail.com')){throw 'Enter a Gmail address. A business SMTP provider needs its own verified settings.'}
 if(!$Recipient){$Recipient=$Sender}
 $to=[Net.Mail.MailAddress]::new($Recipient)
 if($to.Address -ne $Recipient){throw 'Enter a plain recipient email address.'}
 $base=$PublicUrl.Trim()
 $uri=[uri]$base
 if(!$uri.IsAbsoluteUri -or !$uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or !($uri.Scheme -eq 'https' -or ($uri.Scheme -eq 'http' -and $uri.Host -eq '127.0.0.1'))){throw 'Use an HTTPS URL or http://127.0.0.1:5173 for the local demo.'}
 $password=Read-Host 'Google App Password (16 characters, hidden)' -AsSecureString
 if($password.Length -eq 0){throw 'No App Password entered; nothing was changed.'}
 $typed=[Management.Automation.PSCredential]::new($Sender,$password)
 $normalized=$typed.GetNetworkCredential().Password.Replace(' ','')
 if($normalized.Length -ne 16){throw 'A Google App Password has 16 characters. Use the App Password, not the ordinary account password.'}
 $password=ConvertTo-SecureString $normalized -AsPlainText -Force
 $normalized=$null;$typed=$null
 $credential=[Management.Automation.PSCredential]::new($Sender,$password)
 Write-Host "One test message will be sent from $Sender to $Recipient."
 $confirmed=Read-Host 'Send the test message now? Type YES'
 if($confirmed.Trim().ToLowerInvariant() -notin @('yes','y','כן')){Save-MailSetupStatus 'cancelled';Write-Host 'Cancelled. No message sent and no settings changed.';exit 0}
 $smtp=[Net.Mail.SmtpClient]::new('smtp.gmail.com',587)
 $message=[Net.Mail.MailMessage]::new()
 try{
  $smtp.EnableSsl=$true;$smtp.UseDefaultCredentials=$false;$smtp.Credentials=$credential.GetNetworkCredential();$smtp.Timeout=15000
  $message.From=[Net.Mail.MailAddress]::new($Sender,'Mavelle');$message.To.Add($Recipient)
  $message.Subject='Mavelle - email delivery test'
  $message.SubjectEncoding=[Text.Encoding]::UTF8;$message.BodyEncoding=[Text.Encoding]::UTF8
  $message.Body="Mavelle email delivery test.`r`n`r`nIf you received this, Gmail accepted and delivered the test message.`r`nThis message contains no account passwords or verification tokens."
  try{$smtp.Send($message)}catch{throw 'Gmail did not accept the test message. Check the App Password, 2-Step Verification, sender and network. No settings were saved.'}
 }finally{$message.Dispose();$smtp.Dispose()}
 $script:smtpAccepted=$true
 Save-MailSetupStatus 'provider-accepted-test'
 Write-Host 'Gmail accepted the message. Check Inbox and Spam to confirm arrival.' -ForegroundColor Green
 $directory=Join-Path $root '.runtime/mail-config'
 $null=New-Item -ItemType Directory -Path $directory -Force
 if((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked credential directories are not accepted.'}
 $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
 $identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
 $acl.SetOwner($identity)
 foreach($sid in @($identity,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
 Set-Acl -LiteralPath $directory -AclObject $acl
 [pscustomobject]@{Version=1;Host='smtp.gmail.com';Port=587;From=$Sender;PublicUrl=$uri.AbsoluteUri.TrimEnd('/');Credential=$credential}|Export-Clixml -LiteralPath (Join-Path $directory 'settings.clixml')
 Save-MailSetupStatus 'settings-saved'
 Write-Host 'Encrypted settings saved locally for this Windows account; excluded from Git.' -ForegroundColor Green
 if($uri.Host -eq '127.0.0.1'){Write-Host 'Local links work only on this computer. External recipients need a public HTTPS website URL.'}
 if($Restart){& (Join-Path $PSScriptRoot 'Start-Local.ps1') -Restart;if($LASTEXITCODE -ne 0){throw 'Settings were saved, but the application restart failed.'};if((Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/options' -TimeoutSec 10).deliveryMode -ne 'smtp'){throw 'The application did not activate SMTP.'};Save-MailSetupStatus 'active'}
 else{Write-Host 'Run ./scripts/Start-Local.ps1 -Restart to activate SMTP.'}
 Write-Host 'Setup finished. No customer or merchant account was created.' -ForegroundColor Green
}catch{
 $failure=$_.Exception.Message
 $reason=if($failure.StartsWith('A Google App Password')){'app-password-required'}elseif($failure.StartsWith('Gmail did not accept')){'smtp-rejected'}elseif($failure.StartsWith('Enter a Gmail address')){'invalid-sender'}elseif($failure.StartsWith('Enter a plain recipient')){'invalid-recipient'}elseif($failure.StartsWith('Use an HTTPS URL')){'invalid-link-url'}else{'configuration-error'}
 Save-MailSetupStatus 'needs-attention' $reason
 Write-Host $failure -ForegroundColor Red
}
finally{Read-Host 'Press Enter to close this window'|Out-Null}
