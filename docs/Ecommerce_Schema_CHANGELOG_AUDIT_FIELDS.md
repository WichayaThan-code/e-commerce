# Changelog — มาตรฐาน Audit Fields + เวลาไทย (schema v3, 2026-10-02)

สำรองสถานะก่อนแก้: `/workspace/ecommerce-schema_v3_backup_pre_audit/`

## สิ่งที่เปลี่ยน
- **ทุกตาราง 64/64** มี 4 คอลัมน์ท้ายตารางตามลำดับ:
  `created_date_time` TIMESTAMPTZ NOT NULL DEFAULT now() · `created_by` UUID NULL · `modified_date_time` TIMESTAMPTZ NOT NULL DEFAULT now() · `modified_by` UUID NULL
- rename `created_at` → `created_date_time`, `updated_at` → `modified_date_time`, `updated_by` → `modified_by` (ไม่เหลือชื่อเดิมใน schema)
- ตาราง append-only (audit_logs, inventory_ledger, order_status_history, outbox_events, integration_events, shipment_tracking_events ฯลฯ) ก็มี `modified_date_time` (= created_date_time ตอน insert)
- `created_by`/`modified_by` = logical ref `users.id` **ไม่มี FK** (NULL = system/job/webhook) — ลบ FK เดิม 7 ตัว:
  | ตาราง | เดิม | ตอนนี้ |
  |---|---|---|
  | products | created_by FK, updated_by FK (SET NULL) | created_by, modified_by (UUID, no FK) |
  | orders | created_by FK (SET NULL) "buyer หรือ staff" | created_by (no FK) |
  | promotions | created_by FK (SET NULL) | created_by (no FK) |
  | background_jobs | created_by FK (SET NULL) | created_by (no FK) |
  | product_imports | created_by FK **NOT NULL, RESTRICT** | created_by **nullable**, no FK (แอปควรใส่ค่าเสมอ) |
  | platform_settings | updated_by FK (SET NULL) | modified_by (no FK) |
- **FK: 159 → 152**, ตาราง 64, คอลัมน์ 824 → 988
- trigger: `set_updated_at()` → `set_modified_date_time()`; `trg_<table>_modified_date_time` ทุกตาราง (31 → 64); `modified_by` แอปตั้งเอง
- index: `ix_orders_shop_id_created_date_time`, `ix_payment_proofs_shop_id_status_created_date_time`, `ix_otp_verifications_destination_hash_purpose_created_date_time`
- ไม่แตะ: business timestamp (paid_at, confirmed_at, delivered_at, verified_at, expires_at, uploaded_at, occurred_at …) และ business actor (packed_by, verified_by, confirmed_by_user_id, uploaded_by, changed_by, granted_by, invited_by …)

## เวลาไทย (Asia/Bangkok, UTC+7)
- คอลัมน์เวลาทั้งหมดเป็น TIMESTAMPTZ (เก็บ instant จริง) แสดงผลเป็นเวลาไทย
- full script / module 00 / Flyway V3: `SET TIME ZONE 'Asia/Bangkok';` + `ALTER DATABASE <db> SET timezone TO 'Asia/Bangkok'` (ผ่าน DO + format; ถ้าไม่มีสิทธิ์ owner → WARNING ให้ DBA ตั้งเอง)
- Spring Boot: `spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Bangkok`, `spring.jackson.time-zone=Asia/Bangkok`, entity ใช้ `OffsetDateTime` หรือ `Instant` (ไม่ใช้ LocalDateTime)

## Flyway V3 กับตาราง V1/V2
- users, refresh_tokens, merchant_applications: `RENAME COLUMN` แบบมี guard (DO + information_schema) → ข้อมูลเดิมคงอยู่
- `ADD COLUMN IF NOT EXISTS` created_by / modified_by / modified_date_time ที่ยังไม่มี; backfill refresh_tokens.modified_date_time = created_date_time, merchant_documents.created/modified_date_time = uploaded_at
- คอลัมน์ที่ rename ไม่อยู่ท้ายตารางทางกายภาพ (PostgreSQL ย้ายไม่ได้) — ไม่มีผลกับแอป

## ⚠️ ต้องแก้ JPA ก่อน deploy V3 (backend ใช้ `ddl-auto: validate`)
- `auth/UserAccount.java` — `@Column(name="created_at")`, `@Column(name="updated_at")` → `created_date_time`, `modified_date_time`
- `auth/RefreshToken.java` — `@Column(name="created_at")` → `created_date_time`
- `merchant/MerchantApplication.java` — `created_at`, `updated_at` → `created_date_time`, `modified_date_time` (+ getter ถ้าเปลี่ยนชื่อ field)
- `merchant/MerchantDocument.java` — ไม่ต้องแก้
- (optional) map `createdBy`/`modifiedBy` เป็น UUID

## ผลทดสอบ
full script, drop_all + modules, seed, smoke test, V1+V2(+ข้อมูลเดิม)+V3, Flyway CLI 11.8.2 จริง — ผ่านทั้งหมด; `SHOW timezone` = Asia/Bangkok และตัวอย่าง insert แสดง `+07` (ดู `sql/tests/validation_log_2026-10-02.txt`)
