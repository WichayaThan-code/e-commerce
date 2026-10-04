$ErrorActionPreference = 'Stop'

# 1. โหลดค่า Environment Variables จากไฟล์ .env.local (ถ้ามี)
$envFilePath = Join-Path $PSScriptRoot '.env.local'

if (Test-Path $envFilePath) {
    Get-Content $envFilePath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line -match '^([^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value, [System.EnvironmentVariableTarget]::Process)
        }
    }
} else {
    # ค่า Default เผื่อกรณีไม่มีไฟล์ .env.local
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/thaishopfun'
    $env:DB_USERNAME = 'thaishopfun_user'
    $env:DB_PASSWORD = 'Pg@ThaiShopFun2026!'
}

# 2. รัน Spring Boot
# ครั้งแรกจะสร้างฐานข้อมูล (ถ้ายังไม่มี) แล้วให้ Flyway สร้างตาราง
# ครั้งถัดไป ถ้า schema เป็นเวอร์ชันล่าสุดแล้ว จะไม่เปลี่ยนข้อมูลเดิม
try {
    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
} finally {
    # เคลียร์ค่ารหัสผ่านออกจาก memory ของ Process หลังหยุดการทำงาน
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}