-- V37: Merchant header notification read-state (derive-on-read feed; no event table).

CREATE TABLE merchant_notification_cursor (
    shop_id uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    read_all_at timestamptz NOT NULL DEFAULT 'epoch',
    PRIMARY KEY (shop_id, user_id)
);

CREATE TABLE merchant_notification_reads (
    shop_id uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    item_key varchar(120) NOT NULL,
    read_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (shop_id, user_id, item_key)
);

CREATE INDEX IF NOT EXISTS ix_merchant_notification_reads_shop_user
    ON merchant_notification_reads (shop_id, user_id);

-- Speeds feed queries that filter history by order + time (V1 only had order_id).
CREATE INDEX IF NOT EXISTS ix_order_status_history_order_created
    ON order_status_history (order_id, created_date_time);

COMMENT ON TABLE merchant_notification_cursor IS
    '[Merchant] Per-user mark-all-read watermark for derive-on-read notification feed';
COMMENT ON TABLE merchant_notification_reads IS
    '[Merchant] Per-user per-item read markers; pruned when read_all_at advances';
