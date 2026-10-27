-- V42: Product configurator (ตัวออกแบบสินค้าเอง) schema per product — behind app.features.configurator.enabled (default false).
-- Only the schema lives here; cart/order snapshots of a configuration are Supitcha's part (see AB2_handoff_note_supitcha.md).

CREATE TABLE product_configurators (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schema JSONB NOT NULL DEFAULT '{"steps":[]}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT ck_product_configurators_version CHECK (version > 0)
);

DROP TRIGGER IF EXISTS trg_product_configurators_modified_date_time ON product_configurators;
CREATE TRIGGER trg_product_configurators_modified_date_time
    BEFORE UPDATE ON product_configurators
    FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

COMMENT ON TABLE product_configurators IS
    '[Merchant] Configurator steps (SINGLE / MULTI / TEXT) with per-option price delta (THB, 2dp), image and availability';
COMMENT ON COLUMN product_configurators.version IS
    'Incremented on every save; quotes carry it so a stale configuration can be rejected';
