-- V25: Order checkout snapshots + REFUND_REQUESTED status (additive, idempotent).
-- Follows V1 orders / order_items / order_status_history; does not rewrite existing rows.

-- 1. Allow REFUND_REQUESTED on orders.status (user refund flow; not in V1 CHECK).
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (status IN (
    'PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED',
    'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED',
    'CANCELLED', 'REFUNDED', 'REFUND_REQUESTED'));

ALTER TABLE order_status_history DROP CONSTRAINT IF EXISTS ck_order_status_history_from_status;
ALTER TABLE order_status_history ADD CONSTRAINT ck_order_status_history_from_status CHECK (
    from_status IS NULL OR from_status IN (
        'PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED',
        'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED',
        'CANCELLED', 'REFUNDED', 'REFUND_REQUESTED', 'DRAFT', 'CONFIRMED', 'PAID'));

ALTER TABLE order_status_history DROP CONSTRAINT IF EXISTS ck_order_status_history_to_status;
ALTER TABLE order_status_history ADD CONSTRAINT ck_order_status_history_to_status CHECK (to_status IN (
    'PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED',
    'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED',
    'CANCELLED', 'REFUNDED', 'REFUND_REQUESTED', 'DRAFT', 'CONFIRMED', 'PAID'));

-- 2. Snapshot / refund-reject helpers on orders.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_method_id uuid;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_method_name_snapshot varchar(150);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_carrier_code_snapshot varchar(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status_before_refund varchar(40);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_committed boolean DEFAULT false NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_shipping_method_id' AND conrelid = 'orders'::regclass) THEN
        ALTER TABLE orders ADD CONSTRAINT fk_orders_shipping_method_id
            FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id) ON DELETE SET NULL;
    END IF;
END $$;
COMMENT ON COLUMN orders.shipping_method_name_snapshot IS 'วิธีจัดส่งตอน checkout (ไม่เปลี่ยนตามการแก้ master ทีหลัง)';
COMMENT ON COLUMN orders.status_before_refund IS 'สถานะก่อนเข้า REFUND_REQUESTED — ใช้ตอนปฏิเสธคืนเงิน';
COMMENT ON COLUMN orders.stock_committed IS 'true หลัง COMMIT_SALE (ตอนส่งของ DISPATCHED); false = ยังแค่ RESERVE';

-- 3. Line snapshots: compare-at + cost at purchase.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS compare_at_price_snapshot numeric(18,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cost_snapshot numeric(18,2);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS barcode_snapshot varchar(64);
COMMENT ON COLUMN order_items.compare_at_price_snapshot IS 'ราคาขีดฆ่าตอนซื้อ';
COMMENT ON COLUMN order_items.cost_snapshot IS 'ต้นทุนตอนซื้อ (ร้านเห็น)';

-- 4. Timeline metadata (optional JSON for carrier/tracking etc.).
ALTER TABLE order_status_history ADD COLUMN IF NOT EXISTS metadata_json text;
COMMENT ON COLUMN order_status_history.metadata_json IS 'JSON เพิ่มเติม เช่น carrier, trackingNumber';

-- 5. Index for buyer order list / idempotency lookups already exist (uq_orders_idempotency_key, ix_orders_buyer_user_id).
CREATE INDEX IF NOT EXISTS ix_orders_status ON orders (shop_id, status);
CREATE INDEX IF NOT EXISTS ix_orders_payment_status ON orders (shop_id, payment_status);
