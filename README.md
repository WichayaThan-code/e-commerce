# ThaiShopFun

Marketplace สำหรับสินค้าไทย: สมัครร้านแล้วได้ Web Store พร้อมขายทันที โดยแพลตฟอร์มหักค่าธรรมเนียม 5% เมื่อขายสำเร็จ

## เทคโนโลยี

- Frontend: Vite + React + TypeScript (`http://localhost:5173`)
- Backend: Java 17 + Spring Boot (`http://localhost:8081`)
- Database: PostgreSQL (`thaishopfun`)
- Authentication: BCrypt, JWT access token 15 นาที และ rotating refresh token แบบ HttpOnly cookie 30 วัน

## รันครั้งแรก

ต้องมี Node.js, Java 17 และ PostgreSQL ก่อน จาก PowerShell ที่โฟลเดอร์โปรเจกต์:

```powershell
.\backend\setup-postgres.ps1
```

สคริปต์จะตรวจและสร้างฐานข้อมูล `thaishopfun` บน PostgreSQL หลักที่ port `5432` โดยถามรหัสผ่านผู้ดูแลแบบซ่อน

เปิด PowerShell หน้าต่างที่ 1:

```powershell
.\backend\run-local.ps1
```

เปิด PowerShell หน้าต่างที่ 2:

```powershell
npm install
npm run dev
```

เข้า `http://localhost:5173` แล้วสมัครสมาชิก จากนั้นระบบจะ login และพาไป `/dashboard` อัตโนมัติ

## ตรวจสอบก่อนส่งงาน

```powershell
npm run build
cd backend
.\mvnw.cmd test
```

Flyway จะสร้างตาราง `users` และ `refresh_tokens` อัตโนมัติเมื่อ backend เริ่มทำงาน

## API authentication

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
