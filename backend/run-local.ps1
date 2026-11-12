$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) { return }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
    Write-Host "Loaded environment from .env" -ForegroundColor DarkGray
}

if (-not $env:DB_URL) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/TSF_DB' }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'postgres' }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = '1234' }
# Local profile forces MyMemory (no API key). Clear sticky fake from older sessions.
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'local' }
if ($env:TRANSLATION_PROVIDER -eq 'fake') { Remove-Item Env:TRANSLATION_PROVIDER -ErrorAction SilentlyContinue }
# Optional override: $env:APP_TRANSLATION_PROVIDER = 'google' (needs GOOGLE_TRANSLATE_API_KEY)

Write-Host "DB_URL=$env:DB_URL" -ForegroundColor DarkGray
Write-Host "SPRING_PROFILES_ACTIVE=$env:SPRING_PROFILES_ACTIVE (translation=mymemory unless APP_TRANSLATION_PROVIDER set)" -ForegroundColor DarkGray
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
