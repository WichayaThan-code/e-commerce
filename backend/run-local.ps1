$ErrorActionPreference = 'Stop'

$securePassword = Read-Host 'PostgreSQL password for user postgresql' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('', $securePassword)
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/thaishopfun'
$env:DB_USERNAME = 'postgresql'
$env:DB_PASSWORD = $credential.Password
try {
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    $securePassword.Dispose()
}
