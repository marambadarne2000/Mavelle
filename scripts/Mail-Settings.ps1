$ErrorActionPreference='Stop'
function Get-MavelleMailEnvironment {
 param([string]$SettingsPath=(Join-Path (Split-Path -Parent $PSScriptRoot) '.runtime/mail-config/settings.clixml'))
 if(!(Test-Path -LiteralPath $SettingsPath)){return @{}}
 if(!$IsWindows){throw 'Local SMTP credentials require Windows DPAPI. Use process environment settings on other operating systems.'}
 $settings=Import-Clixml -LiteralPath $SettingsPath
 if($settings.Version -ne 1 -or $settings.Credential -isnot [Management.Automation.PSCredential] -or $settings.Credential.Password.Length -eq 0){throw 'Local SMTP settings are invalid or cannot be decrypted by this Windows account.'}
 if($settings.Host -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$' -or [int]$settings.Port -ne 587){throw 'This SMTP setup requires a valid host and STARTTLS port 587.'}
 $address=[Net.Mail.MailAddress]::new([string]$settings.From)
 if($address.Address -ne $settings.From){throw 'Use a plain sender email address.'}
 $base=[uri]$settings.PublicUrl
 if(!$base.IsAbsoluteUri -or !$base.Host -or $base.UserInfo -or $base.Query -or $base.Fragment -or !($base.Scheme -eq 'https' -or ($base.Scheme -eq 'http' -and $base.Host -eq '127.0.0.1'))){throw 'Mail links require a configured HTTPS URL, or loopback HTTP for the local demo.'}
 $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($settings.Credential.Password)
 try{
  return @{
   ACCOUNT_MAIL_MODE='smtp';ACCOUNT_MAIL_HOST=[string]$settings.Host;ACCOUNT_MAIL_PORT='587';ACCOUNT_MAIL_FROM=[string]$settings.From
   ACCOUNT_MAIL_USERNAME=$settings.Credential.UserName;ACCOUNT_MAIL_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
   ACCOUNT_PUBLIC_URL=$base.AbsoluteUri.TrimEnd('/')
  }
 }finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
}
