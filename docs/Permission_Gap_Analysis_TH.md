# Permission Gap Analysis — Ecommerce PostgreSQL Schema v3 เทียบเอกสาร Role and Permission Matrix

วันที่วิเคราะห์: 2026-10-04 (เวลาไทย, ICT) · ประเภท: วิเคราะห์ (read-only) แล้ว **นำข้อเสนอไปใช้ใน v3 วันเดียวกัน**

> **อัปเดต 2026-10-04 (ICT): ข้อเสนอ P01–P17 ทั้งหมดถูกรวมเข้า schema v3 แล้ว (ผู้ใช้อนุมัติ — ไม่แยก V4)** — schema ตอนนี้ 70 ตาราง · 173 FK; ดู `CHANGELOG_AUTHZ_TH.md` และชีต Permission_Support ใน `Ecommerce_PostgreSQL_Schema.xlsx`. เนื้อหาด้านล่างคงผลวิเคราะห์เดิม (สถานะ 'ก่อน') และเพิ่มคอลัมน์ 'สถานะหลัง merge'; ไฟล์ V4 draft ใน /workspace/perm_gap เป็นประวัติ ไม่ต้องใช้แล้ว

Excel คู่กัน: `/workspace/ecommerce-schema/Permission_Gap_Analysis.xlsx`

## 1. ขอบเขตและแหล่งข้อมูล

- เอกสารสิทธิ์: **Role and Permission Matrix (Phase 1 System Architecture) — เวอร์ชันในตัวเอกสาร 1.1 Baseline / ตารางประวัติ 1.0 (26 Sep 2026)** — ไฟล์ .docx ไม่มีรูปภาพฝังอยู่ (ตรวจใน word/media แล้วไม่มี) เนื้อหาเป็นข้อความ + 10 ตาราง
- Schema: `/workspace/ecommerce-schema/` (schema_data_*.py, schema_enums.py, schema_meta.py, sql/ecommerce_schema_v3_full.sql) — 64 ตาราง · 152 FK; Flyway เดิม V1 (users, refresh_tokens) / V2 (merchant_applications, merchant_documents)
- บริบทธุรกิจ: แพลตฟอร์มไม่ถือเงิน (ลูกค้าจ่ายเข้า shop_payment_gateways ของร้าน), ไม่มี COD, payment-first (ร้านเห็นยอดก่อนยืนยันจึงเห็นรายการ), audit 4 คอลัมน์ทุกตาราง, TIMESTAMPTZ Asia/Bangkok

## 2. สรุปโมเดลสิทธิ์ในเอกสาร

**บริบท (Authorization context) 4 แบบ ไม่รวมกัน:** Public (Guest) · Account (Buyer / Applicant — บัญชีเดียวเป็นได้ทั้งสอง) · Store (Store role 1 บทบาทต่อร้าน, ใช้ได้เฉพาะสมาชิก Active) · Platform (PLATFORM_ADMIN แยกต่างหาก ไม่ได้เป็นสมาชิกทุกร้านอัตโนมัติ). Phase 1 ไม่มี custom role และสิทธิ์รายบุคคล

| บทบาท | บริบท | สรุปหน้าที่ (ตามเอกสาร) | จำนวนสิทธิ์ |
| --- | --- | --- | --- |
| GUEST | Public | ดู catalog สาธารณะ (ร้าน Active + สินค้าเผยแพร่) ไม่มี guest checkout | 1 |
| BUYER | Account (own) | โปรไฟล์/ที่อยู่/ตะกร้า/checkout/เริ่มชำระ/ดูและยกเลิกออเดอร์ของตนเอง | 12 |
| APPLICANT | Account (own application) | สร้าง/แก้/อัปโหลด/ส่ง/ถอนใบสมัครเปิดร้านของตนเอง | 6 |
| STORE_OWNER | Store | ทุกสิทธิ์ในร้าน (ประกาศทีละรหัส ไม่ใช้ wildcard) รวมทีม คืนเงิน ปิดร้าน โอนเจ้าของ | 34 |
| STORE_ADMIN | Store | ดูแลร้านและทีม ยกเว้น SHOP_CLOSE / OWNERSHIP_TRANSFER; จัดการได้เฉพาะ Manager/Sales/Warehouse/Viewer | 32 |
| STORE_MANAGER | Store | สินค้า สต็อก ลูกค้า ออเดอร์ (รวมยกเลิก) จัดส่ง รายงาน/ส่งออก — ไม่มีทีม/คืนเงิน/audit | 25 |
| STORE_SALES | Store | ค้นข้อมูลขาย ลูกค้า (รวม PII) สร้าง/แก้ออเดอร์ก่อนชำระ | 12 |
| STORE_WAREHOUSE | Store | ดูสินค้า/สต็อก รับเข้า ปรับสต็อก แพ็ก ส่ง ดูที่อยู่ส่งของ (ไม่เห็นข้อมูลการขาย/ลูกค้า) | 11 |
| STORE_VIEWER | Store | อ่านข้อมูลธุรกิจแบบปกปิด PII ไม่มีเขียน/ส่งออก | 7 |
| PLATFORM_ADMIN | Platform | ร้าน (ดู/ระงับ/คืน), ใบสมัคร (ดู/พิจารณา), บัญชี (ดู/ระงับ/คืน), ค่าระบบ, platform audit — ไม่ได้สิทธิ์แก้ออเดอร์/สต็อก/คืนเงินของร้าน | 10 |

**Permission:** รหัส `MODULE_ACTION` (UPPER_SNAKE_CASE) ความหมายเดียวต่อรหัส รวม 62 รหัส (Store 34 · Platform 10 · Onboarding 6 · Storefront 12). Scope: Platform / Own account / Own application / Own resource / Store / Public. Catalog + role mapping + `policyVersion` อยู่ใน Repository เดียว สร้าง constants ให้ FE/BE และ CI ตรวจ endpoint mapping

**กฎบังคับใช้ (AUTHZ 01–08):** deny by default (รวม Owner/Platform Admin กับรหัสที่ไม่รู้จัก) · ตรวจที่ backend ทุกครั้งตามลำดับ context→scope/permission→โหลด resource ใต้เจ้าของ→field+สถานะ→transaction · จำกัดอำนาจจัดการทีมของ Store Admin และห้ามแก้บทบาทตนเอง · Owner หนึ่งคนต่อร้าน โอนด้วย re-auth + ผู้รับยอมรับ + atomic (เจ้าของเดิมเป็น Store Admin) · field allowlist ห้าม mass assignment · ถอน/เปลี่ยนบทบาทเพิ่ม permissionVersion ล้าง cache และ worker ตรวจซ้ำ · งานระบบใช้ service identity · audit actorId/shopId/permission/targetId/outcome/reasonCode/requestId/policyVersion โดยไม่เก็บ token/PII

**การมองเห็นข้อมูล:** ORDER_READ ไม่ให้ PII อัตโนมัติ; Warehouse เห็น SKU/จำนวน/ข้อมูลแพ็ก ไม่เห็นยอดขาย/ส่วนลด/บันทึกภายใน; Viewer ไม่มีเบอร์/อีเมล/ที่อยู่; Warehouse อ่านที่อยู่เต็มเฉพาะงาน Ready to pack→Dispatched; export ไม่มี PII; public response ห้ามมี ledger/ต้นทุน/draft/PII

**เมนู/หน้าจอ:** UI ใช้ permission (ไม่ใช่ role === ADMIN) ซ่อน/เปิดเมนู route ปุ่ม; ระหว่างโหลดสิทธิ์ไม่เปิด action; ล้มเหลว = ปิด action + Retry; `allowedActions` ใน order response; endpoints `/api/v1/me/permissions`, `/api/v1/platform/me/permissions`, `/api/v1/shops/{shopId}/me/permissions`. Error 401/403/404/409 ห้ามเปิดเผยร้านอื่น

**Approval flows:** (1) ใบสมัคร Draft→Submitted→(Needs information↔Submitted)→Approved/Rejected, Withdrawn; Platform Admin อนุมัติ → สร้าง Shop + STORE_OWNER membership + Active ใน transaction เดียว (applicationId unique), ผู้ตรวจห้ามอนุมัติของตนเอง, 1 ใบค้างต่อบัญชี (2) คำเชิญทีม (shop+role+expiry, ห้ามเชิญเป็น Owner) (3) โอนเจ้าของ (4) ระงับ/คืนร้านและบัญชีพร้อมเหตุผล + step-up (5) แต่งตั้ง Platform Admin ผ่านกระบวนการภายในเท่านั้น

**ข้อสังเกตในเอกสาร:** ตารางประวัติมีแค่ 1.0 แต่ข้อความระบุ 1.1 Baseline · อ้างอิงเลขหน้า ("หน้า 3 ถึง 5", "หน้า 6") ไม่ตรงหัวข้อ · สถานะออเดอร์ 'Draft/Confirmed' ไม่ตรงกับ flow payment-first ของ schema · ไม่มีสิทธิ์สำหรับการยืนยันออเดอร์ของร้าน (merchant confirm) และการตรวจสลิป (ดูหัวข้อ 6)

## 3. สิ่งที่ schema v3 มีอยู่สำหรับ Auth / RBAC

