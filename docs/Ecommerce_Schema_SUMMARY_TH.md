# สรุป Schema PostgreSQL — ThaiShopFun Ecommerce

อัปเดต: 2026-10-04 ICT · **v3 Phase 1 end-to-end evidence + authz (Role and Permission Matrix v1.1)** (ต่อยอด v2 payment-confirm flow) · **ยังไม่รองรับ COD**  
ไฟล์ Excel: `/workspace/ecommerce-schema/Ecommerce_PostgreSQL_Schema.xlsx`

## Changelog (v3 + authz / 2026-10-04) — รวม Permission Gap P01–P17 เข้า v3 (ไม่แยก V4)

รายละเอียด: `CHANGELOG_AUTHZ_TH.md` · Excel ชีตใหม่ **Permission_Support** · **64 → 70 ตาราง, 152 → 173 FK, 988 → 1,103 คอลัมน์**
1. ตารางใหม่ 6: `shop_customers` (+ `orders.shop_customer_id`), `shop_ownership_transfers`, `merchant_application_reviews`, `auth_roles` / `auth_permissions` / `auth_role_permissions` (**READ-ONLY reference** — seed 10 roles / 62 permissions / 150 mappings; การ authorize ยังอยู่ในโค้ด)
2. แก้ตารางเดิม 14: users (suspend + role DEPRECATED), platform_roles (ประวัติ grant/revoke + partial unique active), otp_verifications (STEP_UP), merchant_applications (withdrawn_at + ห้าม self-review + 1 ใบเปิดต่อ user), shops (suspend/restore/close actor), shop_members (removed_by + Owner เดียว), shop_invitations (revoke + 1 PENDING ต่อ email), orders (row_version), inventory_ledger (ADJUST ต้องมี reason), refunds (amount > 0), media_files / background_jobs (ตรวจสิทธิ์ซ้ำใน job/ไฟล์), audit_logs (auth_context, service_name + CHECK), platform_settings (is_admin_editable allowlist)
3. **Owner เดียวต่อร้าน** — partial unique + constraint triggers แบบ deferred (`check_shop_single_owner()`) ตรวจตอน COMMIT
4. Flyway V3 ยังรันต่อจาก V1/V2 ได้: คัดลอก `users.role='ADMIN'` → platform_roles (step 1, คงคอลัมน์), CHECK self-review บน merchant_applications เพิ่มแบบ NOT VALID + WARNING หากข้อมูลเดิมผิด, seed catalog ท้ายไฟล์ — **ยังไม่ได้รันบน shared DB**
5. ข้อกำหนด 67 ข้อ: Supported 31 → **51**, Partial 19 → **2**, Not supported 3 → **0**, App-layer 14

## Changelog (v3 audit standard + เวลาไทย / 2026-10-02)

รายละเอียด: `CHANGELOG_AUDIT_FIELDS_TH.md`
1. **ทุกตาราง (64/64 → ปัจจุบัน 70/70)** ลงท้ายด้วย `created_date_time` (TIMESTAMPTZ NOT NULL DEFAULT now()) · `created_by` (UUID NULL) · `modified_date_time` (TIMESTAMPTZ NOT NULL DEFAULT now()) · `modified_by` (UUID NULL) — แทน created_at/updated_at; updated_by → modified_by
2. created_by/modified_by = logical ref `users.id` **ไม่มี FK** → ลบ FK audit เดิม 7 ตัว: **FK 159 → 152**
3. trigger `set_modified_date_time()` → `trg_<table>_modified_date_time` ครบ 64 ตาราง (เดิม 31)
4. **เวลาไทย**: คอลัมน์เวลาเป็น TIMESTAMPTZ ทั้งหมด แสดงผล Asia/Bangkok (+07) — สคริปต์ตั้ง `SET TIME ZONE` + `ALTER DATABASE … SET timezone`; Spring Boot: `spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Bangkok`, `spring.jackson.time-zone=Asia/Bangkok`, entity ใช้ `OffsetDateTime`/`Instant`
5. Flyway V3: rename คอลัมน์ใน users / refresh_tokens / merchant_applications แบบมี guard (ข้อมูลเดิมคงอยู่) — **ต้องแก้ JPA entity** `UserAccount`, `RefreshToken`, `MerchantApplication` (`@Column(name="created_at"/"updated_at")`)

## Changelog (v3 / 2026-10-02)

รายละเอียดเต็ม: `CHANGELOG_V3_TH.md` — ปิดช่องว่างเทียบ swimlane Phase 1 (สมัคร → สร้างร้าน → สินค้า → เผยแพร่ → สั่ง → ชำระ → ส่ง → ติดตาม → **ลูกค้าได้รับ**) ให้มีหลักฐานครบ

