$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsPath = Join-Path $projectRoot '.tools'
$destinationRoot = Join-Path $toolsPath 'postgresql'
$zipPath = Join-Path $toolsPath 'postgresql.zip'
New-Item -ItemType Directory -Path $toolsPath -Force | Out-Null
$expectedHash = '4b8db0930c38f6ef845db919551dedda3b6b845aeb0927b3d79a6e8e9e4537cf'
if (-not (Test-Path -LiteralPath $zipPath)) {
    Invoke-WebRequest -Uri 'https://get.enterprisedb.com/postgresql/postgresql-17.11-3-windows-x64-binaries.zip' -OutFile $zipPath
}
if ((Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'PostgreSQL archive checksum does not match the supplier checksum. Do not execute it.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^pgsql/(bin|lib|share)/' -and -not $entry.FullName.EndsWith('/')) {
            $destination = [System.IO.Path]::GetFullPath((Join-Path $destinationRoot $entry.FullName))
            if (-not $destination.StartsWith($destinationRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive path.' }
            [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
        }
    }
} finally { $archive.Dispose() }
Write-Host 'Portable PostgreSQL 17.11 is ready. Run scripts/Start-Local.ps1 -Build.'