| ตาราง | คอลัมน์/constraint ที่เกี่ยวข้อง | บทบาทในโมเดลสิทธิ์ |
| --- | --- | --- |
| users | role VARCHAR(30) CHECK (CUSTOMER\|MERCHANT\|ADMIN) default 'CUSTOMER' — legacy V1; status CHECK (ACTIVE\|SUSPENDED); permission_version INTEGER; deleted_at | บทบาทเดี่ยวแบบเดิม + cache bust ระดับบัญชี |
| refresh_tokens | token_hash UNIQUE, expires_at, revoked_at, user_agent, ip_address | เพิกถอน session เมื่อระงับ/logout |
| platform_roles | user_id FK UNIQUE (0..1 ต่อ user), role CHECK ('PLATFORM_ADMIN'), granted_by FK, granted_at, revoked_at | Platform role แยกจาก store role |
| otp_verifications | purpose CHECK (SIGNUP\|LOGIN\|PHONE_VERIFY\|EMAIL_VERIFY\|PASSWORD_RESET), code_hash, status | ยืนยันตัวตน (ยังไม่มี STEP_UP) |
| merchant_applications | user_id FK (ไม่มี UNIQUE แล้วใน V3), status CHECK (DRAFT\|SUBMITTED\|UNDER_REVIEW\|NEEDS_INFORMATION\|APPROVED\|REJECTED\|WITHDRAWN), reviewer_id FK, review_note, reviewed_at, shop_id FK UNIQUE | Applicant context + approval |
| merchant_documents | application_id FK, document_type UNIQUE per application, storage_path, sha256 | เอกสาร private |
| shops | status CHECK (ACTIVE\|SUSPENDED\|CLOSED), owner_user_id FK NOT NULL, application_id UNIQUE, suspended_at, suspend_reason, published_at, deleted_at | tenant root + Store Owner ปัจจุบัน |
| shop_members | UNIQUE(shop_id,user_id), role CHECK (STORE_OWNER\|STORE_ADMIN\|STORE_MANAGER\|STORE_SALES\|STORE_WAREHOUSE\|STORE_VIEWER), status CHECK (ACTIVE\|INACTIVE), permission_version, invited_by FK, joined_at, removed_at | Store role 1 ค่าต่อร้าน |
| shop_invitations | role CHECK (ไม่รวม STORE_OWNER), token_hash UNIQUE, status CHECK (PENDING\|ACCEPTED\|EXPIRED\|REVOKED), invited_by, expires_at, accepted_at, accepted_user_id | คำเชิญผูกร้าน+บทบาท+วันหมดอายุ |
| audit_logs | actor_id, actor_type (USER\|SERVICE\|SYSTEM\|OMS — ไม่มี CHECK), shop_id FK, action, resource_type, resource_id, permission, outcome (SUCCESS\|DENIED\|FAILURE — ไม่มี CHECK), reason_code, before_data, after_data, request_id, policy_version | ตรง AUTHZ 08 ครบทุก field |
| platform_settings | setting_key UNIQUE, setting_value JSONB | ค่าระบบ (allowlist อยู่ใน comment) |
| background_jobs / product_imports | job_type, status, error_code, request_id, shop_id / product_imports.file_id, job_id, error_report_file_id | งาน import/export (ยังไม่ผูกผู้สั่ง/สิทธิ์) |
| media_files | shop_id, uploader_id, purpose (… REPORT\|IMPORT …), visibility (PRIVATE\|PUBLIC) | ไฟล์ |
| อื่น ๆ ที่ใช้ตรวจ scope | orders.buyer_user_id, checkouts.buyer_user_id NOT NULL, carts (partial UNIQUE buyer+shop ACTIVE), buyer_addresses.buyer_user_id, order_addresses (snapshot), inventory_ledger.actor_id/actor_type/reason, order_status_history.changed_by/changed_by_type, refunds.idempotency_key/initiated_by, payment_webhook_events UNIQUE(provider,event_id) | Own resource / idempotency / ledger |
| ไม่มีใน schema | roles / permissions / role_permissions / user_roles tables (ตั้งใจ: catalog อยู่ในโค้ด — ER_Notes 'Permissions'); shop_customers; ownership transfer; application review history | — |

## 4. ผลสรุป (Verdict)

**รองรับบางส่วน (Partially supported) — โครงหลักครบ แต่ยังมีช่องว่างที่ต้องปิดก่อนพัฒนา:** schema v3 ถูกออกแบบมาตามโมเดลนี้แล้วในระดับรากฐาน (6 store roles ชื่อตรง, platform_roles แยก, shop_members+permission_version, คำเชิญห้าม Owner, audit_logs ครบ field AUTHZ 08, onboarding idempotent, own-resource keys ครบ) และเลือกเก็บ permission catalog ในโค้ดตาม §10 — ช่องว่างหลักอยู่ที่ entity ที่ขาด (ลูกค้าร้าน, การโอนเจ้าของ), constraint ที่ระบุใน comment แต่ไม่ได้บังคับ (Owner เดียว, ใบสมัครค้างเดียว, ห้ามตรวจตนเอง) และ metadata สำหรับงานค้าง/การระงับ

| สถานะ | จำนวน | ความหมาย |
| --- | --- | --- |
| Supported | 31 | schema มีโครงสร้างพอ (อาจต้องให้แอปบังคับร่วม) |
| Partial | 19 | มีบางส่วน ขาดคอลัมน์/constraint/ประวัติ |
| Not supported | 3 | ไม่มีโครงสร้างรองรับใน DB |
| App-layer | 14 | เป็นกฎของ Spring Security/API/UI ไม่ต้องมีใน DB (ตรวจแล้วว่าข้อมูลที่ต้องใช้มีอยู่) |
| รวม | 67 |  |

**หลัง merge เข้า v3 (2026-10-04):** Supported 31 → **51** · Partial 19 → **2** · Not supported 3 → **0** · App-layer 14 → **14** — Partial ที่เหลือ: R01 (users.role ยังอยู่จนถึง step 2 DROP) และ R63 (เอกสารยังไม่มี permission PAYMENT_PROOF_REVIEW)


**ช่องว่างสำคัญ (Top gaps):**

1. **R11 (§5) — Not supported:** CUSTOMER_READ / CUSTOMER_PII_READ / CUSTOMER_CREATE / CUSTOMER_UPDATE — ทีมร้านดู เพิ่ม และแก้ 'ลูกค้าของร้าน' (Sales เพิ่มลูกค้าสำหรับการขาย) → CUSTOMER_CREATE ไม่มีที่เก็บ; CUSTOMER_UPDATE จะไปแก้ users ซึ่งเป็นบัญชีกลางของผู้ซื้อ (ขัด BUYER_PROFILE_UPDATE ที่ต้องเป็นของตนเอง และข้ามร้าน); ออเดอร์ MANUAL ไม่มีลูกค้าผูก _(ข้อเสนอ P01)_
2. **R28 (AUTHZ 04) — Not supported:** โอนเจ้าของ: ยืนยันตัวตนซ้ำ, ผู้รับเป็น Active member และยอมรับ, เปลี่ยนทั้งคู่ใน Transaction เดียว, เจ้าของเดิมเป็น Store Admin, มี Audit → ไม่มีตารางคำขอโอน/การยอมรับของผู้รับ/วันหมดอายุ/หลักฐาน re-auth จึงทำ flow สองฝ่าย (เสนอ → ยอมรับ) ไม่ได้ _(ข้อเสนอ P02, P15)_
3. **R27 (AUTHZ 04) — Partial:** หนึ่งร้านมี Store Owner หนึ่งคน → ไม่มี partial unique index บน shop_members สำหรับ STORE_OWNER — อาจมี Owner หลายแถว หรือไม่ตรงกับ shops.owner_user_id _(ข้อเสนอ P05)_
4. **R33 (AUTHZ 06,§11) — Partial:** Worker ของ import/export ตรวจสิทธิ์ซ้ำก่อนทำและก่อนเผยแพร่ไฟล์; ถ้าถูกถอนจบด้วย AUTHORIZATION_REVOKED; ดาวน์โหลดผลเดิมตรวจสิทธิ์ปัจจุบัน → error_code เป็นข้อความอิสระ (เก็บ AUTHORIZATION_REVOKED ได้) แต่ไม่มี requested_by_user_id / required_permission / auth_context ให้ Worker รู้ว่าต้องตรวจใครด้วยสิทธิ์อะไร _(ข้อเสนอ P11)_
5. **R13 (§5,§11) — Partial:** Sales ใช้ ORDER_UPDATE ได้เฉพาะออเดอร์ Draft/Confirmed ที่ยังไม่ชำระและยังไม่เริ่มแพ็ก; ถ้าถูกชำระระหว่างแก้ต้องตอบ 409 → สถานะในเอกสาร (Draft/Confirmed) ไม่มีใน schema v2/v3 (ใช้ PENDING_PAYMENT → PAID_AWAITING_MERCHANT_CONFIRM → MERCHANT_CONFIRMED…) ต้อง map ให้ชัด; ไม่มีคอลัมน์ optimistic lock (row_version) สำหรับตรวจการแข่งกันแก้/ชำระ → 409 _(ข้อเสนอ P14)_
6. **R56 (§14) — Partial:** หนึ่งบัญชีมีใบสมัครที่ยังดำเนินการได้ 1 ใบ; ผู้ถูกปฏิเสธสมัครใหม่ได้ → ไม่มี partial unique index กันใบสมัครค้างพร้อมกันหลายใบ (comment ระบุไว้แต่ไม่ได้สร้าง) _(ข้อเสนอ P04)_
7. **R52 (§14) — Not supported:** ผู้ตรวจไม่อนุมัติใบสมัครของตนเอง → ไม่มี CHECK reviewer_id <> user_id _(ข้อเสนอ P04)_
8. **R01 (§1) — Partial:** หนึ่งบัญชีเป็น Buyer และ Applicant ได้ มี Store role 1 บทบาทต่อร้าน และ Platform role แยกต่างหาก → โครงสร้างหลักรองรับ แต่ users.role (CUSTOMER|MERCHANT|ADMIN, legacy V1, NOT NULL) เป็นบทบาทเดี่ยวต่อบัญชี ขัดกับโมเดลหลายบริบท และขัด §9 ที่ห้ามตรวจ role === ADMIN — ต้องเลิกใช้ในการ authorize _(ข้อเสนอ P08)_
9. **R46 (§13) — Partial:** Platform Admin แต่งตั้งผ่านกระบวนการภายในที่มี Audit ห้ามสมัครเอง ไม่มี Public API → UNIQUE(user_id) ทำให้แต่งตั้งซ้ำหลังเพิกถอนไม่ได้ (ต้องลบประวัติ); ไม่มี revoked_by / grant_reason _(ข้อเสนอ P07)_
10. **R43 (§13) — Partial:** PLATFORM_ACCOUNT_SUSPEND / RESTORE ระงับบัญชีและเพิกถอน Session พร้อมเหตุผล → เพิกถอน session ได้ แต่ users ไม่มี suspended_at / suspended_by / suspend_reason_code _(ข้อเสนอ P08)_

## 5. Requirement Matrix

