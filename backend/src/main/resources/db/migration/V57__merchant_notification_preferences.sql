-- V57: Per-user + per-shop merchant notification preferences (in-app channel only).
-- Preferences hide optional categories from the bell feed/badge without deleting business events or read state.

CREATE TABLE merchant_notification_preferences (
    shop_id uuid NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preferences_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    version bigint NOT NULL DEFAULT 1,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (shop_id, user_id)
);

CREATE INDEX ix_merchant_notification_preferences_user
    ON merchant_notification_preferences (user_id);

COMMENT ON TABLE merchant_notification_preferences IS
    '[Merchant] Personal in-app notification category preferences scoped to authenticated user + shop; never team-wide';
