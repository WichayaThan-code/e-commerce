-- V52: Durable admin in-app notifications (write-on-event) + per-user read state.

CREATE TABLE admin_notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type varchar(64) NOT NULL,
    entity_type varchar(32) NOT NULL,
    entity_id varchar(64) NOT NULL,
    shop_id bigint NULL,
    shop_name varchar(255) NULL,
    dedupe_key varchar(191) NOT NULL,
    required_permission varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_admin_notifications_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX ix_admin_notifications_created
    ON admin_notifications (created_at DESC, id DESC);

CREATE INDEX ix_admin_notifications_permission_created
    ON admin_notifications (required_permission, created_at DESC);

CREATE TABLE admin_notification_reads (
    notification_id uuid NOT NULL REFERENCES admin_notifications(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, user_id)
);

CREATE INDEX ix_admin_notification_reads_user
    ON admin_notification_reads (user_id, read_at DESC);

COMMENT ON TABLE admin_notifications IS
    '[Admin] Durable inbox events for the admin header bell; one row per business event';
COMMENT ON TABLE admin_notification_reads IS
    '[Admin] Per-admin read markers; Admin A reading does not mark Admin B';

-- Backfill open shop applications still waiting for review (idempotent).
INSERT INTO admin_notifications (id, type, entity_type, entity_id, shop_id, shop_name, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       'SHOP_APPLICATION_SUBMITTED',
       'SHOP',
       s.shop_no::text,
       s.shop_no,
       s.name,
       'SHOP_APPLICATION_SUBMITTED:shop:' || s.shop_no,
       'PLATFORM_APPLICATION_READ',
       COALESCE(s.created_date_time, now())
FROM shops s
WHERE s.status = 'PENDING_REVIEW'
ON CONFLICT (dedupe_key) DO NOTHING;

-- Backfill pending reinstatement appeals (idempotent; one notification per request id).
INSERT INTO admin_notifications (id, type, entity_type, entity_id, shop_id, shop_name, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       'SHOP_REINSTATEMENT_REQUESTED',
       'SHOP',
       s.shop_no::text,
       s.shop_no,
       s.name,
       'SHOP_REINSTATEMENT_REQUESTED:request:' || r.id,
       'PLATFORM_SHOP_RESTORE',
       COALESCE(r.created_date_time, now())
FROM shop_reinstatement_requests r
JOIN shops s ON s.id = r.shop_id
WHERE r.status = 'PENDING'
ON CONFLICT (dedupe_key) DO NOTHING;

-- Backfill open refund disputes still needing admin resolve (idempotent; one row per refund).
INSERT INTO admin_notifications (id, type, entity_type, entity_id, shop_id, shop_name, dedupe_key, required_permission, created_at)
SELECT gen_random_uuid(),
       'REFUND_DISPUTED',
       'ORDER',
       rf.order_id::text,
       s.shop_no,
       s.name,
       'REFUND_DISPUTED:refund:' || rf.id,
       'PLATFORM_SHOP_SUSPEND',
       COALESCE(rf.buyer_ack_at, rf.modified_date_time, rf.created_date_time, now())
FROM refunds rf
LEFT JOIN shops s ON s.id = rf.shop_id
WHERE rf.status = 'DISPUTED'
ON CONFLICT (dedupe_key) DO NOTHING;