| ID | หัวข้อเอกสาร | ด้าน | ข้อกำหนด | สถานะ (ก่อน) | สถานะหลัง merge | ตาราง/คอลัมน์ที่รองรับ (ก่อน) | หมายเหตุช่องว่าง | บังคับที่ | ข้อเสนอ | DB support หลัง merge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R01 | §1 | บริบทบัญชี | หนึ่งบัญชีเป็น Buyer และ Applicant ได้ มี Store role 1 บทบาทต่อร้าน และ Platform role แยกต่างหาก | Partial | Partial | shop_members UNIQUE(shop_id,user_id) + ck_shop_members_role; platform_roles (แยกตาราง); merchant_applications.user_id | โครงสร้างหลักรองรับ แต่ users.role (CUSTOMER\|MERCHANT\|ADMIN, legacy V1, NOT NULL) เป็นบทบาทเดี่ยวต่อบัญชี ขัดกับโมเดลหลายบริบท และขัด §9 ที่ห้ามตรวจ role === ADMIN — ต้องเลิกใช้ในการ authorize | DB+App | P08 | shop_members.role / platform_roles.role เป็น source of truth; users.role ยังอยู่ (DEPRECATED step 1 — DROP ใน migration ถัดไป) |
| R02 | §1 | บทบาท | Phase 1 ไม่รองรับ Custom role หรือสิทธิ์รายบุคคล | Supported | Supported | ck_shop_members_role (enum คงที่ 6 ค่า); ไม่มีตาราง user_permissions | ตรงตามเอกสาร | DB |  |  |
| R03 | §1,§13 | บทบาท | Platform Admin ไม่ทำให้เป็นสมาชิก/Store Owner ของทุกร้าน | Supported | Supported | platform_roles แยกจาก shop_members | ตรงตามเอกสาร; แอปต้องไม่ bypass Store API ด้วย platform role | DB+App |  |  |
| R04 | §2,§10 | บทบาท | บทบาทร้าน 6 ค่า ใช้ชื่อ API STORE_OWNER, STORE_ADMIN, STORE_MANAGER, STORE_SALES, STORE_WAREHOUSE, STORE_VIEWER | Supported | Supported | shop_members.role CHECK ck_shop_members_role; Enums_Status store_role | ชื่อตรงกันทุกค่า | DB |  |  |
| R05 | §3,§10 | Permission catalog | รหัส MODULE_ACTION, Role mapping และ policyVersion เก็บใน Repository เดียว สร้าง Constants ให้ FE/BE; ไม่ใช้ wildcard | Supported | Supported | ER_Notes 'Permissions' (ไม่เก็บ permission เป็นแถวใน DB Phase 1); audit_logs.policy_version | ตรงตามการออกแบบ (code-first) — DB ไม่จำเป็นต้องมีตาราง permission; มีทางเลือกตาราง reference (P17) ถ้าต้องการรายงาน/ตรวจ CI จาก DB | App |  | catalog อ้างอิง auth_roles / auth_permissions / auth_role_permissions (READ-ONLY, policy_version) — enforcement ในโค้ด |
| R06 | §3 | Permission catalog | Store Owner ได้ทุก Permission ที่ประกาศแล้ว (ไม่ใช้ wildcard) | App-layer | App-layer | — | กำหนดใน role mapping ของโค้ด | App |  |  |
| R07 | §3,AUTHZ 02 | Scope ร้าน | สิทธิ์ร้านใช้ได้เฉพาะสมาชิก Active ของร้านนั้น | Supported | Supported | shop_members.status (ACTIVE\|INACTIVE), removed_at, UNIQUE(shop_id,user_id) | ตรงตามเอกสาร | DB+App |  |  |
| R08 | §4 | สินค้า | PRODUCT_ARCHIVE เป็น Soft archive ห้ามลบประวัติสินค้าในออเดอร์ | Supported | Supported | products.status (DRAFT\|PUBLISHED\|ARCHIVED), products.archived_at, deleted_at; order_items.product_name_snapshot / sku_code_snapshot / option_label_snapshot | รองรับครบ | DB |  |  |
| R09 | §4,AUTHZ 06 | สินค้า | PRODUCT_IMPORT ตรวจทั้ง PRODUCT_IMPORT และ CREATE/UPDATE รายแถว; Worker ตรวจสิทธิ์ซ้ำก่อนทำ | Partial | Supported | product_imports (shop_id, file_id, job_id, status, checkpoint_row, error_report_file_id); background_jobs | ไม่มีคอลัมน์ผู้สั่ง (มีแค่ created_by แบบ audit, nullable) และไม่มี required_permission / permission_version ตอนสั่ง ให้ Worker ใช้ตรวจซ้ำ | DB+App | P11 | background_jobs.requested_by_user_id + required_permission + permission_version_at_request → worker ตรวจสิทธิ์ซ้ำ; product_imports เดิม |
| R10 | §5 | สต็อก | INVENTORY_RECEIVE/ADJUST ต้องมีเหตุผล ยอดก่อน-หลัง ผ่าน Ledger เท่านั้น | Supported | Supported | inventory_ledger.entry_type (RECEIVE\|ADJUST\|...), on_hand_before, on_hand_after, reason, actor_id, actor_type, idempotency_key | โครงสร้างครบ; reason เป็น NULL ได้ — แนะนำ CHECK ให้ ADJUST ต้องมี reason (P16) | DB+App | P16 | ck_inventory_ledger_adjust_reason: ADJUST ต้องมี reason |
| R11 | §5 | ลูกค้า | CUSTOMER_READ / CUSTOMER_PII_READ / CUSTOMER_CREATE / CUSTOMER_UPDATE — ทีมร้านดู เพิ่ม และแก้ 'ลูกค้าของร้าน' (Sales เพิ่มลูกค้าสำหรับการขาย) | Not supported | Supported | ไม่มีตาราง customer ระดับร้าน — มีเพียง users (บัญชีกลางทั้งแพลตฟอร์ม), orders.buyer_user_id, order_addresses (snapshot) | CUSTOMER_CREATE ไม่มีที่เก็บ; CUSTOMER_UPDATE จะไปแก้ users ซึ่งเป็นบัญชีกลางของผู้ซื้อ (ขัด BUYER_PROFILE_UPDATE ที่ต้องเป็นของตนเอง และข้ามร้าน); ออเดอร์ MANUAL ไม่มีลูกค้าผูก | DB+App | P01 | ตารางใหม่ shop_customers (tenant shop_id, PII, source, status) + uq(shop_id,customer_code), partial uq(shop_id,user_id) |
| R12 | §5 | ออเดอร์ | ORDER_CREATE — ทีมร้าน (Owner/Admin/Manager/Sales) สร้างออเดอร์ | Partial | Supported | orders.channel 'MANUAL', orders.buyer_user_id (NULL ได้), order_items, order_addresses | สร้างได้ แต่ไม่มี FK ไปลูกค้าร้าน (P01) และ flow ชำระก่อน (payment-first) ของ storefront ต้องกำหนดว่าออเดอร์ MANUAL ชำระอย่างไร (ไม่มี COD) | DB+App | P01, P14 | orders.shop_customer_id FK→shop_customers (MANUAL/OMS) + orders.channel |
| R13 | §5,§11 | ออเดอร์ | Sales ใช้ ORDER_UPDATE ได้เฉพาะออเดอร์ Draft/Confirmed ที่ยังไม่ชำระและยังไม่เริ่มแพ็ก; ถ้าถูกชำระระหว่างแก้ต้องตอบ 409 | Partial | Supported | orders.status, orders.payment_status, shipments.status | สถานะในเอกสาร (Draft/Confirmed) ไม่มีใน schema v2/v3 (ใช้ PENDING_PAYMENT → PAID_AWAITING_MERCHANT_CONFIRM → MERCHANT_CONFIRMED…) ต้อง map ให้ชัด; ไม่มีคอลัมน์ optimistic lock (row_version) สำหรับตรวจการแข่งกันแก้/ชำระ → 409 | DB+App | P14 | สถานะแก้ไขได้ = PENDING_PAYMENT + payment_status UNPAID/FAILED + ยังไม่ PACKING (ER_Notes 'Order editable'); orders.row_version optimistic lock |
| R14 | §5 | ออเดอร์ | ORDER_UPDATE ห้ามแก้ราคาย้อนหลังของรายการที่ชำระแล้ว และห้ามเปลี่ยนยอดชำระโดยตรง | App-layer | App-layer | payments แยกตาราง; orders.*_total | บังคับที่ service (field allowlist) — อาจเสริม trigger ป้องกันแก้ order_items เมื่อ payment_status='PAID' (ทางเลือก) | App |  |  |
| R15 | §5 | ออเดอร์ | ORDER_CANCEL ตามสถานะ | Supported | Supported | order_cancellations (requested_by, requester_type BUYER\|STORE\|PLATFORM\|SYSTEM, reason_code, status, processed_by), orders.cancelled_at, cancel_reason, order_status_history | รองรับ; ตรวจ transition ที่แอป | DB+App |  |  |
| R16 | §5,§9 | คืนเงิน | ORDER_REFUND ตามกระบวนการ ตรวจวงเงินคงเหลือ และ Idempotency (ห้ามคืนซ้ำ) | Supported | Supported | refunds (order_id, payment_id, amount, status, idempotency_key UNIQUE, initiated_by, provider_refund_id) | รองรับ; คืนผ่าน PG ของร้าน (สอดคล้องกฎไม่ถือเงิน); วงเงินคงเหลือคำนวณที่แอป (SUM refunds) — แนะนำ CHECK amount > 0 | DB+App | P16 | ck_refunds_amount (amount > 0); วงเงินคงเหลือตรวจที่แอป |
| R17 | §6,§9 | จัดส่ง | SHIPMENT_PACK: Ready to pack → Packing → Packed; SHIPMENT_DISPATCH: Packed → Dispatched | Supported | Supported | shipments.status (READY_TO_PACK\|PACKING\|PACKED\|DISPATCHED\|…), packed_at, packed_by, dispatched_at, dispatched_by | รองรับครบ; transition ตรวจที่แอป | DB+App |  |  |
| R18 | §6,§9 | จัดส่ง | SHIPMENT_UPDATE แก้ Carrier/Tracking ก่อน Delivered เท่านั้น ไม่แก้ผู้รับ | Supported | Supported | shipments.carrier_code, tracking_number, delivered_at; ผู้รับอยู่ใน order_addresses (snapshot) | รองรับ; ประวัติการแก้ใช้ audit_logs | DB+App |  |  |
| R19 | §6,§7 | รายงาน/ส่งออก | REPORT_READ / REPORT_EXPORT / ORDER_EXPORT (ไม่รวม PII) ไฟล์ผูกกับ Permission ของงานต้นทางและร้าน | Partial | Supported | background_jobs.job_type 'report.export.v1'; media_files.purpose 'REPORT', visibility 'PRIVATE', shop_id | ไม่มีการผูก ไฟล์ ↔ job ↔ ผู้สั่ง ↔ permission ที่ต้องใช้ดาวน์โหลด; ไม่มี export type แยก ORDER_EXPORT/REPORT_EXPORT | DB+App | P11, P12 | background_jobs.result_file_id + media_files.required_permission / expires_at / source_job_id |
| R20 | §6,§13 | Audit | AUDIT_READ (ร้าน) และ PLATFORM_AUDIT_READ (แพลตฟอร์ม) | Supported | Supported | audit_logs.shop_id (NULL = ระดับแพลตฟอร์ม), ix_audit_logs_shop_id | รองรับ; แนะนำเพิ่ม index (shop_id, occurred_at) และ auth_context (P13) | DB+App | P13 | ix_audit_logs_shop_id_occurred_at |
| R21 | §7 | การเปิดเผยข้อมูล | ORDER_READ ไม่ให้ PII อัตโนมัติ; Warehouse เห็น SKU/จำนวน ไม่เห็นยอดขาย/ส่วนลด/บันทึกภายใน; Viewer ไม่มีเบอร์/อีเมล/ที่อยู่ | App-layer | App-layer | ข้อมูลอยู่ใน orders, order_items, order_addresses, order_financial_snapshots | เป็น projection ใน DTO; ต้องทำงานร่วมกับเกต payment-first (items_revealed_at / merchant_confirmed_at) — Warehouse/Sales เห็นรายการได้หลัง merchant confirm เท่านั้น | App |  |  |
| R22 | §7 | การเปิดเผยข้อมูล | SHIPMENT_ADDRESS_READ: Warehouse อ่านที่อยู่เต็มเฉพาะงาน Ready to pack/Packing/Packed/Dispatched; หลัง Delivered/Cancelled ให้ปกปิด | App-layer | App-layer | order_addresses + shipments.status | ข้อมูลพอสำหรับตัดสินใจ; บังคับที่แอป | App |  |  |
| R23 | §7 | ไฟล์ | FILE_UPLOAD ไม่ใช่สิทธิ์ทั่วไป; Upload URL รูปสินค้าตรวจ PRODUCT_CREATE/UPDATE; ไฟล์รายงาน/Import report ผูกกับ Permission ต้นทางและร้าน | Partial | Supported | media_files.shop_id, uploader_id, purpose, visibility; product_images; product_imports.error_report_file_id | ไม่มี source_job_id / required_permission บน media_files สำหรับไฟล์ที่ระบบสร้าง (report/export) | DB+App | P12 | media_files.required_permission + expires_at (ไฟล์ export/report); signed URL ที่แอป |
| R24 | AUTHZ 01 | Enforcement | ปฏิเสธเป็นค่าเริ่มต้น; บัญชีถูกระงับทำรายการที่ต้อง Login ไม่ได้; ไม่เชื่อ role/permissions จาก Browser | App-layer | App-layer | users.status (ACTIVE\|SUSPENDED) | Spring Security (deny-by-default + ตรวจ users.status ทุก request หรือผ่าน permission_version) | App |  |  |
| R25 | AUTHZ 02 | Enforcement | ลำดับตรวจ: บริบท+ตัวตน → Scope+Permission → โหลด Resource ใต้เจ้าของ/ร้าน → Field+สถานะ → Transaction | App-layer | App-layer | shop_id อยู่ในทุกตารางธุรกิจ (tenant key) | shop_id ครบทำให้ query แบบ WHERE shop_id = :ctx ได้; แนะนำพิจารณา PostgreSQL RLS เป็นชั้นป้องกันเสริม (ไม่บังคับ) | App |  |  |
| R26 | AUTHZ 03 | ทีม | Owner แต่งตั้ง Admin/Manager/Sales/Warehouse/Viewer; Store Admin จัดการได้เฉพาะ Manager/Sales/Warehouse/Viewer; ห้ามเปลี่ยนบทบาทตนเอง | App-layer | App-layer | shop_invitations.role CHECK ไม่รวม STORE_OWNER; shop_members.role | DB กันเชิญเป็น Owner ได้แล้ว; target-role policy ตาม actor และกฎห้ามแก้ตนเองต้องอยู่ที่แอป | App |  |  |
| R27 | AUTHZ 04 | เจ้าของร้าน | หนึ่งร้านมี Store Owner หนึ่งคน | Partial | Supported | shops.owner_user_id (NOT NULL, ค่าเดียว); shop_members.role | ไม่มี partial unique index บน shop_members สำหรับ STORE_OWNER — อาจมี Owner หลายแถว หรือไม่ตรงกับ shops.owner_user_id | DB | P05 | uq_shop_members_shop_id_owner + constraint triggers trg_shop_members_owner_guard / trg_shops_owner_guard (deferred): Owner ACTIVE 1 คน = shops.owner_user_id |
| R28 | AUTHZ 04 | เจ้าของร้าน | โอนเจ้าของ: ยืนยันตัวตนซ้ำ, ผู้รับเป็น Active member และยอมรับ, เปลี่ยนทั้งคู่ใน Transaction เดียว, เจ้าของเดิมเป็น Store Admin, มี Audit | Not supported | Supported | มีแค่ shops.owner_user_id + shop_members.role + audit_logs | ไม่มีตารางคำขอโอน/การยอมรับของผู้รับ/วันหมดอายุ/หลักฐาน re-auth จึงทำ flow สองฝ่าย (เสนอ → ยอมรับ) ไม่ได้ | DB+App | P02, P15 | ตารางใหม่ shop_ownership_transfers (PENDING 1 ใบต่อร้าน, step-up timestamps, completed_at) + otp_verifications STEP_UP |
| R29 | AUTHZ 04,§12 | เจ้าของร้าน | ห้ามลบหรือระงับ Store Owner ก่อนโอนสำเร็จ; ลบ Store Owner สุดท้ายไม่ได้ | Partial | Supported | shop_members.status, removed_at | ไม่มี guard ระดับ DB; แนะนำ trigger/constraint trigger เสริม (แอปต้องตรวจเป็นหลัก) | DB+App | P05 | DB ห้าม Owner ถูก deactivate/ลบโดยไม่มี Owner ใหม่ (constraint trigger); shop_members.removed_by / remove_reason_code |
| R30 | AUTHZ 03,§11 | ทีม | คำเชิญผูกกับร้านและบทบาท มีวันหมดอายุ; ตรวจว่าเชิญยังใช้ได้และผู้เชิญยังมีสิทธิ์ก่อนสร้างสมาชิก; Target เป็น Owner ต้องปฏิเสธ | Supported | Supported | shop_invitations (shop_id, email, role, token_hash UNIQUE, status PENDING\|ACCEPTED\|EXPIRED\|REVOKED, invited_by, expires_at, accepted_at, accepted_user_id) | รองรับ; ขาด unique คำเชิญค้างซ้ำ (shop_id, email) และ revoked_by/at (P06) | DB+App | P06 | uq_shop_invitations_shop_id_email_pending (lower(email)) + revoked_at / revoked_by |
| R31 | AUTHZ 05 | Enforcement | Field allowlist ต่อ Action ห้าม Mass assignment ไม่รับ shopId/role/ยอดชำระจาก payload | App-layer | App-layer | — | DTO ต่อ action ใน Spring | App |  |  |
| R32 | AUTHZ 06 | Cache | ถอนสมาชิก/เปลี่ยนบทบาท เพิ่ม permissionVersion และล้าง Authorization cache | Supported | Supported | shop_members.permission_version, users.permission_version | รองรับ | DB+App |  |  |
| R33 | AUTHZ 06,§11 | งานค้าง | Worker ของ import/export ตรวจสิทธิ์ซ้ำก่อนทำและก่อนเผยแพร่ไฟล์; ถ้าถูกถอนจบด้วย AUTHORIZATION_REVOKED; ดาวน์โหลดผลเดิมตรวจสิทธิ์ปัจจุบัน | Partial | Supported | background_jobs (status, error_code, request_id, shop_id) | error_code เป็นข้อความอิสระ (เก็บ AUTHORIZATION_REVOKED ได้) แต่ไม่มี requested_by_user_id / required_permission / auth_context ให้ Worker รู้ว่าต้องตรวจใครด้วยสิทธิ์อะไร | DB+App | P11 | background_jobs.auth_context / required_permission / policy_version; error_code AUTHORIZATION_REVOKED |
| R34 | AUTHZ 07 | Service identity | งานระบบ (Email, คืนสต็อก) ใช้ Service identity ที่มีขอบเขตเฉพาะ ไม่ใช้บทบาทผู้ใช้เดิม | Partial | Supported | audit_logs.actor_type (USER\|SERVICE\|SYSTEM\|OMS), inventory_ledger.actor_type, order_status_history.changed_by_type | ไม่มีชื่อ/รหัส service ที่ทำรายการ และ actor_type ไม่มี CHECK constraint | DB+App | P13 | audit_logs.service_name + ck_audit_logs_service_name; ck_audit_logs_actor_type |
| R35 | AUTHZ 08 | Audit | บันทึก actorId, shopId, permission, targetId, outcome, reasonCode, requestId, policyVersion สำหรับทีม สต็อก คืนเงิน โอนเจ้าของ ส่งออก; ไม่บันทึก Token/PII | Supported | Supported | audit_logs.actor_id, shop_id, permission, resource_type+resource_id, outcome, reason_code, request_id, policy_version, before_data, after_data | ครบทุก field; outcome/actor_type ยังไม่มี CHECK; before_data/after_data ต้อง redact PII ที่แอป | DB+App | P13 | ck_audit_logs_outcome + ix_audit_logs_actor_id_occurred_at + ix_audit_logs_resource_type_resource_id |
| R36 | §9 | Contract | GET /api/v1/shops/{shopId}/me/permissions คืน role, permissions, policyVersion, permissionVersion | Supported | Supported | shop_members.role, shop_members.permission_version (+ catalog ในโค้ด) | รองรับ | App |  |  |
| R37 | §9 | Contract | allowedActions ใน Order response คำนวณจากสิทธิ์ สถานะ และ Field scope; UI ตรวจ Permission ไม่ใช่ role | App-layer | App-layer | — | Backend + Frontend | App |  |  |
| R38 | §10 | Contract | 401 / 403 FORBIDDEN / 404 RESOURCE_NOT_FOUND / 409 INVALID_STATE ห้ามเปิดเผยร้านอื่น | App-layer | App-layer | — | Error handling ของ API | App |  |  |
| R39 | §13 | แพลตฟอร์ม | PLATFORM_SHOP_SUSPEND / RESTORE พร้อมเหตุผลและ Audit | Partial | Supported | shops.status (ACTIVE\|SUSPENDED\|CLOSED), suspended_at, suspend_reason | ไม่มี suspended_by, suspend_reason_code, restored_at/restored_by (พึ่ง audit_logs ได้แต่ query ยาก) | DB+App | P09 | shops.suspended_by / suspend_reason_code / restored_at / restored_by |
| R40 | §4,§13 | ร้าน | SHOP_CLOSE ปิดร้านถาวรหลังตรวจออเดอร์และการคืนเงินค้าง | Partial | Supported | shops.status 'CLOSED', deleted_at | ไม่มี closed_at, closed_by, close_reason; การตรวจงานค้างทำที่แอป | DB+App | P09 | shops.closed_at / closed_by / close_reason |
| R41 | §13 | ร้าน | ร้าน Suspended หยุดเผยแพร่และ Checkout ใหม่ แต่ทีมร้านจบ Fulfillment/Refund ได้; แก้สินค้า/สร้างออเดอร์ใหม่ไม่ได้ | Supported | Supported | shops.status; checkouts.shop_id; orders.shop_id | ข้อมูลพอ; บังคับที่แอป (ตรวจ shops.status ก่อน checkout/เขียนสินค้า) | App |  |  |
| R42 | §13 | แพลตฟอร์ม | PLATFORM_APPLICATION_READ / REVIEW (ขอข้อมูลเพิ่ม อนุมัติ ปฏิเสธ) | Supported | Supported | merchant_applications.status, reviewer_id, review_note, reviewed_at; merchant_documents | รองรับ | DB+App |  |  |
| R43 | §13 | แพลตฟอร์ม | PLATFORM_ACCOUNT_SUSPEND / RESTORE ระงับบัญชีและเพิกถอน Session พร้อมเหตุผล | Partial | Supported | users.status, users.permission_version, refresh_tokens.revoked_at | เพิกถอน session ได้ แต่ users ไม่มี suspended_at / suspended_by / suspend_reason_code | DB+App | P08 | users.suspended_at / suspended_by / suspend_reason_code (+ permission_version, refresh_tokens.revoked_at) |
| R44 | §13 | แพลตฟอร์ม | บัญชีที่มี Platform role ไม่ถูกระงับผ่าน Account API ทั่วไป | App-layer | App-layer | platform_roles (ใช้ตรวจ) | แอปตรวจ platform_roles ก่อนระงับ | App |  |  |
| R45 | §13 | แพลตฟอร์ม | PLATFORM_SETTING_UPDATE แก้เฉพาะค่าที่อยู่ใน Allowlist ไม่รวม Secret และการให้ Role | Partial | Supported | platform_settings (setting_key UNIQUE, setting_value JSONB) — comment 'allowlist keys only', 'ห้ามเก็บ secret' | Allowlist อยู่แค่ใน comment ไม่มี flag is_admin_editable / value_schema ให้ตรวจ | DB+App | P10 | platform_settings.is_admin_editable (allowlist) + value_schema + description |
| R46 | §13 | แพลตฟอร์ม | Platform Admin แต่งตั้งผ่านกระบวนการภายในที่มี Audit ห้ามสมัครเอง ไม่มี Public API | Partial | Supported | platform_roles (user_id UNIQUE, role CHECK 'PLATFORM_ADMIN', granted_by, granted_at, revoked_at) | UNIQUE(user_id) ทำให้แต่งตั้งซ้ำหลังเพิกถอนไม่ได้ (ต้องลบประวัติ); ไม่มี revoked_by / grant_reason | DB+App | P07 | platform_roles: partial unique active + grant_reason / revoked_by / revoke_reason (ประวัติ grant/revoke) |
| R47 | §13,AUTHZ 04 | Step-up auth | ยืนยันตัวตนเพิ่มก่อนระงับบัญชี เปลี่ยนค่าระบบ และโอนเจ้าของ | Partial | Supported | otp_verifications (purpose SIGNUP\|LOGIN\|PHONE_VERIFY\|EMAIL_VERIFY\|PASSWORD_RESET) | ไม่มี purpose สำหรับ step-up → ไม่มีหลักฐานใน DB; ทางเลือกคือใช้ auth_time ใน token (App) | DB+App | P15 | otp_verifications.purpose STEP_UP + step_up_action |
| R48 | §13 | แพลตฟอร์ม | Support access / Impersonation ยังไม่อยู่ใน Baseline | Supported | Supported | ไม่มีตาราง impersonation | ถูกต้องตาม Baseline (ไม่มี = ไม่เปิดช่อง) | — |  |  |
| R49 | §14 | Onboarding | Applicant คือบัญชีที่ Login; applicantUserId มาจาก Session | Supported | Supported | merchant_applications.user_id FK→users.id | รองรับ | DB+App |  |  |
| R50 | §14 | Onboarding | สถานะ Draft → Submitted → Approved/Rejected; Needs information; Withdrawn; Approved/Rejected/Withdrawn เป็นสถานะสิ้นสุด | Supported | Supported | ck_merchant_applications_status (DRAFT\|SUBMITTED\|UNDER_REVIEW\|NEEDS_INFORMATION\|APPROVED\|REJECTED\|WITHDRAWN), submitted_at, reviewed_at | รองรับ; schema มี UNDER_REVIEW เพิ่มจากเอกสาร (ต้องยืนยันว่าถอนได้จาก UNDER_REVIEW หรือไม่); ไม่มี withdrawn_at | DB+App | P04 | merchant_applications.withdrawn_at (WITHDRAWN) |
| R51 | §14 | Onboarding | Approve สร้าง Shop + Active membership STORE_OWNER + ร้าน Active ใน Transaction เดียว ใช้ applicationId เป็น Unique key | Supported | Supported | shops.application_id UNIQUE, merchant_applications.shop_id UNIQUE, shops.owner_user_id, shop_members | รองรับ idempotent ระดับ DB | DB+App |  |  |
| R52 | §14 | Onboarding | ผู้ตรวจไม่อนุมัติใบสมัครของตนเอง | Not supported | Supported | merchant_applications.reviewer_id, user_id | ไม่มี CHECK reviewer_id <> user_id | DB+App | P04 | ck_merchant_applications_reviewer_not_applicant (reviewer_id <> user_id); reviews ตรวจที่แอปด้วย |
| R53 | §14 | Onboarding | เก็บผู้ตรวจ เหตุผล เวลา และผลการตัดสินใจ (หลายรอบ Needs information) | Partial | Supported | merchant_applications.reviewer_id, review_note, reviewed_at (ค่าล่าสุดเท่านั้น); audit_logs | รอบก่อนหน้าถูกเขียนทับ ไม่มีประวัติการพิจารณาแยกตาราง | DB | P03 | ตารางใหม่ merchant_application_reviews (ทุกรอบ: action, from/to status, reason, decided_at) |
| R54 | §14 | Onboarding | ผู้สมัครแก้ status, reviewerId, shopId, assignedRole เองไม่ได้; แก้ได้เฉพาะ Draft/Needs information | App-layer | App-layer | merchant_applications.status | Field allowlist ที่แอป | App |  |  |
| R55 | §14 | Onboarding | เอกสารแนบเป็น Private ตรวจสิทธิ์ Upload/Download | Supported | Supported | merchant_documents.storage_path (ใต้ STORAGE_ROOT), sha256 | รองรับ; ตรวจสิทธิ์ที่แอป | DB+App |  |  |
| R56 | §14 | Onboarding | หนึ่งบัญชีมีใบสมัครที่ยังดำเนินการได้ 1 ใบ; ผู้ถูกปฏิเสธสมัครใหม่ได้ | Partial | Supported | V3 drop merchant_applications_user_id_key (สมัครใหม่ได้); ix_merchant_applications_user_id | ไม่มี partial unique index กันใบสมัครค้างพร้อมกันหลายใบ (comment ระบุไว้แต่ไม่ได้สร้าง) | DB | P04 | uq_merchant_applications_user_id_open (1 ใบที่ยังเปิดต่อ user; ใบที่ปิดแล้วยื่นใหม่ได้) |
| R57 | §14 | Onboarding | ถ้าเลือกเปิดร้านอัตโนมัติ ต้องกำหนดผ่าน Configuration ชัดเจน | App-layer | App-layer | platform_settings (เก็บ key เช่น onboarding.auto_approve) | ใช้ platform_settings ได้ | App |  |  |
| R58 | §15 | Storefront | Guest อ่าน CATALOG เฉพาะร้าน Active และสินค้าที่เผยแพร่; Public response ห้ามมี ledger/ต้นทุน/Draft/PII | Supported | Supported | shops.status, shops.published_at; products.status 'PUBLISHED', published_at; product_skus.cost_price (ต้องไม่ส่ง); media_files.visibility | รองรับ | DB+App |  |  |
| R59 | §15 | Storefront | BUYER_PROFILE_* / BUYER_ADDRESS_* เฉพาะของตนเอง ไม่รวม role และสถานะบัญชี | Supported | Supported | users; buyer_addresses.buyer_user_id, deleted_at | รองรับ | DB+App |  |  |
| R60 | §15 | Storefront | CART_* ตะกร้าตนเอง แยกตามร้าน | Supported | Supported | carts uq_carts_buyer_user_id_shop_id (partial WHERE status='ACTIVE'); cart_items | รองรับ | DB |  |  |
| R61 | §15 | Storefront | ไม่รองรับ Guest checkout; Checkout ต่อร้าน ผูก buyerUserId จาก Session; Idempotency key | Supported | Supported | checkouts.buyer_user_id NOT NULL, shop_id, idempotency_key UNIQUE; orders.idempotency_key UNIQUE | รองรับ | DB+App |  |  |
| R62 | §15 | Payment | BUYER_PAYMENT_CREATE เริ่มชำระเท่านั้น; Webhook ตรวจลายเซ็น ยอด สกุลเงิน Order reference และ Dedup | Supported | Supported | payments (status INTENT…, idempotency_key UNIQUE, amount, currency); payment_webhook_events (UNIQUE(provider,event_id), signature_valid) | รองรับ; สอดคล้องกฎจ่ายเข้า PG ร้านโดยตรง | DB+App |  |  |
| R63 | §15 | Payment | วิธีตรวจหลักฐานโอนเงินต้องกำหนดแยกก่อนเปิดใช้ | Partial | Partial | payment_proofs (status PENDING_REVIEW\|VERIFIED\|REJECTED, verified_by, verified_at, reject_reason) | schema มีตารางแล้ว แต่เอกสารยังไม่มี Permission ว่าใครตรวจสลิปได้ (ช่องว่างฝั่งเอกสาร) | Doc | — | payment_proofs มีครบ แต่เอกสารยังไม่มี permission PAYMENT_PROOF_REVIEW (doc gap) |
| R64 | §15 | Storefront | BUYER_ORDER_READ / CANCEL / SHIPMENT_READ เฉพาะออเดอร์ของตนเอง; ยกเลิกได้ก่อนชำระและก่อนแพ็ก | Supported | Supported | orders.buyer_user_id; order_cancellations.requester_type 'BUYER'; shipments.order_id | รองรับ | DB+App |  |  |
| R65 | §15 | Storefront | ที่อยู่จัดส่งเป็น Snapshot ของออเดอร์ | Supported | Supported | order_addresses (UNIQUE(order_id,address_type)) | รองรับ | DB |  |  |
| R66 | §15 | Storefront | ผู้ซื้อไม่สั่ง ORDER_REFUND โดยตรง หลังชำระเป็นขั้นตอน Support | Supported | Supported | return_requests (buyer_user_id), refunds.initiated_by | รองรับ; บังคับที่แอป | App |  |  |
| R67 | §16 | Contract | /me/permissions, /platform/me/permissions, /shops/{id}/me/permissions แยกบริบท; ล้าง State/Cache เมื่อเปลี่ยนร้าน/Logout/ถอนสิทธิ์ | App-layer | App-layer | platform_roles, shop_members, users.permission_version | ข้อมูลพอ | App |  |  |

