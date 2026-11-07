-- V53: Durable merchant lifecycle notifications (shop approve/reject/suspend/restore).
-- Operational order/stock feed stays derive-on-read (V37). Retention for the bell is 7 days (rolling).

CREATE TABLE merchant_notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    type varchar(64) NOT NULL,
    entity_type varchar(32) NOT NULL,
    entity_id varchar(64) NOT NULL,
    dedupe_key varchar(191) NOT NULL,
    required_permission varchar(64) NOT NULL DEFAULT 'SHOP_READ',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_merchant_notifications_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX ix_merchant_notifications_shop_created
    ON merchant_notifications (shop_id, created_at DESC, id DESC);

CREATE INDEX ix_merchant_notifications_created
    ON merchant_notifications (created_at);

-- One shop lifecycle transition at a given event time must produce one row even if dedupe_key formats differ.
CREATE UNIQUE INDEX uq_merchant_notifications_lifecycle_event
    ON merchant_notifications (shop_id, type, created_at)
    WHERE type IN ('SHOP_APPROVED', 'SHOP_REJECTED', 'SHOP_SUSPENDED', 'SHOP_RESTORED');

COMMENT ON TABLE merchant_notifications IS
    '[Merchant] Durable shop-scoped lifecycle inbox events for the merchant header bell; separate from admin_notifications';

-- epoch millis from date_trunc('milliseconds') matches Java Instant.truncatedTo(MILLIS).toEpochMilli().
-- Backfill shop approvals still within rolling 7 days (idempotent; created_at = published_at).
INSERT INTO merchant_notifications (id, shop_id, type, entity_type, entity_id, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       s.id,
       'SHOP_APPROVED',
       'SHOP',
       s.shop_no::text,
       'SHOP_APPROVED:shop:' || s.shop_no || ':' || (EXTRACT(EPOCH FROM date_trunc('milliseconds', s.published_at)) * 1000)::bigint,
       'SHOP_READ',
       date_trunc('milliseconds', s.published_at)
FROM shops s
WHERE s.published_at IS NOT NULL
  AND s.published_at >= now() - interval '7 days'
  AND s.status IN ('APPROVED', 'SUSPENDED', 'ACTIVE')
ON CONFLICT (dedupe_key) DO NOTHING;

INSERT INTO merchant_notifications (id, shop_id, type, entity_type, entity_id, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       s.id,
       'SHOP_SUSPENDED',
       'SHOP',
       s.shop_no::text,
       'SHOP_SUSPENDED:shop:' || s.shop_no || ':' || (EXTRACT(EPOCH FROM date_trunc('milliseconds', s.suspended_at)) * 1000)::bigint,
       'SHOP_READ',
       date_trunc('milliseconds', s.suspended_at)
FROM shops s
WHERE s.suspended_at IS NOT NULL
  AND s.suspended_at >= now() - interval '7 days'
  AND s.status = 'SUSPENDED'
ON CONFLICT (dedupe_key) DO NOTHING;

INSERT INTO merchant_notifications (id, shop_id, type, entity_type, entity_id, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       s.id,
       'SHOP_RESTORED',
       'SHOP',
       s.shop_no::text,
       'SHOP_RESTORED:shop:' || s.shop_no || ':' || (EXTRACT(EPOCH FROM date_trunc('milliseconds', s.restored_at)) * 1000)::bigint,
       'SHOP_READ',
       date_trunc('milliseconds', s.restored_at)
FROM shops s
WHERE s.restored_at IS NOT NULL
  AND s.restored_at >= now() - interval '7 days'
  AND s.status IN ('APPROVED', 'ACTIVE')
ON CONFLICT (dedupe_key) DO NOTHING;
