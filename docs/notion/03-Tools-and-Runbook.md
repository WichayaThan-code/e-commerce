# Tools & Runbook

## เครื่องมือของโปรเจกต์

| เครื่องมือ | ใช้ทำอะไร | จุดใช้งาน |
| --- | --- | --- |
| Git / GitHub | version control และ PR | repository หลักใน README |
| Node.js / npm / Vite | frontend dev และ build | frontend/package.json |
| Java 17 / Maven Wrapper | backend dev และ tests | backend/mvnw, pom.xml |
| PostgreSQL 17 / Postgres.app | ฐานข้อมูล local ตาม backend README | backend/database-local.sh |
| psql | รัน SQL และตรวจ schema | Terminal |
| Flyway | version schema | backend/src/main/resources/db/migration/ |
| pg_dump / pg_restore | backup และ restore | Terminal; ตัวอย่างด้านล่าง |
| Notion | ติดตามงาน เอกสาร และการตัดสินใจ | หน้า Project Hub |

## รัน local

จาก root ของ repository เปิด terminal frontend:

```sh
cd frontend
npm ci
npm run dev
```

เปิดอีก terminal จาก root สำหรับ backend บน macOS เครื่องที่ตั้งค่าไว้แล้ว:

```sh
cd backend
./database-local.sh status
./database-local.sh start
./run-local.sh
```

ใช้ start เมื่อฐานข้อมูลยังไม่ทำงาน; เครื่องอื่นตั้งค่าตาม backend/README.md ก่อน Windows ใช้ setup-postgres.ps1 และ run-local.ps1

## ตรวจงาน

```sh
# จาก frontend/
npm run build
# จาก backend/
bash ./mvnw test
```

บันทึก commit, วันเวลา, คำสั่งและผลใน Evidence ของงาน รอบจัดทำเอกสารนี้ยังไม่ได้รันคำสั่งทดสอบเหล่านี้

## Environment checklist

| Variable | ความหมาย |
| --- | --- |
| DB_URL | JDBC URL ของ PostgreSQL |
| DB_USERNAME / DB_PASSWORD | บัญชีเชื่อมฐานข้อมูล; เก็บใน secret หรือ .env.local |
| SERVER_PORT | backend port; default 8081 |
| FRONTEND_URL | origin ที่ backend อนุญาต |
| JWT_SECRET | signing secret; เปลี่ยนค่าตัวอย่างก่อน production |
| DATA_ENCRYPTION_SECRET | key material สำหรับข้อมูลเจ้าของร้าน; ต้องมีแผนสำรองและจัดการ key |
| COOKIE_SECURE | เปิด secure cookie เมื่อใช้ HTTPS |
| STORAGE_ROOT | ที่เก็บไฟล์เอกสาร |

ไม่คัดลอกเนื้อหา .env.local ลง Notion; อ่านชื่อตัวแปร frontend จาก frontend/.env.example

## เชื่อม PostgreSQL และรัน query

แทน YOUR_DB_USER ด้วยบัญชีของเครื่อง; ใช้ -W เพื่อกรอกรหัสผ่านแบบ prompt ไม่วางรหัสผ่านในคำสั่ง

```sh
psql -h 127.0.0.1 -p 5432 -U YOUR_DB_USER -d thaishopfun -W
```

คำสั่งต่อไปนี้ใช้ภายใน psql เท่านั้น:

```text
\conninfo
\dt public.*
\d+ public.merchant_applications
\q
```

รัน query ทั้งชุดจาก root repository:

```sh
psql -h 127.0.0.1 -p 5432 -U YOUR_DB_USER -d thaishopfun -W -v ON_ERROR_STOP=1 -f docs/notion/sql/readonly.sql
```

สคริปต์เปิด transaction แบบ READ ONLY และปิดด้วย ROLLBACK มี statement timeout 15 วินาที หาก query ล้มเหลวเมื่อคัดลอกทีละส่วน ให้ ROLLBACK ก่อนเริ่มใหม่

## Backup / restore สำหรับซ้อมกู้คืน

ใช้ PostgreSQL client 17 ให้ตรงกับ server 17 ของโปรเจกต์ ตัวอย่างนี้สร้าง backup และฐานใหม่ ไม่เขียนทับฐานหลัก:

```sh
pg_dump -h 127.0.0.1 -U YOUR_DB_USER -d thaishopfun -W -Fc -f thaishopfun-backup.dump
createdb -h 127.0.0.1 -U YOUR_DB_USER -W thaishopfun_restore_check
pg_restore -h 127.0.0.1 -U YOUR_DB_USER -W -d thaishopfun_restore_check --no-owner --no-privileges --exit-on-error thaishopfun-backup.dump
```

บัญชีต้องมีสิทธิ์สร้างฐานข้อมูล ถ้าชื่อฐานทดสอบมีอยู่แล้วให้เลือกชื่อใหม่ก่อนรัน ตรวจ migration history, จำนวนแถว และเปิดไฟล์เอกสารหลัง restore บนสภาพแวดล้อมทดสอบ ต้องสำรอง STORAGE_ROOT และเก็บ encryption key แยกอย่างปลอดภัยด้วย เพราะ pg_dump ไม่รวมไฟล์และ environment secrets และไม่สำรอง cluster roles

อ้างอิง: [pg_dump](https://www.postgresql.org/docs/17/app-pgdump.html), [SQL dump / restore](https://www.postgresql.org/docs/17/backup-dump.html)

## แก้ปัญหาเบื้องต้น

| อาการ | ตรวจตามลำดับ |
| --- | --- |
| ต่อฐานไม่ได้ | database-local.sh status → host/port/database → บัญชีและรหัสผ่าน |
| Flyway หรือ schema validation ล้มเหลว | error log → flyway_schema_history → migration ใหม่; ไม่แก้ migration ที่ใช้ไปแล้ว |
| Frontend เรียก API ไม่ได้ | backend :8081 → frontend/.env.example → FRONTEND_URL/origin |
| อัปโหลดไม่ได้ | ขนาดไฟล์ → ชนิดไฟล์ → สิทธิ์เขียน STORAGE_ROOT |
| ถอดรหัสข้อมูลเดิมไม่ได้ | ตรวจว่า DATA_ENCRYPTION_SECRET ตรงกับตอนบันทึก; อย่าสุ่มเปลี่ยน key |