## 6. ช่องว่างฝั่งเอกสาร (schema มีฟีเจอร์ แต่เอกสารยังไม่กำหนด permission)

ควรส่งกลับให้ Product Owner เพิ่มในเอกสารเวอร์ชันถัดไป — มิฉะนั้นตามกฎ AUTHZ 01 (deny by default) ฟีเจอร์เหล่านี้จะใช้ไม่ได้กับทุกบทบาท

| Permission ที่ควรเพิ่ม (เสนอ) | ตาราง/คอลัมน์ใน schema | หมายเหตุ |
| --- | --- | --- |
| ORDER_CONFIRM (ร้านยืนยันออเดอร์ที่ชำระแล้ว / เปิดเผยรายการ) | merchant_order_confirmations, orders.merchant_confirm_status, items_revealed_at, order_item_reveal_logs | หัวใจของ flow payment-first แต่เอกสารไม่มี permission — ต้องกำหนดว่า role ใดยืนยัน/ปฏิเสธได้ (เสนอ Owner/Admin/Manager) |
| PAYMENT_PROOF_REVIEW (ตรวจสลิปโอนเงิน) | payment_proofs.status, verified_by | เอกสาร §15 บอกให้กำหนดแยก — ยังไม่มีรหัสสิทธิ์ |
| SHOP_PAYMENT_GATEWAY_MANAGE | shop_payment_gateways.config_encrypted, webhook_secret_encrypted | ข้อมูลลับของร้าน — เสนอ Owner เท่านั้น (หรือ Owner+Admin) และต้อง step-up |
| OMS_CONNECTION_MANAGE | oms_connections.credentials_encrypted, webhook_secret_encrypted | credential ภายนอก — ไม่มีสิทธิ์กำหนด |
| PROMOTION_* / COUPON_* | promotions, coupons, promotion_usages | ไม่มีในเอกสาร |
| RETURN_REQUEST_REVIEW (ร้าน) / BUYER_RETURN_CREATE | return_requests, return_items | มีแค่ ORDER_REFUND; การอนุมัติคืนสินค้าไม่มีรหัส |
| SHIPPING_METHOD_MANAGE | shipping_methods | ไม่มีในเอกสาร |
| SHOP_CATEGORY_MANAGE / PLATFORM_HUB_CATEGORY_MANAGE | shop_categories, hub_categories | ไม่มีในเอกสาร |
| REVIEW_REPLY / REVIEW_MODERATE / BUYER_REVIEW_CREATE | product_reviews | ไม่มีในเอกสาร |
| PLATFORM_BLOG_MANAGE | blog_posts (ไม่มี shop_id = เนื้อหาแพลตฟอร์ม), blog_categories | ไม่มีในเอกสาร |
| BUYER_ORDER_CONFIRM_RECEIPT | orders.buyer_received_at, completion_source 'BUYER_CONFIRM' | ไม่มีในเอกสาร §15 |
| USER_INVITATION_REVOKE | shop_invitations.status 'REVOKED' | อาจรวมใน USER_INVITE — ควรระบุ |

