# Changelog — v3 / 2026-10-02 (Phase 1 end-to-end evidence)

ที่มา: เทียบ schema v2 กับ swimlane กระบวนการ Phase 1 (Merchant · Platform · Customer · Warehouse · Payment provider · Carrier · Evidence)
Definition of Done: ร้านสมัคร → สร้างร้าน → เพิ่มสินค้า → เผยแพร่ → ลูกค้าสั่ง → ชำระสำเร็จ → ร้านส่งของ → ลูกค้าติดตามสถานะ → ลูกค้าได้รับสินค้า **พร้อมหลักฐานครบทุกขั้น**

> ⚠️ **ยังไม่รองรับ COD** (เก็บเงินปลายทาง) — คง payment-first ตาม v2: ต้องชำระสำเร็จ (gateway CAPTURED หรือสลิป VERIFIED) ก่อนร้านยืนยันและจัดส่ง

ตัวเลขรวม: **59 → 64 ตาราง** · **145 → 159 FK** · คอลัมน์ ~761 → ~824 · ค่า enum 178 → 223 · 17 โมดูลเท่าเดิม

## ตารางใหม่ (NEW) — 5 ตาราง
- `otp_verifications` (Auth & Identity) — ประวัติ OTP: `user_id` (null ได้ ก่อน signup), `channel` SMS|EMAIL, `destination_masked`, `destination_hash`, `purpose`, `code_hash`, `attempts`, `max_attempts`, `expires_at`, `verified_at`, `status`, `ip_address`, `created_at`
- `payment_proofs` (Payments) — สลิปโอน / ใบเสร็จ gateway: `payment_id`, `order_id`, `shop_id`, `uploaded_by_user_id`, `media_file_id`, `proof_type` SLIP|GATEWAY_RECEIPT, `reference_no`, `amount_declared` NUMERIC(18,2), `currency`, `transferred_at`, `status`, `verified_by`, `verified_at`, `reject_reason`, `created_at`, `updated_at`
- `shipment_tracking_events` (Shipping, BIGSERIAL PK) — timeline การขนส่ง: `shipment_id`, `shop_id`, `source`, `carrier_status_code`, `normalized_status` (shipment_status), `description`, `location`, `event_at`, `raw_payload_safe` JSONB, `external_event_id` (**UNIQUE(shipment_id, external_event_id)** กันซ้ำ), `created_at`
- `shipment_delivery_proofs` (Shipping) — หลักฐานส่งถึง (POD): `shipment_id`, `media_file_id` (null ได้), `proof_type`, `receiver_name`, `delivered_at`, `created_at`
- `shipment_media` (Shipping) — รูปแพ็ก / ใบปะหน้า: `shipment_id`, `media_file_id`, `media_type`, `uploaded_by`, `created_at`

## FK ใหม่ (14)
- `otp_verifications.user_id → users` (SET NULL)
- `payment_proofs.payment_id → payments`, `.order_id → orders`, `.shop_id → shops`, `.media_file_id → media_files` (RESTRICT); `.uploaded_by_user_id → users`, `.verified_by → users` (SET NULL)
- `shipment_tracking_events.shipment_id → shipments` (CASCADE), `.shop_id → shops` (RESTRICT)
- `shipment_delivery_proofs.shipment_id → shipments`, `.media_file_id → media_files` (RESTRICT)
- `shipment_media.shipment_id → shipments`, `.media_file_id → media_files` (RESTRICT); `.uploaded_by → users` (SET NULL)

## คอลัมน์ใหม่ / เปลี่ยน (NEW / CHANGED)
### orders
- **NEW** `buyer_received_at` — ลูกค้ากด "ได้รับสินค้าแล้ว"
- **NEW** `auto_complete_at` — delivered_at + N วัน (แนะนำ platform_settings `order.auto_complete_days` = 7)
- **NEW** `completed_at` — เวลาเข้า COMPLETED
- **NEW** `completion_source` — BUYER_CONFIRM | AUTO | ADMIN
- **CHANGED** `status` เพิ่มค่า `COMPLETED` หลัง `DELIVERED`

