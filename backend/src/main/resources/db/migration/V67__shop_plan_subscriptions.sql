-- V63: shop self-serve plan purchases (Basic/Pro) with MONTHLY/YEARLY billing.
-- Does not modify existing shops.plan_code values.

ALTER TABLE shops ADD COLUMN IF NOT EXISTS plan_billing_cycle VARCHAR(10) NULL;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS plan_expires_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN shops.plan_billing_cycle IS 'MONTHLY|YEARLY for the currently active paid plan; null on FREE / unset.';
COMMENT ON COLUMN shops.plan_expires_at IS 'When the current paid plan ends; null means no expiry (FREE / unlimited admin assign).';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_shops_plan_billing_cycle'
    ) THEN
        ALTER TABLE shops
            ADD CONSTRAINT ck_shops_plan_billing_cycle
            CHECK (plan_billing_cycle IS NULL OR plan_billing_cycle IN ('MONTHLY', 'YEARLY'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS shop_plan_subscriptions (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shops(id),
    plan_code VARCHAR(50) NOT NULL REFERENCES plans(code),
    billing_cycle VARCHAR(10) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'THB',
    status VARCHAR(30) NOT NULL,
    starts_at TIMESTAMPTZ NULL,
    ends_at TIMESTAMPTZ NULL,
    payment_proof_file_id UUID NULL REFERENCES media_files(id),
    reject_reason TEXT NULL,
    created_by UUID NULL,
    reviewed_by UUID NULL,
    reviewed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_shop_plan_sub_billing_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_shop_plan_sub_status CHECK (status IN (
        'PENDING_PAYMENT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'CANCELLED', 'EXPIRED'
    )),
    CONSTRAINT ck_shop_plan_sub_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS ix_shop_plan_subscriptions_shop_status
    ON shop_plan_subscriptions (shop_id, status);

CREATE INDEX IF NOT EXISTS ix_shop_plan_subscriptions_status_created
    ON shop_plan_subscriptions (status, created_at DESC);

-- One open purchase order per shop at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_plan_subscriptions_open
    ON shop_plan_subscriptions (shop_id)
    WHERE status IN ('PENDING_PAYMENT', 'PENDING_REVIEW');

COMMENT ON TABLE shop_plan_subscriptions IS
    'Merchant-initiated plan purchase/upgrade requests; amount is snapshotted from plans at order time.';