## 7. ข้อเสนอการเปลี่ยนแปลง schema

ทุกข้อใช้ convention เดิม: UUID PK `gen_random_uuid()`, VARCHAR + CHECK (`ck_<table>_<col>`), FK ชื่อ `fk_<table>_<col>` + index FK, partial unique `uq_…`, audit 4 คอลัมน์ท้ายตาราง + `trg_<table>_modified_date_time`, snake_case

| ID | Priority | ประเภท | Object | การเปลี่ยนแปลง | คอลัมน์ / constraint หลัก | แก้ Req | DB vs App | ผลกระทบ migration | สถานะ |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P01 | High | NEW TABLE + ALTER | shop_customers (+ orders.shop_customer_id) | ลูกค้าระดับร้าน รองรับ CUSTOMER_READ / PII_READ / CREATE / UPDATE แยกจากบัญชีกลาง users | id UUID PK gen_random_uuid(); shop_id FK→shops (CASCADE) NOT NULL; user_id FK→users (SET NULL) NULL; customer_code; display_name NOT NULL; phone; email; default_address JSONB; internal_note; source CHECK (STOREFRONT\|MANUAL\|OMS\|IMPORT); status CHECK (ACTIVE\|ARCHIVED); deleted_at; audit 4 คอลัมน์; UNIQUE(shop_id,customer_code); partial UNIQUE(shop_id,user_id) WHERE user_id IS NOT NULL AND deleted_at IS NULL; ix(shop_id,phone); orders.shop_customer_id FK SET NULL | R11, R12 | DB: ตาราง/FK/unique · App: masking PII ตาม CUSTOMER_PII_READ, Viewer/Warehouse ไม่เห็น internal_note | ตารางใหม่ + คอลัมน์ใหม่ใน orders (nullable) — ไม่กระทบ V1/V2; ต้องตัดสินใจว่าจะสร้าง shop_customers อัตโนมัติเมื่อ buyer สั่งซื้อ (source=STOREFRONT) หรือไม่ | Applied — merged into v3 (2026-10-04) |
| P02 | High | NEW TABLE | shop_ownership_transfers | คำขอโอนเจ้าของ 2 ฝ่าย (เสนอ → ผู้รับยอมรับ) พร้อมหลักฐาน re-auth และวันหมดอายุ | id UUID PK; shop_id FK→shops; from_user_id, to_user_id FK→users (RESTRICT); to_member_id FK→shop_members; status CHECK (PENDING\|COMPLETED\|DECLINED\|CANCELLED\|EXPIRED\|FAILED); initiator_reauth_at NOT NULL; recipient_reauth_at; accepted_at; completed_at; cancelled_at; expires_at NOT NULL; reason_code; request_id; audit; CHECK from_user_id<>to_user_id; partial UNIQUE(shop_id) WHERE status='PENDING' | R28 | DB: สถานะ/unique · App: ตรวจผู้รับ Active member, re-auth, ทำ demote→promote→shops.owner_user_id ใน transaction เดียว + audit_logs OWNERSHIP_TRANSFER | ตารางใหม่ | Applied — merged into v3 (2026-10-04) |
| P03 | Medium | NEW TABLE | merchant_application_reviews | ประวัติการพิจารณาใบสมัครทุกรอบ (ผู้ตรวจ เหตุผล เวลา ผล) | id UUID PK; application_id FK→merchant_applications (CASCADE); reviewer_id FK→users (RESTRICT); action CHECK (START_REVIEW\|REQUEST_INFO\|APPROVE\|REJECT); from_status; to_status CHECK; reason_code; note; decided_at; request_id; audit; ix(application_id,decided_at) | R53 | DB | ตารางใหม่; merchant_applications.reviewer_id/review_note คงไว้เป็นค่าล่าสุด (denormalized) | Applied — merged into v3 (2026-10-04) |
| P04 | High | ALTER | merchant_applications | 1 ใบที่ยังดำเนินการต่อบัญชี, ห้ามตรวจของตนเอง, เวลาถอน | partial UNIQUE uq_merchant_applications_user_id_open (user_id) WHERE status IN (DRAFT,SUBMITTED,UNDER_REVIEW,NEEDS_INFORMATION); CHECK ck_merchant_applications_reviewer_not_applicant (reviewer_id IS NULL OR reviewer_id<>user_id); ADD withdrawn_at TIMESTAMPTZ | R50, R52, R56 | DB (บังคับได้จริง) + App (transition) | ตาราง V2 — ต้องตรวจข้อมูลเดิมว่าไม่มีใบค้างซ้ำ/ตรวจตนเองก่อนสร้าง constraint; JPA MerchantApplication เพิ่ม withdrawnAt | Applied — merged into v3 (2026-10-04) |
| P05 | High | ALTER + TRIGGER | shop_members (+ shops) | Owner หนึ่งคนต่อร้าน และต้องตรงกับ shops.owner_user_id; ห้ามลบ/ระงับ Owner ก่อนโอน | partial UNIQUE uq_shop_members_shop_id_owner (shop_id) WHERE role='STORE_OWNER' AND status='ACTIVE'; CONSTRAINT TRIGGER DEFERRABLE INITIALLY DEFERRED trg_shop_members_owner_guard / trg_shops_owner_guard; ADD removed_by FK→users, remove_reason_code | R27, R29 | DB (กันข้อมูลผิด) + App (ข้อความ error / ลำดับ demote→promote) | ตาราง v3 ใหม่ — ไม่กระทบ V1/V2; ลำดับโอนต้อง demote เจ้าของเดิมก่อน promote (partial unique index deferred ไม่ได้) — ทดสอบแล้ว | Applied — merged into v3 (2026-10-04) |
| P06 | Medium | ALTER | shop_invitations | กันคำเชิญค้างซ้ำ และเก็บผู้เพิกถอน | partial UNIQUE (shop_id, lower(email)) WHERE status='PENDING'; ADD revoked_at, revoked_by FK→users | R30 | DB + App (target-role policy ตาม actor) | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P07 | Medium | ALTER | platform_roles | เก็บประวัติแต่งตั้ง/เพิกถอน แต่งตั้งซ้ำได้ | DROP uq_platform_roles_user_id → partial UNIQUE (user_id) WHERE revoked_at IS NULL; ADD grant_reason, revoked_by FK→users, revoke_reason; Relationships 1:1 → N:1 | R46 | DB + App (ไม่มี Public API แต่งตั้ง, step-up, audit) | ตาราง v3; ปรับ Relationships ในชีต | Applied — merged into v3 (2026-10-04) |
| P08 | High | ALTER | users | ระงับบัญชีพร้อมเหตุผล/ผู้ระงับ; เลิกใช้ users.role ในการ authorize | ADD suspended_at, suspended_by FK→users, suspend_reason_code; backfill platform_roles จาก users.role='ADMIN'; COMMENT users.role DEPRECATED → ภายหลัง DROP DEFAULT/NOT NULL หรือ DROP COLUMN | R01, R43 | DB + App (Spring Security ใช้ platform_roles/shop_members + permission_version; revoke refresh_tokens) | ตาราง V1 — JPA UserAccount: เพิ่มฟิลด์; ถ้า DROP role ต้องแก้ entity/registration ที่ set role='CUSTOMER'/'MERCHANT' ก่อน (แนะนำ 2 ขั้น: V4 deprecate, V5 drop) | Applied — merged into v3 (2026-10-04) |
| P09 | Medium | ALTER | shops | ระงับ/คืนสถานะ/ปิดถาวร มีผู้ทำและเหตุผล | ADD suspended_by, suspend_reason_code, restored_at, restored_by, closed_at, closed_by, close_reason (FK→users SET NULL) | R39, R40 | DB + App (ตรวจออเดอร์/คืนเงินค้างก่อน CLOSED) | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P10 | Low | ALTER | platform_settings | Allowlist ที่ตรวจได้จริง | ADD is_admin_editable BOOLEAN NOT NULL DEFAULT false, value_schema JSONB, description | R45 | DB + App (ปฏิเสธ key ที่ is_admin_editable=false, validate กับ value_schema) | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P11 | High | ALTER | background_jobs | ผู้สั่งงานและสิทธิ์ที่ Worker ต้องตรวจซ้ำ (import/export) | ADD requested_by_user_id FK→users, auth_context CHECK (ACCOUNT\|SHOP\|PLATFORM\|SERVICE), required_permission, permission_version_at_request, policy_version, result_file_id FK→media_files; error_code ใช้ค่า AUTHORIZATION_REVOKED | R09, R19, R33 | DB (ข้อมูลที่ต้องใช้) + App (Worker ตรวจสิทธิ์ปัจจุบันก่อนทำ/ก่อนเผยแพร่) | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P12 | Medium | ALTER | media_files | ไฟล์ report/export/import report ผูกกับ job และ permission | ADD source_job_id FK→background_jobs, required_permission, expires_at | R19, R23 | DB + App (ดาวน์โหลดตรวจสิทธิ์ปัจจุบัน, signed URL อายุสั้น) | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P13 | Medium | ALTER | audit_logs | บังคับค่า enum, บริบท authorization, service identity, index สำหรับ AUDIT_READ | CHECK actor_type (USER\|SERVICE\|SYSTEM\|OMS), outcome (SUCCESS\|DENIED\|FAILURE); ADD auth_context CHECK (PUBLIC\|ACCOUNT\|SHOP\|PLATFORM\|SERVICE), service_name (+CHECK SERVICE ต้องมี service_name); ix(shop_id,occurred_at), ix(actor_id,occurred_at), ix(resource_type,resource_id) | R20, R34, R35 | DB + App (redact PII/token ใน before_data/after_data) | ตาราง v3 (append-only, BIGSERIAL) | Applied — merged into v3 (2026-10-04) |
| P14 | High | ALTER + DECISION | orders | optimistic lock และ mapping สถานะ Draft/Confirmed ของเอกสาร | ADD row_version INTEGER NOT NULL DEFAULT 0 (JPA @Version); ตัดสินใจ: 'Draft/Confirmed ที่ยังไม่ชำระ' = status='PENDING_PAYMENT' AND payment_status IN ('UNPAID','FAILED') AND ไม่มี shipment ≥ PACKING — หรือเพิ่ม DRAFT สำหรับ channel MANUAL | R12, R13 | DB (row_version) + App (allowedActions, 409) | ตาราง v3; ถ้าเพิ่ม DRAFT ต้องแก้ ck_orders_status และ Enums | Applied — merged into v3 (2026-10-04) |
| P15 | Low | ALTER | otp_verifications | หลักฐาน step-up ก่อนระงับบัญชี/เปลี่ยนค่าระบบ/โอนเจ้าของ | purpose เพิ่ม STEP_UP; ADD step_up_action VARCHAR(80) | R28, R47 | App เป็นหลัก (auth_time ใน token) — DB เป็นหลักฐานเสริม | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P16 | Low | ALTER | inventory_ledger, refunds | CHECK เล็กน้อยให้ตรงกฎ | ck_inventory_ledger_adjust_reason CHECK (entry_type<>'ADJUST' OR reason IS NOT NULL); ck_refunds_amount CHECK (amount>0) | R10, R16 | DB | ตาราง v3 | Applied — merged into v3 (2026-10-04) |
| P17 | Optional | NEW TABLES (reference) | auth_permissions, auth_roles, auth_role_permissions | สำเนา Permission catalog/Role matrix ใน DB สำหรับรายงาน/หน้าจอ admin/ตรวจ CI — ไม่ใช่ source of truth (เอกสาร §10 ให้เก็บใน Repository) | auth_permissions: id UUID PK, code VARCHAR(80) UNIQUE (UPPER_SNAKE_CASE CHECK), module, scope CHECK (PLATFORM\|OWN_ACCOUNT\|OWN_APPLICATION\|OWN_RESOURCE\|STORE\|PUBLIC), description_th, policy_version; auth_roles: id, code UNIQUE, context CHECK (PUBLIC\|ACCOUNT\|APPLICANT\|STORE\|PLATFORM); auth_role_permissions: id, role_id FK, permission_id FK, UNIQUE(role_id,permission_id); seed สร้างจาก catalog ใน CI | R05 | App เป็น source of truth; DB เป็น read-model | ตารางใหม่ (ไม่บังคับ); ถ้าทำ ต้องให้ seed ถูก regenerate ทุกครั้งที่ policyVersion เปลี่ยน | Applied — merged into v3 (2026-10-04) |