### payments
- **NEW** `attempt_no` (default 1) — จ่ายไม่สำเร็จแล้ว retry = แถวใหม่ attempt_no+1 (แถว FAILED คงไว้เป็นหลักฐาน)

### shipments
- **NEW** `picked_up_at` — carrier รับพัสดุ
- **NEW** `last_tracking_event_at` — denorm event ล่าสุด
- **CHANGED** `status` รองรับ `OUT_FOR_DELIVERY`, `DELIVERY_FAILED`

### media_files
- **CHANGED** `purpose` เพิ่ม `PAYMENT_SLIP`, `PACKING_PHOTO`, `DELIVERY_PROOF`

## สถานะ / Enum
### เพิ่มค่าในกลุ่มเดิม
- `order_status`: **`COMPLETED`**
- `shipment_status`: **`OUT_FOR_DELIVERY`**, **`DELIVERY_FAILED`**
- `media_purpose` (เพิ่มกลุ่มลงชีต Enums_Status ครั้งแรก): **`PAYMENT_SLIP`**, **`PACKING_PHOTO`**, **`DELIVERY_PROOF`** + ค่าเดิม PRODUCT|SHOP_LOGO|SHOP_COVER|DOCUMENT|BLOG|REPORT|IMPORT|OTHER

### กลุ่ม enum ใหม่
- `completion_source`: BUYER_CONFIRM | AUTO | ADMIN
- `payment_proof_type`: SLIP | GATEWAY_RECEIPT
- `payment_proof_status`: PENDING_REVIEW | VERIFIED | REJECTED
- `tracking_event_source`: CARRIER_WEBHOOK | CARRIER_POLL | MERCHANT | SYSTEM | OMS
- `delivery_proof_type`: PHOTO | SIGNATURE | OTP | CARRIER_POD
- `shipment_media_type`: PACKING_PHOTO | LABEL | OTHER
- `otp_channel`: SMS | EMAIL
- `otp_purpose`: SIGNUP | LOGIN | PHONE_VERIFY | EMAIL_VERIFY | PASSWORD_RESET
- `otp_status`: PENDING | VERIFIED | EXPIRED | FAILED

## Flow ที่เปลี่ยน
- สถานะออเดอร์: `PENDING_PAYMENT → PAID_AWAITING_MERCHANT_CONFIRM → MERCHANT_CONFIRMED → READY_TO_PACK → PACKING → PACKED → DISPATCHED → DELIVERED → **COMPLETED**` (+ CANCELLED / REFUNDED)
- โอน/พร้อมเพย์ (manual): สลิป → `payment_proofs` PENDING_REVIEW → VERIFIED → `payments` CAPTURED → ออเดอร์ PAID_AWAITING_MERCHANT_CONFIRM (ต่อ flow v2 เดิม) · REJECTED → ลูกค้าอัปโหลดใหม่
- Tracking: carrier webhook / poll / OMS → `shipment_tracking_events` → อัปเดต `shipments.status` → DELIVERED + POD (`shipment_delivery_proofs`) → ตั้ง `orders.auto_complete_at`
- OMS: event สถานะขนส่งจาก OMS บันทึกเป็น `shipment_tracking_events.source = OMS` (dedup ด้วย external_event_id)

## Excel
- ชีตใหม่ **`Process_Evidence_Map`** — 31 แถว: Phase | Step | Lane | Evidence | Table | Column(s) | Status value | Notes (ทุก table/column ถูกตรวจว่ามีจริงตอน generate)
- อัปเดต Overview, Relationships, Enums_Status, OMS_Integration, ER_Notes (เวอร์ชัน v3 / 2026-10-02)

## ไม่เปลี่ยน
- ยังไม่รองรับ COD
- payment-first + merchant confirm gate + ซ่อน SKU จนร้านยืนยัน + OMS push หลัง confirm
- ไม่มี escrow / commission / settlement · PK หลัก UUID
