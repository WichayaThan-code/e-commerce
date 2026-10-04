# Backend — API และฐานข้อมูล

ต้องมี Java 17 และ PostgreSQL ที่เปิดรับการเชื่อมต่อ ถ้ายังไม่มีฐานข้อมูล `thaishopfun` การสตาร์ตจะสร้างให้เองเมื่อผู้ใช้มีสิทธิ์ ถ้าสร้างไม่ได้ให้รัน `setup-postgres.ps1` หมวดหมู่สินค้าและบัญชีแอดมินเริ่มต้นถูกใส่เฉพาะเมื่อยังไม่มีแถวนั้น
เปิด Terminal แยกจาก frontend และเข้า `ThaiShopFun/backend` ก่อนรันคำสั่งด้านล่าง

## เครื่อง macOS นี้ (ตั้งค่าแล้ว)

ติดตั้ง Java 17 และ PostgreSQL 17 แล้ว ฐานข้อมูล `thaishopfun` ใช้บัญชีเฉพาะโปรเจกต์
ข้อมูลการเชื่อมต่อเก็บใน `.env.local` (ไม่เข้า Git) และข้อมูล PostgreSQL อยู่ที่ `~/Library/Application Support/Postgres/var-17/`
จากโฟลเดอร์ `backend`:

```sh
./database-local.sh status
# หลังรีสตาร์ตเครื่อง หากฐานข้อมูลยังไม่ทำงาน:
./database-local.sh start
./run-local.sh
```

หยุด backend ด้วย Ctrl+C และหยุดฐานข้อมูลด้วย `./database-local.sh stop`
สามารถ Start/Stop และ Connect ผ่าน Postgres.app ได้ โดยใช้ฐานข้อมูลเดียวกัน
ใช้ `run-local.sh` เพื่อโหลด `.env.local` อัตโนมัติ

## macOS / Linux (ตั้งค่าเครื่องอื่น)

สร้างฐานข้อมูลและผู้ใช้ PostgreSQL ให้พร้อม แล้วกำหนดค่าการเชื่อมต่อใน Terminal นี้:

```sh
export DB_URL='jdbc:postgresql://127.0.0.1:5432/thaishopfun'
export DB_USERNAME='thaishopfun_user'
printf 'PostgreSQL password: Pg@ThaiShopFun2026!'
read -r -s DB_PASSWORD
printf '\n'
export DB_PASSWORD
bash ./mvnw spring-boot:run
```

เปลี่ยน DB_USERNAME ให้ตรงกับผู้ใช้ฐานข้อมูลของเครื่อง

## Windows PowerShell

```powershell
.\setup-postgres.ps1
.\run-local.ps1
```

สคริปต์ถามรหัสผ่านแบบซ่อนและใช้ผู้ใช้ฐานข้อมูล `postgresql`

## การตั้งค่าและการทดสอบ

- API: http://localhost:8081
- Frontend ที่อนุญาต: http://localhost:5173 (เปลี่ยนด้วย `FRONTEND_URL`)
- การตั้งค่า: `src/main/resources/application.yml`
- Flyway migrations: `src/main/resources/db/migration/`
- เจ้าของร้านอยู่ตาราง `owners` (หนึ่งตัวตนต่อหนึ่งแถว ตาม `citizen_id_hash`) และร้านชี้ด้วย `shops.owner_id` เจ้าของหนึ่งคนมีได้หลายร้าน ร้านหนึ่งมีเจ้าของได้คนเดียว
- ไฟล์อัปโหลด: `uploads/` เมื่อรันจากโฟลเดอร์นี้
- ระบบ backend อ่าน environment variables; ไม่ได้โหลด `.env` ของ frontend

macOS / Linux:

```sh
bash ./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

## API authentication

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
