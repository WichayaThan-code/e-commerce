# Changelog — Payment-Confirm Flow (v2 / 2026-09-28)

สรุปเฉพาะตาราง / คอลัมน์ / สถานะที่เพิ่มหรือเปลี่ยน

## ตารางใหม่ (NEW)
- `order_financial_snapshots` — ยอดเงิน denorm + buyer_province/district/postal_hint สำหรับ seller หลังจ่ายโดยไม่ join items
- `merchant_order_confirmations` — audit ยืนยัน/ปฏิเสธออเดอร์ (confirmed_by_user_id, confirmed_at, ip, user_agent)
- `order_item_reveal_logs` — audit เปิดเผย line items ให้ seller

## คอลัมน์ใหม่ / เปลี่ยนชื่อ (NEW / CHANGED)
### orders
- **NEW** `merchant_confirm_status`, `merchant_confirmed_at`, `items_revealed_at`, `confirm_deadline_at`
- **CHANGED** `status` default → `PENDING_PAYMENT`; ค่าสถานะหลักเป็น flow ใหม่ (ดูด้านล่าง)
- **CHANGED** `paid_at` — ตั้งเมื่อ payment CAPTURED

### order_items
- **NEW** `image_url_snapshot`
- **CHANGED** คำอธิบาย: source of truth แต่ seller ห้าม SELECT จนกว่า `merchant_confirmed_at`

### inventory_levels
- **RENAMED** `reserved` → `reserved_qty`
- **CHANGED** คำอธิบาย `on_hand` / `available` ให้สอดคล้อง deduct ตอน confirm

### inventory_reservations
- **NEW** `cart_id`, `order_item_id`, `reserved_at`, `paid_hold_at`, `committed_at`, `released_at`
- **CHANGED** `status` รองรับ `PAID_HOLD`

### inventory_ledger
- **RENAMED** `reserved_before` / `reserved_after` → `reserved_qty_before` / `reserved_qty_after`
- **NEW** `reservation_id`, `order_item_id`
- **NEW** entry types: `SALE_CONFIRM`, `RESTOCK_REFUND` (คง `COMMIT_SALE` เป็น alias)

### payments
- **NEW** `intent_id`, `authorized_at`, `captured_at`
- **CHANGED** `status` เริ่มที่ `INTENT`; flow INTENT → PENDING → AUTHORIZED → CAPTURED

## สถานะ / Enum ใหม่ (NEW)
### order_status
- `PENDING_PAYMENT`
- `PAID_AWAITING_MERCHANT_CONFIRM`
- `MERCHANT_CONFIRMED`
- (+ คง fulfillment: READY_TO_PACK … DELIVERED, CANCELLED, REFUNDED)
- legacy: DRAFT, CONFIRMED, PAID

### merchant_confirm_status
- `NOT_REQUIRED` | `AWAITING` | `CONFIRMED` | `REJECTED` | `EXPIRED`

### merchant_confirm_action
- `CONFIRM` | `REJECT`

### item_reveal_reason
- `MERCHANT_CONFIRM` | `ADMIN_OVERRIDE` | `SYSTEM`

### inventory_reservation_status
- **NEW** `PAID_HOLD`
- คง: ACTIVE, COMMITTED, RELEASED, EXPIRED

### inventory_entry_type
- **NEW** `SALE_CONFIRM`, `RESTOCK_REFUND`

### payment_record_status
- **NEW** `INTENT`

## นโยบายที่เกี่ยวข้อง (ไม่ใช่คอลัมน์ แต่บันทึกใน OMS_Integration / ER_Notes)
- Seller หลังจ่ายเห็นเงิน + logistics hint (จังหวัด/อำเภอ/รหัสไปรษณีย์) — **ไม่เห็น** SKU/ชื่อสินค้า/รูป/ที่อยู่เต็ม จน confirm
- Inventory: RESERVE → PAID_HOLD → SALE_CONFIRM (deduct) หรือ RELEASE
- **OMS push หลัง merchant confirm เท่านั้น**
