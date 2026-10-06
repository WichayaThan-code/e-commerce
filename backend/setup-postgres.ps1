$ErrorActionPreference = 'Stop'

$env:PGPASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '1234' }
try {
    $exists = & psql -h localhost -p 5432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='TSF_DB'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect as PostgreSQL user postgres.' }
    if (($exists | Out-String).Trim() -ne '1') {
        & psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE "TSF_DB" OWNER postgres'
        if ($LASTEXITCODE -ne 0) { throw 'Could not create database TSF_DB.' }
    }
    Write-Host 'Database TSF_DB is ready and owned by postgres.' -ForegroundColor Green
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
