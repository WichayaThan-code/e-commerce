-- V24: shopping cart backend.
-- Reuses the V1 tables carts / cart_items (one cart row per buyer per shop), promotions + coupons
-- (shop coupons) and shipping_methods (per-shop shipping rules). Additive only: no data is deleted
-- or rewritten. Re-runnable (IF NOT EXISTS / guarded constraints).

-- 1. Guest carts: a cart belongs to a signed-in buyer OR to a guest token (SHA-256 hex of the
--    X-Cart-Token header; the raw token is never stored). Merged into the buyer's cart on login.
ALTER TABLE carts ALTER COLUMN buyer_user_id DROP NOT NULL;
ALTER TABLE carts ADD COLUMN IF NOT EXISTS guest_token_hash varchar(64);
ALTER TABLE carts ADD COLUMN IF NOT EXISTS coupon_code varchar(64);
ALTER TABLE carts ADD COLUMN IF NOT EXISTS shipping_method_id uuid;
ALTER TABLE carts ADD COLUMN IF NOT EXISTS expires_at timestamp with time zone;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_carts_owner' AND conrelid = 'carts'::regclass) THEN
        ALTER TABLE carts ADD CONSTRAINT ck_carts_owner CHECK (buyer_user_id IS NOT NULL OR guest_token_hash IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_carts_shipping_method_id' AND conrelid = 'carts'::regclass) THEN
        ALTER TABLE carts ADD CONSTRAINT fk_carts_shipping_method_id
            FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id) ON DELETE SET NULL;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_carts_guest_token_hash_shop_id ON carts (guest_token_hash, shop_id)
    WHERE status = 'ACTIVE' AND guest_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_carts_expires_at ON carts (expires_at) WHERE status = 'ACTIVE';
COMMENT ON COLUMN carts.guest_token_hash IS 'ตะกร้าผู้เยี่ยมชม: SHA-256 hex ของ X-Cart-Token (ไม่เก็บ token จริง); NULL = ตะกร้าของสมาชิก';
COMMENT ON COLUMN carts.coupon_code IS 'โค้ดคูปองของร้านที่ใช้กับตะกร้านี้ (ตรวจซ้ำทุกครั้งที่คำนวณยอด)';
COMMENT ON COLUMN carts.shipping_method_id IS 'วิธีจัดส่งที่ผู้ซื้อเลือก; NULL = ใช้วิธีที่ถูกที่สุด';
COMMENT ON COLUMN carts.expires_at IS 'หมดอายุเมื่อไม่มีการใช้งาน (ต่ออายุทุกครั้งที่แก้ตะกร้า)';

-- 2. Lines selected for checkout (select / deselect in the cart page).
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS is_selected boolean DEFAULT true NOT NULL;
COMMENT ON COLUMN cart_items.is_selected IS 'เลือกไว้สำหรับ checkout';

-- 3. Weight-based shipping: fee_amount covers the first 1 kg, extra_fee_per_kg each started kg after.
ALTER TABLE shipping_methods ADD COLUMN IF NOT EXISTS extra_fee_per_kg numeric(18,2);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_shipping_methods_fee_type' AND conrelid = 'shipping_methods'::regclass) THEN
        ALTER TABLE shipping_methods ADD CONSTRAINT ck_shipping_methods_fee_type CHECK (fee_type IN ('FLAT', 'WEIGHT', 'FREE')) NOT VALID;
    END IF;
END $$;
COMMENT ON COLUMN shipping_methods.extra_fee_per_kg IS 'WEIGHT: ค่าส่งต่อทุก 1 กก. ที่เกิน 1 กก. แรก (fee_amount = 1 กก. แรก)';