### 7.1 อะไรควรอยู่ใน DB vs บังคับที่แอป (Spring Security)

| อยู่ใน DB (บังคับได้แม้ bug ในแอป) | บังคับที่แอป (Spring Security / service / DTO) |
| --- | --- |
| Owner เดียวต่อร้าน + ตรงกับ shops.owner_user_id (partial unique + deferred constraint trigger) | Role→permission mapping, deny-by-default, `@PreAuthorize`/custom PermissionEvaluator ตาม context |
| ใบสมัครค้างเดียวต่อบัญชี, ห้ามผู้ตรวจ = ผู้สมัคร, applicationId unique (มีแล้ว) | Target-role policy ของ Store Admin, ห้ามแก้บทบาทตนเอง, ห้ามระงับบัญชีที่มี platform role |
| คำเชิญห้าม STORE_OWNER (มีแล้ว), ไม่มีคำเชิญค้างซ้ำ | ตรวจสถานะ/transition (ORDER_UPDATE เฉพาะยังไม่ชำระ, SHIPMENT_* transitions, application transitions) |
| Idempotency keys (checkouts, orders, payments, refunds, ledger — มีแล้ว), webhook dedup | Field allowlist/DTO ต่อ action, ห้าม mass assignment, masking PII (Viewer/Warehouse), เกต payment-first |
| permission_version (มีแล้ว), row_version สำหรับ optimistic lock | Cache สิทธิ์ + invalidate เมื่อ permission_version เปลี่ยน, re-check ใน worker, signed URL อายุสั้น |
| enum CHECK ของ audit_logs, ADJUST ต้องมี reason, refund amount > 0 | Step-up auth (auth_time), redact PII/token ก่อนเขียน audit, error 401/403/404/409, allowedActions |

