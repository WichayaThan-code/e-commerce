# Changelog — authz (Role and Permission Matrix v1.1) รวมเข้า schema v3

วันที่: 2026-10-04 ICT · ผู้อนุมัติ: Supitcha Promyou · ขอบเขต: รวมข้อเสนอ P01–P17 จาก `PERMISSION_GAP_ANALYSIS_TH.md` **เข้า v3 โดยตรง (ไม่แยก V4)**
Backup ก่อนแก้: `/workspace/ecommerce-schema_v3_backup_pre_authz/`

> หลักการ: การตัดสินสิทธิ์ (role → permission) **ยังอยู่ในโค้ด** (Spring Security + `policyVersion` + `permission_version`) ตามเอกสาร.
> DB เพิ่มเฉพาะสิ่งที่ต้อง "รับประกันระดับข้อมูล" + เก็บหลักฐาน/ประวัติ. ตาราง `auth_*` เป็น **READ-ONLY reference** เท่านั้น.

## ตัวเลข

| | ก่อน | หลัง |
| --- | ---: | ---: |
| ตาราง | 64 | **70** (+6) |
| Foreign keys | 152 | **173** (+21) |
| คอลัมน์ (Tables sheet) | 988 | **1,103** (+115 รวม audit 4×6 ของตารางใหม่) |
| ค่า enum (Enums_Status) | 223 | **267** (+44: กลุ่มใหม่ 10 + otp_purpose STEP_UP) |
| CHECK constraints | — | 84 (รวม authz ใหม่ 7 + enum CHECK ของคอลัมน์ใหม่) |
| Partial/expression UNIQUE index | 1 | **7** (+6) |
| Constraint triggers (deferred) | 0 | **2** |
| Process_Evidence_Map | 31 แถว | **40 แถว** (+9 แถว phase "Authz") |

## ตารางใหม่ (6)

| ตาราง | โมดูล | ทำไม (ข้อกำหนด) | จุดสำคัญ |
| --- | --- | --- | --- |
| `auth_roles` | Auth & Identity | §2, §3, §10 catalog | 10 บทบาท (GUEST, BUYER, APPLICANT, STORE_* 6, PLATFORM_ADMIN); `ck_auth_roles_code_format`; READ-ONLY |
| `auth_permissions` | Auth & Identity | §3 permission catalog | 62 รหัส MODULE_ACTION; scope PLATFORM/OWN_ACCOUNT/OWN_APPLICATION/OWN_RESOURCE/STORE/PUBLIC; `is_active` (ไม่ลบเพื่อเก็บประวัติ) |
| `auth_role_permissions` | Auth & Identity | Role × Permission matrix | 150 mapping; UNIQUE(role_id, permission_id); FK CASCADE |
| `merchant_application_reviews` | Merchant Onboarding | R53 ประวัติพิจารณาทุกรอบ | action START_REVIEW/REQUEST_INFO/APPROVE/REJECT + from/to status (CHECK application_status); reviewer FK RESTRICT |
| `shop_customers` | Shop & Team | R11 ลูกค้าระดับร้าน (CUSTOMER_*) | PII แยกจาก users; UNIQUE(shop_id, customer_code); partial UNIQUE(shop_id, user_id) WHERE user_id IS NOT NULL AND deleted_at IS NULL; source/status CHECK |
| `shop_ownership_transfers` | Shop & Team | R28 AUTHZ 04 โอนเจ้าของ | PENDING 1 ใบต่อร้าน (partial unique); from ≠ to (CHECK); step-up timestamps; to_member_id FK **NO ACTION** (ไม่ชน cascade ตอนลบร้าน) |

## ตารางเดิมที่แก้ (P04–P16)

| ตาราง | การเปลี่ยนแปลง |
| --- | --- |
| `users` | + `suspended_at`, `suspended_by` (FK SET NULL), `suspend_reason_code`; `role` = **DEPRECATED** (comment) — V3 step 1 คัดลอก ADMIN → platform_roles, คงคอลัมน์ |
| `platform_roles` | เลิก UNIQUE(user_id) → partial `uq_platform_roles_user_id_active (user_id) WHERE revoked_at IS NULL` (เก็บประวัติ grant/revoke, แต่งตั้งซ้ำได้); + `grant_reason`, `revoked_by` (FK), `revoke_reason`; Relationship 1:1 → N:1 |
| `otp_verifications` | purpose + `STEP_UP`; + `step_up_action` |
| `merchant_applications` | + `withdrawn_at`; `ck_merchant_applications_reviewer_not_applicant` (reviewer_id IS NULL OR reviewer_id <> user_id); `uq_merchant_applications_user_id_open` (1 ใบที่ยังเปิดต่อ user) — V2 UNIQUE(user_id) ถูก drop ใน V3 (ยื่นใหม่หลังปิดได้) |
| `shops` | + `suspended_by`, `suspend_reason_code`, `restored_at`, `restored_by`, `closed_at`, `closed_by`, `close_reason` (FK SET NULL) |
| `shop_members` | + `removed_by` (FK), `remove_reason_code`; `uq_shop_members_shop_id_owner (shop_id) WHERE role='STORE_OWNER' AND status='ACTIVE'`; constraint trigger `trg_shop_members_owner_guard` |
| `shop_invitations` | + `revoked_at`, `revoked_by` (FK); `uq_shop_invitations_shop_id_email_pending (shop_id, lower(email)) WHERE status='PENDING'` |
| `orders` | + `shop_customer_id` (FK shop_customers SET NULL), `row_version` INTEGER NOT NULL DEFAULT 0 (JPA @Version); "Draft/Confirmed unpaid" = PENDING_PAYMENT + payment_status UNPAID/FAILED + ยังไม่ PACKING (ER_Notes, ไม่เพิ่ม DRAFT) |
| `inventory_ledger` | `ck_inventory_ledger_adjust_reason` (ADJUST ต้องมี reason); actor_type ไม่ใส่ CHECK (NO_ENUM_CHECK) |
| `refunds` | `ck_refunds_amount` (amount > 0) |
| `media_files` | + `source_job_id` (FK background_jobs), `required_permission`, `expires_at` |
| `background_jobs` | + `requested_by_user_id` (FK), `auth_context` (CHECK), `required_permission`, `permission_version_at_request`, `policy_version`, `result_file_id` (FK media_files); error_code AUTHORIZATION_REVOKED |
| `audit_logs` | + `auth_context` (CHECK), `service_name`; `ck_audit_logs_service_name` (SERVICE ต้องมี service_name); CHECK ใหม่ `actor_type`, `outcome`; index (shop_id, occurred_at), (actor_id, occurred_at), (resource_type, resource_id) |
| `platform_settings` | + `is_admin_editable` (allowlist ของ PLATFORM_SETTING_UPDATE), `value_schema`, `description` |