1. **สลิป/หลักฐานชำระ** — ตารางใหม่ `payment_proofs` (SLIP | GATEWAY_RECEIPT; PENDING_REVIEW → VERIFIED | REJECTED) สำหรับ BANK_TRANSFER / PROMPTPAY + `payments.attempt_no` สำหรับ retry หลังจ่ายไม่สำเร็จ
2. **ลูกค้าได้รับสินค้า** — `order_status` ใหม่ `COMPLETED` หลัง `DELIVERED` + `orders.buyer_received_at`, `completed_at`, `auto_complete_at`, `completion_source` (BUYER_CONFIRM | AUTO | ADMIN)
3. **Tracking + POD** — `shipment_tracking_events` (dedup UNIQUE(shipment_id, external_event_id); source รวม OMS), `shipment_delivery_proofs` (PHOTO | SIGNATURE | OTP | CARRIER_POD), `shipments.picked_up_at`, `last_tracking_event_at`, shipment_status `OUT_FOR_DELIVERY`, `DELIVERY_FAILED`
4. **OTP** — `otp_verifications` (เก็บ code_hash / destination_hash เท่านั้น)
5. **รูปแพ็ก/ใบปะหน้า** — `shipment_media` + `media_files.purpose` ใหม่ PAYMENT_SLIP, PACKING_PHOTO, DELIVERY_PROOF
6. **ชีต Excel ใหม่ `Process_Evidence_Map`** — ขั้นตอน swimlane → ตาราง/คอลัมน์/ค่าสถานะที่เก็บหลักฐาน

## Changelog (v2 / 2026-09-28)

รองรับกฎธุรกิจใหม่: **ลูกค้าจ่ายก่อน → ร้านเห็นยอดเงินแต่ยังไม่เห็นสินค้า → ร้านยืนยันออเดอร์จึงเปิดเผยรายการและตัดสต็อกในแอป → แล้วค่อย sync OMS**

### สิ่งที่เปลี่ยนหลัก
1. **Order status ใหม่ (payment-first)**  
   `PENDING_PAYMENT` → `PAID_AWAITING_MERCHANT_CONFIRM` → `MERCHANT_CONFIRMED` → ขั้น fulfillment → `CANCELLED` / `REFUNDED`  
   (เลิกใช้ `DRAFT` / `CONFIRMED` / `PAID` เป็นสถานะหลัก — เก็บเป็น legacy ใน Enums)
2. **เกตความเป็นส่วนตัวของสินค้า**  
   - คอลัมน์ใหม่บน `orders`: `merchant_confirm_status`, `merchant_confirmed_at`, `items_revealed_at`, `confirm_deadline_at`  
   - ตารางใหม่ `order_financial_snapshots` — ยอดเงิน + province/district/postal hint สำหรับ seller list โดยไม่ต้อง join `order_items`  
   - ตาราง audit `merchant_order_confirmations`, `order_item_reveal_logs`  
   - **แนวทางที่เลือก:** คง `order_items` เป็น source of truth + บังคับที่ API/UI ห้าม SELECT รายละเอียด SKU จนกว่า `merchant_confirmed_at` มีค่า (เอกสารใน ER_Notes)
3. **Inventory ในแอป (reserve → deduct → release)**  
   - `inventory_levels.reserved` เปลี่ยนชื่อเป็น `reserved_qty`  
   - `inventory_reservations`: เพิ่ม `order_item_id`, `cart_id`, สถานะ `PAID_HOLD`, timestamps reserved/paid_hold/committed/released  
   - Ledger: `SALE_CONFIRM` (ตัดตอนร้านยืนยัน), `RESTOCK_REFUND`; ลิงก์ `reservation_id` / `order_item_id`
4. **Payments**  
   - สถานะ `INTENT` + คอลัมน์ `intent_id`, `authorized_at`, `captured_at`  
   - Webhook CAPTURED → ออเดอร์ `PAID_AWAITING_MERCHANT_CONFIRM`
5. **OMS**  
   - **Push หลัง merchant confirm เท่านั้น** — OMS ไม่ได้รับรายการที่ร้านยังไม่เปิดเผย (อัปเดตชีต OMS_Integration + flow ใน Excel)

### สิ่งที่ไม่เปลี่ยน
- ไม่มี escrow / commission / settlement  
- OMS ยังเป็น adapter ภายนอก  
- PK หลักยังเป็น UUID; append-only ยังใช้ BIGSERIAL

## ภาพรวม