## 8. ผลกระทบต่อ V1/V2 และ Flyway

- **users.role (V1):** ปัจจุบัน NOT NULL DEFAULT 'CUSTOMER' CHECK (CUSTOMER|MERCHANT|ADMIN). ขัดกับโมเดลหลายบริบท — เสนอ 2 ขั้น: (1) V4 backfill `platform_roles` จาก role='ADMIN', COMMENT ว่า DEPRECATED, Spring Security เลิกอ่าน `users.role` (ใช้ platform_roles + shop_members + permission_version) และเลิก set 'MERCHANT' ตอน approve (2) migration ถัดไป DROP COLUMN หลังแก้ JPA `UserAccount` และ registration flow
- **merchant_applications (V2):** V3 drop UNIQUE(user_id) แล้ว (ดี — สมัครใหม่ได้); ข้อเสนอ P04 เพิ่ม partial unique + CHECK → ต้อง query ข้อมูลเดิมก่อน (`SELECT user_id, count(*) … WHERE status IN (open) GROUP BY 1 HAVING count(*)>1` และ `WHERE reviewer_id = user_id`) ; JPA `MerchantApplication` เพิ่ม withdrawnAt
- **refresh_tokens / merchant_documents:** ไม่กระทบ
- **Flyway:** ถ้า `V3__ecommerce_schema_v3.sql` ยัง**ไม่เคยรัน**บนฐานข้อมูลที่ใช้ร่วมกัน (dev/staging/prod) → แนะนำแก้ที่ schema_data_*.py / schema_meta.py / schema_enums.py แล้ว regenerate V3 (ตาราง 64→67 หรือ 70 ถ้ารวม P17; FK 152→171 หรือ 173) เพื่อคง single source; ถ้า V3 **รันแล้ว** → ห้ามแก้ V3 (checksum mismatch) ให้สร้าง `V4__authz_gaps.sql` ตาม draft ด้านล่าง และอัปเดต generator ให้ full script ตรงกับ V1+V2+V3+V4
- **Excel/ER:** ต้องเพิ่มแถวใน Tables/Relationships/Enums_Status (enum ใหม่: ownership_transfer_status, application_review_action, shop_customer_source, job_auth_context, audit_auth_context, otp_purpose +STEP_UP) และแก้ ER_Notes 'Permissions' ถ้าเลือก P17

**สิ่งที่ทำจริง (2026-10-04):** เลือกทาง regenerate V3 (V3 ยังไม่เคยรันบน shared DB) — users.role step 1 อยู่ใน V3 (คัดลอก ADMIN → platform_roles, คงคอลัมน์ + COMMENT DEPRECATED); merchant_applications CHECK self-review เพิ่มแบบ NOT VALID + WARNING ถ้าข้อมูลเดิมผิด, partial unique open application หยุด migration พร้อมวิธีแก้ถ้ามีข้อมูลซ้ำ; seed catalog (P17) อยู่ท้าย V3 และใน seed_reference_data.sql; ทดสอบ V1→V3 ด้วย Flyway CLI 11.8.2 ผ่าน

## 9. Role × Permission Matrix (จากเอกสาร)

### 9.1 Store (§4–§6)

