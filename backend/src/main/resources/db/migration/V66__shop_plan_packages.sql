-- V62: expand plans catalog (prices, description, sort) and backfill empty shops.plan_code → FREE.
-- Idempotent; never deletes data; never overwrites an existing non-empty plan_code.

ALTER TABLE plans ADD COLUMN IF NOT EXISTS price_monthly NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS price_yearly NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS description VARCHAR(255) NULL;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN plans.price_monthly IS 'Monthly list price in THB; 0 = free.';
COMMENT ON COLUMN plans.price_yearly IS 'Yearly list price in THB; 0 = free.';
COMMENT ON COLUMN plans.description IS 'Short Thai marketing blurb for the plan card.';
COMMENT ON COLUMN plans.sort_order IS 'Display order ascending (1 = Free).';

-- Update existing catalog rows in place (FK from shops.plan_code).
UPDATE plans SET
    name = 'Free',
    price_monthly = 0,
    price_yearly = 0,
    limit_products = 5,
    limit_orders_per_month = 50,
    limit_staff = 2,
    limit_storage_bytes = 104857600,
    description = 'ร้านเริ่มต้น',
    sort_order = 1,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'FREE';

UPDATE plans SET
    name = 'Basic',
    price_monthly = 290,
    price_yearly = 2900,
    limit_products = 50,
    limit_orders_per_month = 500,
    limit_staff = 5,
    limit_storage_bytes = 1073741824,
    description = 'ร้านที่เริ่มโต',
    sort_order = 2,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'BASIC';

UPDATE plans SET
    name = 'Pro',
    price_monthly = 790,
    price_yearly = 7900,
    limit_products = NULL,
    limit_orders_per_month = NULL,
    limit_staff = NULL,
    limit_storage_bytes = 10737418240,
    description = 'ร้านที่ขายจริงจัง',
    sort_order = 3,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PRO';

-- Ensure seed rows exist even on DBs that skipped V47 seed somehow.
INSERT INTO plans (
    code, name, price_monthly, price_yearly,
    limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes,
    description, sort_order, active, created_at, updated_at
) VALUES
    ('FREE',  'Free',  0,    0,    5,    50,   2,    104857600,   'ร้านเริ่มต้น',     1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BASIC', 'Basic', 290,  2900, 50,   500,  5,    1073741824,  'ร้านที่เริ่มโต',    2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PRO',   'Pro',   790,  7900, NULL, NULL, NULL, 10737418240, 'ร้านที่ขายจริงจัง', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

DO $$
DECLARE
    empty_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO empty_count
      FROM shops
     WHERE plan_code IS NULL OR TRIM(plan_code) = '';
    RAISE NOTICE 'V62 backfill: % shop(s) with empty plan_code will be set to FREE', empty_count;
END $$;

-- Backfill ONLY empty plan_code — never overwrite BASIC/PRO/other.
UPDATE shops
   SET plan_code = 'FREE',
       modified_date_time = CURRENT_TIMESTAMP
 WHERE plan_code IS NULL OR TRIM(plan_code) = '';