ออกแบบ schema ระดับ senior สำหรับ **หน้าร้าน Ecommerce (storefront + hub)** ที่ต้อง **เสียบเข้า OMS ภายนอก** (ทีมอื่นสร้าง) โดยไม่พิมพ์ schema ภายใน OMS ทั้งก้อน

- เป้าหมาย DB: **PostgreSQL 17**
- Audit: ทุกตารางมี created_date_time · created_by · modified_date_time · modified_by (ท้ายตาราง); เวลา TIMESTAMPTZ แสดงผล Asia/Bangkok
- PK หลัก: **UUID** (สอดคล้อง Flyway V1/V2 ที่มีอยู่)
- BIGSERIAL: ตาราง append-only (`inventory_ledger`, `order_status_history`, `order_item_reveal_logs`, `audit_logs`, `outbox_events`, `integration_events`)
- Tenant: `shop_id` เกือบทุกตารางธุรกิจ
- เงิน: `NUMERIC(18,2)` + `currency` — **ไม่มี escrow / commission / settlement**

## โมดูลและจำนวนตาราง (รวม 70 ตาราง · 173 FK)

| โมดูล | จำนวนตาราง | ตารางหลัก |
| --- | ---: | --- |
| Auth & Identity | 8 | users, refresh_tokens, platform_roles, password_reset_tokens, **otp_verifications** (v3), **auth_roles**, **auth_permissions**, **auth_role_permissions** (authz, read-only) |
| Merchant Onboarding | 3 | merchant_applications, merchant_documents, **merchant_application_reviews** (authz) |
| Shop & Team | 7 | shops, shop_members, shop_invitations, shop_payment_gateways, hub_categories, **shop_customers**, **shop_ownership_transfers** (authz) |
| Catalog | 5 | shop_categories, products, product_skus, product_images, product_category_map |
| Inventory | 3 | inventory_levels, inventory_reservations, inventory_ledger |
| Cart & Checkout | 4 | carts, cart_items, checkouts, buyer_addresses |
| Orders | 7 | orders, order_items, order_addresses, order_status_history, **order_financial_snapshots**, **merchant_order_confirmations**, **order_item_reveal_logs** |
| Payments | 3 | payments, payment_webhook_events, **payment_proofs** (v3) |
| Shipping | 6 | shipping_methods, shipments, shipment_items, **shipment_tracking_events**, **shipment_delivery_proofs**, **shipment_media** (v3) |
| Promotions | 3 | promotions, coupons, promotion_usages |
| Reviews | 1 | product_reviews |
| Content | 2 | blog_categories, blog_posts |
| Returns & Cancellations | 4 | order_cancellations, return_requests, return_items, refunds |
| Media | 2 | media_files, media_variants |
| Notifications & Jobs | 4 | notifications, background_jobs, outbox_events, product_imports |
| Audit & Platform | 2 | audit_logs, platform_settings |
| **OMS Integration (adapter)** | **6** | oms_connections, oms_order_links, oms_shipment_links, oms_webhook_inbox, oms_sync_checkpoints, integration_events |

Sheets ใน Excel: Overview, Tables (1,103 คอลัมน์ รวม audit 280), Relationships (173 FK), Enums_Status (267 ค่า), OMS_Integration, **Process_Evidence_Map** (40 แถว: Phase 1 31 + Authz 9), ER_Notes, **Permission_Support** (authz: R01–R67 → ตาราง/คอลัมน์ + DB guarantees + catalog)

## Flow สั้น ๆ (v3)

1. Checkout → สร้างออเดอร์ `PENDING_PAYMENT` + payment `INTENT` + **RESERVE** สต็อก (`ACTIVE`) + `order_financial_snapshots`
2. PG webhook CAPTURED **หรือ** สลิป `payment_proofs` VERIFIED (โอน/พร้อมเพย์) → ออเดอร์ `PAID_AWAITING_MERCHANT_CONFIRM` + reservation `PAID_HOLD` — **ร้านเห็นยอดเงิน / hint จังหวัด-อำเภอ-รหัสไปรษณีย์ เท่านั้น**
3. ร้านกดยืนยัน → `merchant_order_confirmations` + เปิด `items_revealed_at` + ledger **SALE_CONFIRM** (หัก `on_hand`) + reservation `COMMITTED` + outbox `oms.order.push`
4. Worker ดัน OMS **หลัง confirm** → `oms_order_links`
5. แพ็ก (`shipment_media` PACKING_PHOTO) → DISPATCHED (tracking_number) → `shipment_tracking_events` (carrier / OMS)
6. DELIVERED + POD (`shipment_delivery_proofs`) → ตั้ง `auto_complete_at` → ลูกค้ากดรับ / auto → **`COMPLETED`** → เปิดรีวิว
7. ยกเลิก/หมดเวลาก่อน confirm → **RELEASE**; refund หลังตัดสต็อก → **RESTOCK_REFUND**; จ่ายไม่สำเร็จ → retry (payments แถวใหม่ attempt_no+1)