| Permission | ความหมาย | Scope | OWNER | ADMIN | MANAGER | SALES | WAREHOUSE | VIEWER |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SHOP_READ | ดูข้อมูลร้าน | STORE | Y | Y | Y | Y | Y | Y |
| SHOP_UPDATE | แก้ข้อมูลร้าน โลโก้ และปก | STORE | Y | Y | - | - | - | - |
| SHOP_CLOSE | ปิดร้านตามกระบวนการ | STORE | Y | - | - | - | - | - |
| OWNERSHIP_TRANSFER | โอนเจ้าของร้าน | STORE | Y | - | - | - | - | - |
| USER_READ | ดูสมาชิกและบทบาทของทีม | STORE | Y | Y | - | - | - | - |
| USER_INVITE | เชิญสมาชิกเข้าร้าน | STORE | Y | Y | - | - | - | - |
| USER_ROLE_UPDATE | เปลี่ยนบทบาทสมาชิก | STORE | Y | Y | - | - | - | - |
| USER_REMOVE | นำสมาชิกออกจากร้าน | STORE | Y | Y | - | - | - | - |
| PRODUCT_READ | ดูสินค้าและราคาขาย | STORE | Y | Y | Y | Y | Y | Y |
| PRODUCT_CREATE | เพิ่มสินค้า | STORE | Y | Y | Y | - | - | - |
| PRODUCT_UPDATE | แก้สินค้า ราคา และรูป | STORE | Y | Y | Y | - | - | - |
| PRODUCT_ARCHIVE | เก็บสินค้าออกจากการขาย (soft archive) | STORE | Y | Y | Y | - | - | - |
| PRODUCT_IMPORT | นำเข้าข้อมูลสินค้าจำนวนมาก | STORE | Y | Y | Y | - | - | - |
| INVENTORY_READ | ดูยอดพร้อมขาย จอง และคงเหลือ | STORE | Y | Y | Y | Y | Y | Y |
| INVENTORY_RECEIVE | บันทึกรับสินค้าเข้า | STORE | Y | Y | Y | - | Y | - |
| INVENTORY_ADJUST | ปรับยอดพร้อมเหตุผลและ Audit | STORE | Y | Y | Y | - | Y | - |
| CUSTOMER_READ | ดูรายการและข้อมูลลูกค้าแบบปกปิด | STORE | Y | Y | Y | Y | - | Y |
| CUSTOMER_PII_READ | ดูชื่อเต็ม เบอร์ ที่อยู่ และอีเมล | STORE | Y | Y | Y | Y | - | - |
| CUSTOMER_CREATE | เพิ่มลูกค้าสำหรับการขาย | STORE | Y | Y | Y | Y | - | - |
| CUSTOMER_UPDATE | แก้ข้อมูลลูกค้า | STORE | Y | Y | Y | Y | - | - |
| ORDER_READ | ดูออเดอร์ภายในร้าน | STORE | Y | Y | Y | Y | Y | Y |
| ORDER_CREATE | สร้างออเดอร์ | STORE | Y | Y | Y | Y | - | - |
| ORDER_UPDATE | แก้รายการ จำนวน และข้อมูลออเดอร์ | STORE | Y | Y | Y | Y | - | - |
| ORDER_CANCEL | ยกเลิกออเดอร์ตามสถานะ | STORE | Y | Y | Y | - | - | - |
| ORDER_REFUND | เริ่มคืนเงินตามกระบวนการ | STORE | Y | Y | - | - | - | - |
| SHIPMENT_READ | ดูสถานะการจัดส่งและเลขติดตาม | STORE | Y | Y | Y | Y | Y | Y |
| SHIPMENT_ADDRESS_READ | ดูผู้รับ เบอร์ และที่อยู่สำหรับส่ง | STORE | Y | Y | Y | Y | Y | - |
| SHIPMENT_PACK | เริ่มแพ็กและยืนยันแพ็กเสร็จ | STORE | Y | Y | Y | - | Y | - |
| SHIPMENT_DISPATCH | บันทึกส่งออกและเลขติดตาม | STORE | Y | Y | Y | - | Y | - |
| SHIPMENT_UPDATE | แก้ขนส่งและเลขติดตาม (ก่อน Delivered) | STORE | Y | Y | Y | - | Y | - |
| REPORT_READ | ดูรายงานสรุปยอดขายและสต็อก | STORE | Y | Y | Y | - | - | Y |
| REPORT_EXPORT | ส่งออกรายงานสรุป (ไม่มี PII) | STORE | Y | Y | Y | - | - | - |
| ORDER_EXPORT | ส่งออกรายการออเดอร์แบบปกปิด | STORE | Y | Y | Y | - | - | - |
| AUDIT_READ | ดูประวัติการเปลี่ยนแปลงธุรกิจ | STORE | Y | Y | - | - | - | - |

### 9.2 Platform (§13) — PLATFORM_ADMIN เท่านั้น

| Permission | ขอบเขต |
| --- | --- |
| PLATFORM_SHOP_READ | ดูร้านทั้งหมดและสถานะ ไม่รวมข้อมูลผู้ซื้อ |
| PLATFORM_SHOP_SUSPEND | ระงับร้านพร้อมเหตุผลและ Audit |
| PLATFORM_SHOP_RESTORE | เปิดใช้งานร้านที่ถูกระงับหลังตรวจเงื่อนไข |
| PLATFORM_APPLICATION_READ | อ่านใบสมัครและเอกสารที่ใช้ตรวจเปิดร้าน |
| PLATFORM_APPLICATION_REVIEW | ขอข้อมูลเพิ่ม อนุมัติ หรือปฏิเสธใบสมัคร |
| PLATFORM_ACCOUNT_READ | ค้นบัญชีและสถานะ โดยปกปิดข้อมูลส่วนบุคคล |
| PLATFORM_ACCOUNT_SUSPEND | ระงับบัญชีและเพิกถอน Session พร้อมเหตุผล |
| PLATFORM_ACCOUNT_RESTORE | คืนสถานะบัญชีตามกระบวนการ |
| PLATFORM_SETTING_UPDATE | แก้ค่าระบบที่ Allowlist ไม่รวม Secret และการให้ Role |
| PLATFORM_AUDIT_READ | ดู Audit ด้านแพลตฟอร์มที่ตัดข้อมูลอ่อนไหวแล้ว |

### 9.3 Store Onboarding (§14) — APPLICANT (own application)

| Permission | ข้อจำกัด |
| --- | --- |
| SHOP_APPLICATION_CREATE | สร้างใบสมัครของตนเอง |
| SHOP_APPLICATION_READ | อ่านเฉพาะใบสมัครของตนเอง |
| SHOP_APPLICATION_UPDATE | แก้ของตนเองใน Draft หรือ Needs information |
| SHOP_APPLICATION_FILE_UPLOAD | อัปโหลดเอกสารของใบสมัครที่ยังแก้ได้ |
| SHOP_APPLICATION_SUBMIT | ส่งของตนเองเมื่อข้อมูลครบ |
| SHOP_APPLICATION_WITHDRAW | ถอน Draft, Submitted หรือ Needs information |

### 9.4 Storefront (§15)

| Permission | Guest | Buyer | Scope และเงื่อนไข |
| --- | --- | --- | --- |
| CATALOG_READ | Y | Y | Public ร้าน Active และสินค้าที่เผยแพร่ |
| BUYER_PROFILE_READ | - | Y | บัญชีตนเอง |
| BUYER_PROFILE_UPDATE | - | Y | ข้อมูลตนเอง ไม่รวม role และสถานะบัญชี |
| BUYER_ADDRESS_READ | - | Y | ที่อยู่ตนเอง |
| BUYER_ADDRESS_UPDATE | - | Y | เพิ่ม แก้ หรือลบที่อยู่ตนเอง |
| CART_READ | - | Y | ตะกร้าตนเอง แยกตามร้าน |
| CART_UPDATE | - | Y | ตะกร้าตนเอง ตรวจสินค้าและจำนวน |
| CHECKOUT_CREATE | - | Y | สร้างออเดอร์จากตะกร้าตนเอง |
| BUYER_PAYMENT_CREATE | - | Y | เริ่มชำระออเดอร์ตนเองที่ยังชำระได้ |
| BUYER_ORDER_READ | - | Y | ออเดอร์ที่ buyerUserId ตรง Session |
| BUYER_ORDER_CANCEL | - | Y | ตนเอง ก่อนชำระและก่อนเริ่มแพ็ก |
| BUYER_SHIPMENT_READ | - | Y | การจัดส่งของออเดอร์ตนเอง |

ตรวจความสอดคล้อง: ตัวอย่าง response `/shops/{shopId}/me/permissions` ของ STORE_SALES ใน §9 (12 สิทธิ์) ตรงกับคอลัมน์ Sales ในตาราง §4–§6 ทุกตัว

## 10. ภาคผนวก — SQL draft และผลทดสอบ

**หมายเหตุ:** draft ด้านล่างเป็นประวัติก่อน merge — ของจริงอยู่ใน `sql/ecommerce_schema_v3_full.sql` และ `sql/flyway/V3__ecommerce_schema_v3.sql` (generate จาก schema_data_*.py / schema_meta.py)

ไฟล์ draft (อยู่นอกโฟลเดอร์ schema เพื่อไม่ปนกับไฟล์ที่ generate):
- `/workspace/perm_gap/V4__authz_gaps_DRAFT.sql` — P01–P16
- `/workspace/perm_gap/V4b__authz_reference_OPTIONAL_DRAFT.sql` — P17 + seed 10 roles / 62 permissions / 150 role-permission rows (generate จาก matrix ในเอกสาร)
- `/workspace/perm_gap/smoke_V4_DRAFT.sql` — smoke test

ผลทดสอบบน PostgreSQL 17.11 (ฐานข้อมูลทดสอบชั่วคราว `permgap` ในเครื่อง box): รัน `ecommerce_schema_v3_full.sql` → V4 draft → V4b ผ่านทั้งหมด (ตาราง 64→70, FK 152→173). Smoke test ได้ผลตามคาด: ใบสมัครค้างใบที่ 2 ถูกปฏิเสธ · ผู้ตรวจ=ผู้สมัครถูกปฏิเสธ · approve (shop + owner + review) ใน transaction เดียวผ่าน · ร้านไม่มี owner ถูกปฏิเสธตอน COMMIT · owner คนที่ 2 ถูกปฏิเสธ · ปิด owner โดยไม่โอนถูกปฏิเสธ · โอนเจ้าของ (demote→promote→shops.owner_user_id) ผ่าน · คำเชิญค้างซ้ำ (ไม่สนตัวพิมพ์) ถูกปฏิเสธ · แต่งตั้ง platform admin ซ้ำหลังเพิกถอนผ่าน · audit_logs ค่า enum ผิดถูกปฏิเสธ

