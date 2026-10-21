-- V32 (B1): Cash on Delivery (COD) — additive, idempotent.
-- Owner decision 2026-10-19: COD is added on top of the V1/v3 payment-first design (docs said "ยังไม่รองรับ COD").
-- Per-shop setting = shop_payment_gateways row provider 'COD' (is_active + encrypted config {fee, maxOrderAmount}).
-- Cash collected after delivery = payments row provider 'COD' CAPTURED (+ optional payment_proofs COD_RECEIPT).

-- 1. How the order is paid + the COD fee charged at checkout (included in grand_total).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method varchar(20) NOT NULL DEFAULT 'BANK_TRANSFER';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_fee numeric(18,2) NOT NULL DEFAULT 0;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_payment_method;
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('BANK_TRANSFER', 'COD'));
ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_cod_fee;
ALTER TABLE orders ADD CONSTRAINT ck_orders_cod_fee CHECK (cod_fee >= 0);
COMMENT ON COLUMN orders.payment_method IS 'BANK_TRANSFER = ชำระก่อน (โอน/พร้อมเพย์ + สลิป) | COD = เก็บเงินปลายทาง (V32 (B1))';
COMMENT ON COLUMN orders.cod_fee IS 'ค่าธรรมเนียม COD ตอน checkout — รวมอยู่ใน grand_total แล้ว';
CREATE INDEX IF NOT EXISTS ix_orders_payment_method ON orders (shop_id, payment_method);

-- 2. COD as a payment provider (payments) and a per-shop payment setting (shop_payment_gateways).
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_provider;
ALTER TABLE payments ADD CONSTRAINT ck_payments_provider CHECK (provider IN ('OMISE', '2C2P', 'PROMPTPAY', 'BANK_TRANSFER', 'CUSTOM', 'COD'));
ALTER TABLE shop_payment_gateways DROP CONSTRAINT IF EXISTS ck_shop_payment_gateways_provider;
ALTER TABLE shop_payment_gateways ADD CONSTRAINT ck_shop_payment_gateways_provider CHECK (provider IN ('OMISE', '2C2P', 'PROMPTPAY', 'BANK_TRANSFER', 'CUSTOM', 'COD'));

-- 3. Optional evidence of cash collected (receipt / carrier COD remittance) as a proof row.
ALTER TABLE payment_proofs DROP CONSTRAINT IF EXISTS ck_payment_proofs_proof_type;
ALTER TABLE payment_proofs ADD CONSTRAINT ck_payment_proofs_proof_type CHECK (proof_type IN ('SLIP', 'GATEWAY_RECEIPT', 'COD_RECEIPT'));
