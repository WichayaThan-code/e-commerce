# Project Documentation

## ขอบเขตและแหล่งข้อมูล

ตรวจจาก README.md, backend/README.md, frontend/package.json, controllers, enums, application.yml และ Flyway V1/V2 ณ 2026-09-17 เป็น snapshot ของ repository ไม่ใช่ผลตรวจ production

ตำแหน่งธุรกิจ: ขายระบบ OMS ให้ร้านค้า ไม่ได้ทำแพลตฟอร์มแข่งกับ Shopee/TikTok — ดูรายละเอียดที่ 06-OMS-Product-Vision โค้ดปัจจุบันบางส่วน (เช่นหน้า marketplace/สมัครร้านค้า) ยังเขียนตามโครงเดิมก่อนปรับตำแหน่งธุรกิจ

Repository: https://github.com/WichayaThan-code/e-commerce

## สถาปัตยกรรม

Browser → React/TypeScript (Vite :5173) → Spring Boot REST API (:8081) → PostgreSQL (:5432, thaishopfun)

Flyway ดูแล schema; JPA ตั้ง `ddl-auto: validate`; เอกสารร้านค้าเก็บเป็นไฟล์ที่ STORAGE_ROOT (ค่าเริ่มต้น ./uploads) ส่วน metadata เก็บในฐานข้อมูล

| ตำแหน่ง | หน้าที่ |
| --- | --- |
| frontend/src/pages/ | หน้าระบบสมาชิก แอดมิน และหน้าร้าน (storefront) ของร้านค้าแต่ละราย |
| frontend/src/services/api.ts | API client และ type ที่ใช้ร่วมกับ UI |
| backend/src/main/java/com/thaishopfun/auth/ | บัญชีผู้ใช้ authentication และ security |
| backend/src/main/java/com/thaishopfun/merchant/ | draft ใบสมัคร เอกสาร และการส่งใบสมัคร |
| backend/src/main/resources/db/migration/ | V1 auth และ V2 merchant |
| backend/src/test/ | integration tests และ test configuration |
| docs/design/ และ docs/flows/ | แบบหน้าจอและ flow |

## API ที่พบในโค้ด

| Method | Path | วัตถุประสงค์ |
| --- | --- | --- |
| POST | /api/auth/register | สมัครสมาชิก |
| POST | /api/auth/login | เข้าสู่ระบบ |
| POST | /api/auth/refresh | ต่ออายุ authentication |
| POST | /api/auth/logout | ออกจากระบบ |
| GET | /api/auth/me | ข้อมูลผู้ใช้ปัจจุบัน |
| GET | /api/merchant-applications/draft | อ่าน draft ของผู้ใช้ |
| PUT | /api/merchant-applications/draft/shop | บันทึกข้อมูลร้าน |
| PUT | /api/merchant-applications/draft/owner | บันทึกข้อมูลเจ้าของและธนาคาร |
| POST | /api/merchant-applications/draft/documents/{type} | อัปโหลด multipart โดยใช้ field file |
| POST | /api/merchant-applications/draft/submit | ส่งใบสมัคร |

ตรวจ request/response field จาก AuthDtos.java, MerchantDtos.java และ frontend/src/services/api.ts เมื่อพัฒนา endpoint เพื่อไม่ให้เอกสาร snapshot แทนที่สัญญา API จริง

## Data dictionary

| ตาราง | Key และความสัมพันธ์ | กลุ่มข้อมูลสำคัญ |
| --- | --- | --- |
| users | id UUID PK; email และ phone UNIQUE | ชื่อ อีเมล โทรศัพท์ password_hash role status timestamps |
| refresh_tokens | id UUID PK; user_id → users.id; token_hash UNIQUE | hash, expires_at, revoked_at, created_at |
| merchant_applications | id UUID PK; user_id UNIQUE → users.id; reference_no UNIQUE | สถานะ ข้อมูลร้าน ที่อยู่ เจ้าของ ข้อมูลธนาคาร และ timestamps |
| merchant_documents | id UUID PK; application_id → merchant_applications.id | document_type, filename, path, MIME, size, sha256, uploaded_at |

users 1 → N refresh_tokens; users 1 → 0..1 merchant_applications; merchant_applications 1 → N merchant_documents

FK ทั้งสามใช้ ON DELETE CASCADE จึงต้องพิจารณาลูกข้อมูลก่อนลบ parent; ไฟล์ใน uploads ไม่ได้ถูกลบด้วย PostgreSQL FK

merchant_documents มี UNIQUE(application_id, document_type) เพื่อให้แต่ละประเภทมีหนึ่งรายการต่อใบสมัคร

- UserRole: CUSTOMER, MERCHANT, ADMIN
- UserStatus: ACTIVE, SUSPENDED
- ApplicationStatus: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
- DocumentType: LOGO, IDENTITY, PORTRAIT, ADDRESS, BUSINESS
- OwnerType: PERSON, COMPANY; SaleType: OWNER, RESELLER

การมีค่า enum ไม่ได้แปลว่ามี API สำหรับทุก transition แล้ว; ต้องออกแบบการตรวจร้านค้าเพิ่มเติมตาม TASK-005

citizen_id_encrypted และ bank_account_encrypted เป็นข้อมูลเข้ารหัส ห้ามใช้เป็นค่าค้นหาด้วยเลขดิบหรือส่งออกลงเอกสารงาน password_hash และ token_hash ไม่ควรปรากฏในรายงาน

## กติกาการส่งใบสมัครที่พบ

ต้องมีเอกสาร IDENTITY และ ADDRESS; ถ้า owner_type เป็น COMPANY ต้องมี BUSINESS เพิ่ม ตรวจ validation ของข้อมูลร้าน/เจ้าของที่ MerchantApplicationService.java และ MerchantDtos.java

application.yml กำหนด upload สูงสุด 5MB ต่อไฟล์และ 6MB ต่อ request; ตรวจ MerchantStorageService.java สำหรับกติกาชนิดไฟล์เพิ่มเติม

## Documents database

สร้าง Name (Title), Type (Select: Spec/API/Database/Runbook/Decision/Meeting), Project (Relation), Owner (Person), Status (Draft/Review/Published), Updated (Date), Source (Text), URL (URL)

เพิ่มเอกสารในชุดนี้เป็นรายการตั้งต้น แล้วแนบลิงก์หน้า Notion จริงหลัง import ทุกครั้งที่เปลี่ยน API/schema ให้อัปเดตเอกสารพร้อม PR
