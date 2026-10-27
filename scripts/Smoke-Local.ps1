$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$config = Get-Content -LiteralPath (Join-Path $projectRoot '.runtime\local.json') -Raw | ConvertFrom-Json
$baseUrl = 'http://127.0.0.1:5173'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$products = Invoke-RestMethod "$baseUrl/api/products?search=SILK" -WebSession $session
if ($products.items.Count -ne 1 -or $products.items[0].sku -ne 'MG-001') { throw 'Catalog smoke failed.' }
$csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -WebSession $session
$headers = @{}; $headers[$csrf.headerName] = $csrf.token
$login = Invoke-WebRequest "$baseUrl/api/auth/login" -WebSession $session -Method Post -Headers $headers -ContentType 'application/x-www-form-urlencoded' -Body @{username=$config.demoAdminEmail;password=$config.demoAdminPassword}
if ($login.StatusCode -ne 200) { throw 'Demo administrator login failed.' }
$admin = Invoke-RestMethod "$baseUrl/api/admin/identity" -WebSession $session
if ($admin.role -ne 'ADMIN') { throw 'Administrator role was not verified by the server.' }
$csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -WebSession $session
$headers = @{}; $headers[$csrf.headerName] = $csrf.token
$logout = Invoke-WebRequest "$baseUrl/api/auth/logout" -WebSession $session -Method Post -Headers $headers
if ($logout.StatusCode -ne 204) { throw 'Logout smoke failed.' }
$anonymous = Invoke-WebRequest "$baseUrl/api/auth/me" -WebSession $session -SkipHttpErrorCheck
if ($anonymous.StatusCode -ne 401) { throw 'Logout did not invalidate the session.' }
Write-Host 'PASS: catalog through proxy, demo administrator login and role, CSRF logout, anonymous session rejection.'