## กลไกระดับ DB ที่เพิ่ม

1. **Owner เดียวต่อร้าน** — partial unique + `check_shop_single_owner()` เรียกจาก constraint triggers `trg_shop_members_owner_guard` (AFTER INSERT/UPDATE/DELETE ON shop_members) และ `trg_shops_owner_guard` (AFTER INSERT/UPDATE OF owner_user_id, status, deleted_at ON shops) แบบ **DEFERRABLE INITIALLY DEFERRED** → ตรวจตอน COMMIT: ร้านที่ไม่ CLOSED/ไม่ถูกลบ ต้องมี ACTIVE STORE_OWNER = 1 คน และ = `shops.owner_user_id`
   - การ approve ใบสมัคร (สร้าง shop + owner member) และการโอนเจ้าของ **ต้องอยู่ใน transaction เดียว**
   - ลำดับโอน: เจ้าของเดิม → STORE_ADMIN ก่อน, แล้วผู้รับ → STORE_OWNER, แล้ว `shops.owner_user_id` (partial unique ตรวจทันที)
2. **ห้าม self-review**, **1 ใบสมัครที่เปิดต่อ user**, **1 คำเชิญ PENDING ต่อ email (ไม่สนตัวพิมพ์)**, **1 platform role active ต่อ user**, **1 คำขอโอน PENDING ต่อร้าน**
3. **Catalog seed (idempotent)** — `seed_reference_data.sql` และ Flyway V3: upsert by code (UPDATE เฉพาะเมื่อค่าต่าง), mapping ON CONFLICT DO NOTHING + ลบ mapping ที่ไม่อยู่ใน catalog, permission ที่หายไปถูก `is_active=false`

## Flyway V3 (ยังไม่ได้รันบน shared DB ของผู้ใช้)

- ยังรันต่อจาก V1/V2 เดิมได้ (users, refresh_tokens, merchant_applications, merchant_documents) — ทดสอบด้วย psql `-1` และ **Flyway CLI 11.8.2** จริง
- `users.role` — step 1 เท่านั้น: `INSERT INTO platform_roles … SELECT … WHERE role='ADMIN' AND NOT EXISTS active row`, คงคอลัมน์ + COMMENT DEPRECATED. Step 2 (DROP COLUMN) ทำใน migration ถัดไปหลังแก้ JPA/โค้ด login
- `merchant_applications` (ตาราง V2) — guard ข้อมูลเดิม:
  - CHECK self-review: ถ้ามีแถวผิด → `ADD CONSTRAINT … NOT VALID` + `RAISE WARNING` (แถวใหม่ยังโดนตรวจ) ไม่งั้นเพิ่มแบบ validated
  - partial unique open application: ถ้ามีใบเปิดซ้ำ → `RAISE EXCEPTION` พร้อมวิธีแก้ (V2 มี UNIQUE(user_id) จึงไม่ควรเกิด)
- seed catalog (10/62/150) อยู่ท้าย V3 ด้วย

## ผลต่อ Java (ddl-auto=validate)

- คอลัมน์ใหม่บนตาราง V1/V2 เป็น nullable หรือมี default → entity เดิม **ไม่พังจาก validate** เพราะ Hibernate ไม่ตรวจคอลัมน์เกิน
- ควรเพิ่ม field: `UserAccount` (suspendedAt, suspendedBy, suspendReasonCode; role → @Deprecated), `MerchantApplication` (withdrawnAt) และเลิกพึ่ง unique(user_id) ใน service (ใช้ status open แทน)
- ตารางใหม่ต้องมี entity เมื่อ implement (ShopCustomer, ShopOwnershipTransfer, MerchantApplicationReview; auth_* ใช้ read-only หรือไม่ map)
- โค้ดที่ authorize ด้วย `users.role` ต้องย้ายไป `platform_roles` ก่อน step 2

## สถานะข้อกำหนด (67 ข้อ)

| | ก่อน | หลัง |
| --- | ---: | ---: |
| Supported | 31 | **51** |
| Partial | 19 | **2** (R01 users.role ยังอยู่จนถึง step 2 · R63 เอกสารยังไม่มี PAYMENT_PROOF_REVIEW) |
| Not supported | 3 | **0** |
| App-layer | 14 | 14 |

รายละเอียด: Excel ชีต **Permission_Support** และ `PERMISSION_GAP_ANALYSIS_TH.md` / `Permission_Gap_Analysis.xlsx` (คอลัมน์สถานะหลัง merge)