## จุดเชื่อม OMS (Integration hooks)

| จุดเสียบ | รายละเอียด |
| --- | --- |
| `orders.oms_external_order_id` / `oms_sync_status` | sync หลัง **merchant confirm** (v2) |
| `orders.merchant_confirm_status` / `merchant_confirmed_at` | เกตเปิดเผยสินค้า + จุดยิง OMS |
| `shipments.oms_external_shipment_id` / `label_url` | อ้างอิง shipment + ใบปะหน้าจาก OMS |
| `shipment_tracking_events` (source=OMS) / `shipment_delivery_proofs` (CARRIER_POD) | v3: สถานะขนส่ง + POD ที่ OMS ส่งกลับ (dedup external_event_id) |
| `return_requests.oms_external_return_id` | อ้างอิงคืนสินค้าใน OMS |
| `oms_connections` … `integration_events` | adapter ตามเดิม |

## สมมติฐานสำคัญ

1. ตะกร้า/checkout **ต่อร้าน** (แต่ละร้านมี PG ของตัวเอง)
2. Guest checkout ยังไม่รองรับ
2b. **สิทธิ์ตัดสินในโค้ด** — DB รับประกันเฉพาะกฎข้อมูล (Owner เดียว, ห้าม self-review ฯลฯ); `auth_*` เป็น reference; `users.role` deprecated
3. ไม่ทำตาราง settlement/escrow
4. OMS module เป็น **adapter เท่านั้น**
5. **OMS push หลัง merchant confirm** — ไม่ส่งรายการที่ร้านยังไม่เปิด
6. Align กับตารางเดิม V1/V2 แล้วขยาย
7. **ยังไม่รองรับ COD** (v3) — ต้องชำระสำเร็จก่อน fulfillment
8. Auto-complete หลังส่งถึง N วัน (ค่าแนะนำ 7 วัน, ตั้งใน platform_settings)

## ชีต Excel

1. **Overview** — โมดูล + รายการตาราง + v3 version note  
2. **Tables** — คอลัมน์ทั้งหมด  
3. **Relationships** — FK + cardinality + ON DELETE  
4. **Enums_Status** — รวม merchant confirm + reservation PAID_HOLD + SALE_CONFIRM + v3: COMPLETED, payment_proof_*, tracking/delivery/otp enums, media_purpose  
5. **OMS_Integration** — push timing หลัง confirm  
6. **Process_Evidence_Map** (v3) — swimlane step → table.column → status value  
8. **Permission_Support** (authz) — ข้อกำหนดเอกสาร Role and Permission Matrix → สถานะก่อน/หลัง → ตาราง/constraint + รายการ DB guarantees + catalog 10 roles
7. **ER_Notes** — authz: Permissions, Single store owner, Ownership transfer, Application review, Order editable, Shop customers, Jobs & files, Audit, users.role deprecation, JPA impact · Audit fields (มาตรฐาน + decisions), JPA impact, Timezone (Asia/Bangkok), Merchant item privacy, Seller safe fields, Payment→Confirm flow, API contract + v3: Payment proof, Order completion, Tracking & POD, OTP, Evidence media, Out of scope (COD)

## ERD / Deck (`erd/`)
- `erd_00_*` overview, `erd_01`…`erd_06` ต่อโมดูล (◆ = ตาราง evidence v3, ▲ = ตาราง authz), **`erd_07_authz_permissions`** (มุมมองสิทธิ์: roles/team/review/transfer/customers/audit), `flow_01_payment_confirm` (มี COMPLETED + ลูกค้ารับของ), `flow_02_oms_integration`
- `ecommerce_schema.dbml`, `ecommerce_erd.mmd` (70 ตาราง / 173 FK — parse ผ่าน @dbml/core 10.2 และ mermaid 11)
- `Ecommerce_ERD_Presentation.pptx` (17 สไลด์ รวม evidence map 3 สไลด์ + สไลด์ Role & Permission support + erd_07; รูปในสไลด์ย่อเหลือ ≤2400–2800 px แบบ palette PNG → ไฟล์ ~2.9 MB; รูปเต็มความละเอียดอยู่ใน erd/ และ erd_split/) · rebuild: `erd/build_all.sh`
