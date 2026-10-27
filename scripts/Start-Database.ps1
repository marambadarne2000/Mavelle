$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimePath = Join-Path $projectRoot '.runtime'
$binPath = Join-Path $projectRoot '.tools\postgresql\pgsql\bin'
$dataPath = Join-Path $runtimePath 'postgres-data'
$configPath = Join-Path $runtimePath 'local.json'
if (-not (Test-Path -LiteralPath (Join-Path $binPath 'pg_ctl.exe'))) {
    throw 'Portable PostgreSQL is missing. Follow README.md > Windows without Docker.'
}
New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
if (-not (Test-Path -LiteralPath $configPath)) {
    $randomBytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $appPassword = [Convert]::ToHexString($randomBytes).ToLowerInvariant()
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $adminPassword = [Convert]::ToHexString($randomBytes).ToLowerInvariant()
    @{ port = 55432; database = 'maramigo'; user = 'maramigo'; password = $appPassword; adminPassword = $adminPassword } |
        ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
}
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath (Join-Path $dataPath 'PG_VERSION'))) {
    $passwordPath = Join-Path $runtimePath 'init-password'
    try {
        Set-Content -LiteralPath $passwordPath -Value $config.adminPassword -Encoding ascii
        & (Join-Path $binPath 'initdb.exe') -D $dataPath -U maramigo_local_admin --encoding=UTF8 --locale=C --auth=scram-sha-256 "--pwfile=$passwordPath"
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
    } finally {
        if (Test-Path -LiteralPath $passwordPath) { [System.IO.File]::Delete($passwordPath) }
    }
}
& (Join-Path $binPath 'pg_ctl.exe') -D $dataPath status *> $null
if ($LASTEXITCODE -ne 0) {
    # Wait for pg_ctl only, not its long-lived postgres child. This also works when
    # the caller redirects the entire script's output into a log file.
    $controlArguments = "-D `"$dataPath`" -l `"$(Join-Path $runtimePath 'postgres.log')`" -o `"-h 127.0.0.1 -p $($config.port)`" -w start"
    $control = Start-Process -FilePath (Join-Path $binPath 'pg_ctl.exe') -ArgumentList $controlArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimePath 'pgctl-start.log') -RedirectStandardError (Join-Path $runtimePath 'pgctl-start-error.log')
    $control.WaitForExit()
    if ($control.ExitCode -ne 0) { throw 'PostgreSQL startup failed. Check .runtime/pgctl-start*.log and postgres.log.' }
}
$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $config.adminPassword
    $common = @('-h', '127.0.0.1', '-p', [string]$config.port, '-U', 'maramigo_local_admin', '-d', 'postgres', '-v', 'ON_ERROR_STOP=1')
    $role = & (Join-Path $binPath 'psql.exe') @common -tAc "SELECT 1 FROM pg_roles WHERE rolname='maramigo'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to the local PostgreSQL instance.' }
    if ($role -ne '1') {
        # Passwords are generated hexadecimal values, never user-supplied SQL fragments.
        $sql = "CREATE ROLE maramigo LOGIN PASSWORD '$($config.password)'"
        $sql | & (Join-Path $binPath 'psql.exe') @common
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the application database role.' }
    }
    $database = & (Join-Path $binPath 'psql.exe') @common -tAc "SELECT 1 FROM pg_database WHERE datname='maramigo'"
    if ($database -ne '1') {
        & (Join-Path $binPath 'createdb.exe') -h 127.0.0.1 -p $config.port -U maramigo_local_admin -O maramigo maramigo
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the application database.' }
    }
} finally { $env:PGPASSWORD = $previousPassword }
Write-Host "PostgreSQL ready on 127.0.0.1:$($config.port). Credentials are in ignored .runtime/local.json."
