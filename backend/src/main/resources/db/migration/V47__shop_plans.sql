-- V47: shop subscription plans (catalog + optional shops.plan_code). Additive; existing shops stay unassigned (NULL).

CREATE TABLE IF NOT EXISTS plans (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    limit_products BIGINT,
    limit_orders_per_month BIGINT,
    limit_staff BIGINT,
    limit_storage_bytes BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE plans IS 'Platform shop plan catalog; null limit_* = unlimited.';

INSERT INTO plans (code, name, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes, active, created_at, updated_at)
VALUES
    ('FREE',  'Free',  50,   100,   2,  104857600,   TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BASIC', 'Basic', 500,  1000, 10,  1073741824,  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PRO',   'Pro',   NULL, NULL, 50,  10737418240, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE shops ADD COLUMN IF NOT EXISTS plan_code VARCHAR(50) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_shops_plan_code'
    ) THEN
        ALTER TABLE shops
            ADD CONSTRAINT fk_shops_plan_code FOREIGN KEY (plan_code) REFERENCES plans(code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shops_plan_code ON shops (plan_code);
