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
export DB_USERNAME='postgresql'
printf 'PostgreSQL password: '
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

ค่าเริ่มต้น: ฐานข้อมูล `TSF_DB` ที่ `localhost:5432` ผู้ใช้ `postgres` รหัสผ่าน `1234`
`setup-postgres.ps1` สร้างฐานข้อมูล `TSF_DB` ให้หากยังไม่มี และเปลี่ยนค่าได้ด้วย `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

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

## การส่ง OTP และลิงก์รีเซ็ตรหัสผ่าน (อีเมล / SMS)

backend ส่งรหัส OTP และลิงก์รีเซ็ตรหัสผ่านจริงผ่านอีเมล (SMTP) ส่วน SMS ตอนนี้มีแค่ตัว log (ยังไม่ต่อผู้ให้บริการจริง)

- โค้ด: `com.thaishopfun.common.notification` (`EmailSender`, `SmsSender`, `SmtpEmailSender`, `LoggingEmailSender`, `LoggingSmsSender`, `NotificationConfig`) และ `com.thaishopfun.auth.delivery` (event, template ภาษาไทย, listener)
- ส่ง **หลัง transaction commit แล้วเท่านั้น** (`@TransactionalEventListener(AFTER_COMMIT)`) และส่งแบบ async บน thread pool ขนาดเล็ก (`notify-*`, core 2 / max 4 / queue 200) — API ตอบกลับทันที ถ้า transaction rollback จะไม่ส่งอะไรเลย
- ถ้าส่งไม่สำเร็จ จะ log ระดับ ERROR (อีเมล/เบอร์ถูก mask) — response ของ API ไม่เปลี่ยน (ตาราง `otp_verifications` ไม่มีคอลัมน์สถานะการส่ง จึงไม่ได้บันทึกลง DB)
- ปลายทางที่ไม่มีในระบบ (EMAIL_VERIFY / PHONE_VERIFY / forgot-password) ยังตอบ 200 แบบเดียวกันและ **ไม่ส่งอะไร**
- ไม่ log รหัส OTP / โทเคนเต็มที่ระดับ INFO (เนื้อหาอีเมล/SMS log เฉพาะระดับ DEBUG ในโหมด `log`)
- response ของ API ไม่เปลี่ยน; `OTP_EXPOSE_CODE=true` ยังคืน `code` / `resetToken` ใน response สำหรับทดสอบ local (ค่าเริ่มต้น `false`)

| ช่องทาง | ใช้เมื่อ | เนื้อหา |
|---|---|---|
| อีเมล OTP | `otp/send` ที่ปลายทางเป็นอีเมล (`EMAIL_VERIFY`, `SIGNUP`, `PASSWORD_RESET`) | หัวเรื่อง `[ThaiShopFun] รหัส OTP สำหรับ...` + รหัส 6 หลัก + อายุ (นาที) |
| อีเมลรีเซ็ตรหัสผ่าน | `forgot-password` | ลิงก์ `APP_FRONTEND_URL` + `APP_RESET_PASSWORD_PATH?token=...` + โทเคนสำหรับคัดลอก |
| SMS | `otp/send` ที่ปลายทางเป็นเบอร์ (`PHONE_VERIFY`, `SIGNUP`, `PASSWORD_RESET`) | `ThaiShopFun: รหัส OTP ของคุณคือ ...` |

อีเมลเป็น UTF-8 แบบ multipart (plain text + HTML)

> หมายเหตุ: frontend ยังไม่มีหน้า `/reset-password` — ลิงก์ในอีเมลจะใช้ได้เมื่อ frontend เพิ่มหน้านี้ (อ่าน `token` จาก query string แล้วเรียก `POST /api/auth/reset-password`)

### Environment variables

| ตัวแปร | ค่าเริ่มต้น | ความหมาย |
|---|---|---|
| `NOTIFICATION_EMAIL_PROVIDER` | (ว่าง) | `smtp` หรือ `log` — ว่าง = `smtp` ถ้าตั้ง `MAIL_HOST` ไว้, ไม่งั้น `log` |
| `MAIL_HOST` | (ว่าง) | SMTP host; ว่าง = ไม่ส่งอีเมลจริง (แอปสตาร์ทได้ปกติ) |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | (ว่าง) | ว่าง = ไม่ใช้ SMTP AUTH |
| `MAIL_PASSWORD` | (ว่าง) | รหัสผ่าน / app password / API key |
| `MAIL_FROM` | (ว่าง) | ผู้ส่ง; ว่าง = ใช้ `MAIL_USERNAME` ถ้าเป็นอีเมล, ไม่งั้น `no-reply@thaishopfun.local` |
| `MAIL_FROM_NAME` | `ThaiShopFun` | ชื่อผู้ส่ง |
| `MAIL_STARTTLS` | `true` | เปิด/บังคับ STARTTLS (port 587) |
| `MAIL_SSL` | `false` | SMTP over SSL (port 465 — ตั้ง `MAIL_STARTTLS=false` คู่กัน) |
| `APP_FRONTEND_URL` | `FRONTEND_URL` หรือ `http://localhost:5173` | ฐานของลิงก์รีเซ็ตรหัสผ่าน |
| `APP_RESET_PASSWORD_PATH` | `/reset-password` | path ของหน้ารีเซ็ตรหัสผ่านฝั่ง frontend |
| `SMS_PROVIDER` | `log` | ตอนนี้รองรับแค่ `log` (ค่าอื่นทำให้แอปสตาร์ทไม่ขึ้น) |
| `OTP_EXPOSE_CODE` | `false` | `true` = คืน `code` / `resetToken` ใน response (ทดสอบ local เท่านั้น) |

