$ErrorActionPreference = 'Stop'

$securePassword = Read-Host 'PostgreSQL password for administrator postgres' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('', $securePassword)
$env:PGPASSWORD = $credential.Password
try {
    $exists = & psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='thaishopfun'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect as PostgreSQL administrator.' }
    if (($exists | Out-String).Trim() -ne '1') {
        & psql -h 127.0.0.1 -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE thaishopfun OWNER postgresql'
        if ($LASTEXITCODE -ne 0) { throw 'Could not create database thaishopfun.' }
    }
    Write-Host 'Database thaishopfun is ready and owned by postgresql.' -ForegroundColor Green
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $securePassword.Dispose()
}