`NOTIFICATION_EMAIL_PROVIDER=smtp` แต่ไม่มี `MAIL_HOST` หรือใส่ค่าที่ไม่รู้จัก แอปจะหยุดตอนสตาร์ทพร้อมข้อความบอกสาเหตุ
SMTP ตั้งค่าผ่าน `app.notification.mail.*` (ไม่ใช่ `spring.mail.*`) จึงไม่มี mail health check / การทดสอบเชื่อมต่อตอนสตาร์ท

ใส่ค่าจริงใน `backend/.env` (ไม่เข้า Git — อย่า commit รหัสผ่าน) ดูตัวอย่างใน `.env.example`
หมายเหตุ: `run-local.ps1` โหลด `.env` และ **ทับ** ตัวแปรที่ตั้งไว้ใน PowerShell — ถ้าจะ override ชั่วคราวด้วย `$env:MAIL_HOST=...` อย่าใส่บรรทัดนั้นใน `.env`

### ตัวอย่างการตั้งค่า SMTP

**Gmail (app password)** — เปิด 2-Step Verification ที่บัญชี Google แล้วสร้าง App password ที่ https://myaccount.google.com/apppasswords (ใช้รหัสผ่านปกติไม่ได้)

```dotenv
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=yourname@gmail.com
MAIL_PASSWORD=xxxxxxxxxxxxxxxx
MAIL_FROM=yourname@gmail.com
MAIL_STARTTLS=true
```

Gmail ส่งได้จำกัดต่อวัน เหมาะกับทดสอบ/ทีมเล็ก

**SendGrid (SMTP relay)** — username คือคำว่า `apikey` ตรง ๆ, password คือ API key (สิทธิ์ Mail Send) และ `MAIL_FROM` ต้องเป็น Sender ที่ verify แล้ว

```dotenv
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=SG.xxxxxxxx
MAIL_FROM=no-reply@yourdomain.co.th
MAIL_STARTTLS=true
```

**SMTP ของบริษัท** — ขอ host/port/บัญชีจากทีม IT

```dotenv
MAIL_HOST=smtp.yourcompany.co.th
MAIL_PORT=587
MAIL_USERNAME=no-reply@yourcompany.co.th
MAIL_PASSWORD=********
MAIL_FROM=no-reply@yourcompany.co.th
# ถ้าเซิร์ฟเวอร์ใช้ port 465 (SSL ตรง):
# MAIL_PORT=465
# MAIL_SSL=true
# MAIL_STARTTLS=false
```

### SMS

ค่าเริ่มต้น `SMS_PROVIDER=log` — ไม่ส่ง SMS จริง แค่ log ว่า "SMS would be sent" พร้อมเบอร์ที่ mask แล้ว (เช่น `081****78`)
การเพิ่มผู้ให้บริการจริง (เช่น ThaiBulkSMS, Twilio): สร้างคลาสที่ implement `SmsSender` (เมธอด `send(SmsMessage)` — ส่งไม่สำเร็จให้ throw `NotificationException`) แล้วเพิ่ม `case` ใน `NotificationConfig#smsSender` พร้อม env ของ credentials ของผู้ให้บริการนั้น (ยังไม่มี credentials ใด ๆ ใน repo)

### ทดสอบบนเครื่อง (ไม่ต้องมี SMTP จริง)

1. ไม่ตั้ง `MAIL_HOST` → provider เป็น `log` อีเมลจะแสดงแค่ใน log; ตั้ง `OTP_EXPOSE_CODE=true` เพื่อดูรหัสใน response
2. ทดสอบส่งอีเมลจริงกับ SMTP ปลอม: รัน fake SMTP ที่ port 1025 เช่น [Mailpit](https://github.com/axllent/mailpit) (`mailpit.exe` แล้วเปิด http://localhost:8025) หรือ Python `pip install aiosmtpd` แล้ว `python -m aiosmtpd -n -l localhost:1025`
3. สตาร์ท backend โดย override env ชั่วคราว (ไม่ต้องแก้ `.env`):

```powershell
$env:MAIL_HOST='localhost'; $env:MAIL_PORT='1025'; $env:MAIL_STARTTLS='false'
.\run-local.ps1
```

4. สมัครสมาชิก แล้วเรียก `POST /api/auth/otp/send` (`EMAIL_VERIFY`) และ `POST /api/auth/forgot-password` — อีเมลภาษาไทยจะไปโผล่ที่ fake SMTP นำรหัสจากอีเมลไปเรียก `POST /api/auth/otp/verify`
5. Unit/integration tests ใช้ `EmailSender` / `SmsSender` แบบ mock (`NotificationDeliveryIntegrationTests`, `SmtpEmailSenderTests`) ไม่ต้องมีเซิร์ฟเวอร์อีเมล

## Database schema (Flyway) — สำหรับทีมสร้าง DB ว่าง

Schema ทั้งหมดอยู่ใน **ไฟล์เดียว**: `src/main/resources/db/migration/V1__init_schema.sql`
(สร้างครบทุกตาราง/คอลัมน์ ~70 ตาราง + constraint, index, FK, trigger, function, comment — รันครั้งเดียวบน DB ว่างได้ในรอบเดียว)

| ไฟล์ | หน้าที่ |
|------|---------|
| `V1__init_schema.sql` | สร้าง schema ทั้งหมด (รวม V1+V2+V3 เดิมเป็นไฟล์เดียว) — **ไม่มี** ข้อมูลอ้างอิง (ดู Seed ด้านล่าง) |
| `V2__shop_onboarding_owners_categories_reinstatement.sql` | ส่วนต่อจาก branch ร้านค้า (7f730e8): ตาราง `owners`, `shop_reinstatement_requests`, คอลัมน์ร้าน/ใบสมัคร (`shop_no`, `owner_id`, social links, `opening_hours`, `citizen_id_hash`), `hub_categories.category_no`/`name_zh`, สถานะร้าน `PENDING_REVIEW/ACTIVE/REJECTED/SUSPENDED/CLOSED` — เขียนแบบ defensive (`IF NOT EXISTS`) |

ไฟล์เดิม (`V1__create_auth_tables.sql`, `V2__create_merchant_applications.sql`, `V3__ecommerce_schema_v3.sql`) ย้ายไปเก็บที่ `backend/db-archive/` (อยู่นอก `resources` — Flyway จะไม่โหลด) เก็บไว้อ้างอิงเท่านั้น ห้ามย้ายกลับ
migration ชุด V1..V13 ของ branch 7f730e8 ย้ายไปไว้ที่ `backend/db-archive/from-7f730e8/` และรวมส่วนที่ยังไม่มีใน V1 เป็น `V2__shop_onboarding_owners_categories_reinstatement.sql` แล้ว

### สร้าง schema บนฐานข้อมูลว่าง (ทีมใหม่ / เครื่องใหม่)

**วิธีที่ 1 — ให้ backend (Flyway) สร้างให้ (แนะนำ)**

1. สร้าง DB ว่าง เช่น `TSF_DB` (Windows: `.\setup-postgres.ps1` หรือ `CREATE DATABASE "TSF_DB";`)
2. ตั้ง `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` แล้วสตาร์ท backend (`.\run-local.ps1` หรือ `./run-local.sh`)
3. Flyway รัน `V1__init_schema.sql` อัตโนมัติ (สร้าง `flyway_schema_history` ให้เอง) แล้ว Hibernate `ddl-auto: validate` ตรวจ schema
4. รัน seed ข้อมูลอ้างอิง (หัวข้อ Seed ด้านล่าง)

**วิธีที่ 2 — รันมือด้วย psql** (เช่น เตรียม DB ให้ทีมอื่น/ตรวจ schema)

```sh
psql -U postgres -d <db> -v ON_ERROR_STOP=1 -1 -f src/main/resources/db/migration/V1__init_schema.sql
psql -U postgres -d <db> -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/seed_reference_data.sql
```

Windows:

```powershell
$env:PGPASSWORD = '1234'
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
& $psql -U postgres -d <db> -v ON_ERROR_STOP=1 -1 -f .\src\main\resources\db\migration\V1__init_schema.sql
& $psql -U postgres -d <db> -v ON_ERROR_STOP=1 -f .\src\main\resources\db\seed\seed_reference_data.sql
```

DB ที่สร้างด้วยมือแบบนี้ยังไม่มี `flyway_schema_history` — ตอนสตาร์ท backend ครั้งแรก Flyway จะ baseline ที่เวอร์ชัน 1 ให้เอง (ข้าม V1 ไม่รันซ้ำ)

### พฤติกรรม `baseline-on-migrate` / `baseline-version` ใน `application.yml`

ค่าปัจจุบัน: `baseline-on-migrate: true`, `baseline-version: 1`

- **DB ว่าง** (ไม่มี object ใน schema): Flyway ไม่ baseline — รัน `V1__init_schema.sql` ตามปกติ
- **DB ที่มีตารางครบแล้วแต่ยังไม่มีตาราง `flyway_schema_history`** (เช่น รัน V1 ด้วย psql): Flyway baseline ที่เวอร์ชัน 1 แล้วข้าม V1 — รันเฉพาะ V2 ขึ้นไป (ถ้ามี)
- **TSF_DB (shared)**: history ถูกปรับแล้ว (2026-10-06) ให้เหลือแถว `BASELINE` เวอร์ชัน 1 แถวเดียว — สตาร์ทแอปได้เลย (validate, ไม่มี DDL); history เดิมสำรองไว้ในตาราง `flyway_schema_history_backup_20261006`
- **ระวัง**: ถ้ามีตาราง `flyway_schema_history` อยู่แต่ **ว่างเปล่า** Flyway จะ **ไม่** baseline และจะพยายามรัน V1 ทับ schema เดิม (ล้มเหลวแล้ว rollback แอปไม่สตาร์ท) — อย่า `DELETE` history เฉย ๆ
- **DB เก่าที่ history ยังเป็น V1/V2/V3 ชุดเดิม** (เช่น `tsf_dev`, `tsf_app` ที่มีแค่ V1+V2): validate จะ fail (checksum V1 ไม่ตรง / ไม่พบ V2,V3) — ทางเลือก: (ก) DB ทดลอง → drop แล้วสร้าง DB ว่างใหม่ให้ Flyway รัน V1; (ข) DB ที่มี schema v3 ครบแล้ว → สำรอง history แล้วปรับให้เหลือ baseline เวอร์ชัน 1 แบบเดียวกับ TSF_DB:

```sql
BEGIN;
CREATE TABLE flyway_schema_history_backup_YYYYMMDD AS SELECT * FROM flyway_schema_history;
DELETE FROM flyway_schema_history;
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (1, '1', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, current_user, now(), 0, true);
COMMIT;
```

### กติกาการแก้ schema ต่อจากนี้

- **ห้ามแก้ `V1__init_schema.sql` และ `V2__...` ที่ deploy แล้ว** (แม้แต่ช่องว่าง/คอมเมนต์ — checksum เปลี่ยนแล้ว DB ที่รันไปแล้ว validate fail)
- การเปลี่ยนแปลงใหม่ทุกครั้ง → สร้างไฟล์ใหม่ `V3__<คำอธิบาย>.sql`, `V4__...` ตามลำดับ ใน `src/main/resources/db/migration/`
- ถ้าเพิ่ม/แก้คอลัมน์ที่ entity ใช้ ต้องแก้ JPA entity ให้ตรงด้วย (`ddl-auto: validate`)
- ถ้าเคยรัน backend มาก่อนแล้วเห็น migration เก่าโผล่ ให้ลบ `target/classes/db/migration/` เก่า (หรือ `mvnw clean`) — Maven ไม่ลบไฟล์ที่ถูกลบจาก `src` ออกจาก `target` ให้เอง

### Seed ข้อมูลอ้างอิง (รันมือ — ไม่ใช่ Flyway version)

หลัง schema พร้อมแล้ว รัน seed **ครั้งเดียว** (idempotent) ด้วย psql — **ไม่ได้**ใส่เป็น migration ที่ auto-run เพื่อเลี่ยงการเขียนข้อมูลอ้างอิงแบบทำลาย/ทับโดยไม่ตั้งใจ:

- ใน repo: `src/main/resources/db/seed/seed_reference_data.sql` (สำเนาเดียวกับ `docs/sql/seed_reference_data.sql`)

Windows (ตัวอย่าง):

```powershell
$env:PGPASSWORD = '1234'
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d TSF_DB -v ON_ERROR_STOP=1 -f .\src\main\resources\db\seed\seed_reference_data.sql
```

macOS / Linux:

```sh
psql -U postgres -d TSF_DB -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/seed_reference_data.sql
```

ถ้าต้องการตั้ง `PLATFORM_ADMIN` ให้ user ที่มีอีเมลอยู่แล้ว:

```sh
psql -U postgres -d TSF_DB -v ON_ERROR_STOP=1 -c "SELECT set_config('app.admin_email', 'admin@example.com', false);" -f src/main/resources/db/seed/seed_reference_data.sql
```


## API authentication

### Already existed
- `POST /api/auth/register` — create customer + access token + httpOnly refresh cookie `tsf_refresh`
- `POST /api/auth/login` — email or phone + password → tokens
- `POST /api/auth/refresh` — rotate refresh cookie → new access token
- `POST /api/auth/logout` — revoke current refresh token + clear cookie
- `GET /api/auth/me` — current user (Bearer access token)

### Added (auth deliverable)
- `POST /api/auth/forgot-password` body `{ "email" }` → always generic Thai message; e-mails a reset link (after commit) when the account exists; when `OTP_EXPOSE_CODE=true` also returns `resetToken` (local/testing only)
- `POST /api/auth/reset-password` body `{ "token", "newPassword" }` → sets password, bumps `users.permission_version` (old access tokens stop working) and revokes all refresh tokens
- `POST /api/auth/change-password` (auth required) body `{ "currentPassword", "newPassword" }` → bumps `permission_version` and revokes all refresh tokens
- `POST /api/auth/otp/send` body `{ "destination", "purpose" }` — purposes: `EMAIL_VERIFY`, `PHONE_VERIFY`, `PASSWORD_RESET`, `SIGNUP` — the code is delivered by e-mail (SMTP) or SMS (`SmsSender`, log-only by default) after commit; returns `code` only if `OTP_EXPOSE_CODE=true`. Verify purposes for an unknown destination answer with the same generic 200 shape and send nothing.
- `POST /api/auth/otp/verify` body `{ "otpId", "code" }` — marks verified; for `PASSWORD_RESET` returns one-time `resetToken`

### Token rules
- Access token carries `pv`; every request checks it against `users.permission_version` and that the user is still `ACTIVE` and not deleted.
- Refresh rotation is single-use (conditional update). Re-using an already rotated/revoked refresh token revokes **all** of that user's refresh tokens. Suspended/deleted users cannot refresh.

### Lockout / rate limit
- Wrong password: in-memory lock after `app.security.max-login-attempts` (default 5) for `login-lockout-minutes` (default 15), keyed by the resolved user id (so email and phone share one counter; unknown identifiers are counted separately). Memory is bounded (expired entries purged, hard cap 50k). No `failed_attempts` column on `users`.
- OTP: uses `otp_verifications.attempts` / `max_attempts`; send cooldown + hourly cap by `destination_hash`.

### Response shapes (summary)
- Auth session: `{ "accessToken", "expiresIn", "user": { id, firstName, lastName, email, phone, role, emailVerified, phoneVerified } }` + Set-Cookie `tsf_refresh`
- Errors: `{ "code", "message" }` — malformed JSON / wrong parameter types → 400 `VALIDATION_ERROR`; unique-constraint races → 409 `CONFLICT`

## Merchant / shop API (from branch 7f730e8)

- `GET /api/merchant-applications/draft`, `PUT .../draft/shop`, `PUT .../draft/owner`, `POST .../draft/owner/{ownerId}`, `POST .../draft/documents/{type}`, `POST .../draft/submit` — one open DRAFT per user; a new application can be started after the previous one is submitted, rejected or withdrawn (multi-shop)
- `GET /api/merchant/owners`, `PUT /api/merchant/owners/{id}`, `GET|POST /api/merchant/shops`, `GET /api/merchant/store-access`, `GET|PUT /api/merchant/settings`, `PUT /api/merchant/settings/hours`, `POST /api/merchant/settings/logo|cover`, `GET|POST /api/merchant/reinstatement?shopId=`
- `GET /api/admin/store-applications`, `POST .../{shopId}/approve|reject|suspend|reactivate`, `POST .../bulk/{action}`, `GET .../export`, `GET .../{shopId}/documents/{documentId}` (role ADMIN)
- Public: `GET /api/categories`, `GET /api/shops`, `GET /api/shops/{slug}` (+ `/logo`, `/cover`)
- `shopId` in these APIs is the running number `shops.shop_no`; the table primary key stays UUID (V1 convention). Shop status `APPROVED` in the API is stored as `ACTIVE` in `shops.status`.
