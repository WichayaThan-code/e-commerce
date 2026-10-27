-- V43: Product configurator (V42) -> cart / order lines. Additive only; NULL = normal (non-configured) line.
-- configuration = server-quoted snapshot {version, selections, lines[{stepId, optionId, label, priceDelta}], basePrice, configDelta, currency}
-- config_hash   = SHA-256 hex from the configurator quote; part of the cart-line identity.

ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS configuration JSONB;
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS config_hash VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS configuration JSONB;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS config_hash VARCHAR(64);

-- Identity: plain lines keep UNIQUE(cart_id, sku_id) (partial, config_hash IS NULL); configured lines are unique per (cart_id, sku_id, config_hash).
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS uq_cart_items_cart_id_sku_id;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_items_cart_sku_plain ON cart_items (cart_id, sku_id) WHERE config_hash IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_items_cart_sku_config ON cart_items (cart_id, sku_id, config_hash) WHERE config_hash IS NOT NULL;

COMMENT ON COLUMN cart_items.configuration IS 'V43: configurator snapshot (server quote); NULL = normal line';
COMMENT ON COLUMN cart_items.config_hash IS 'V43: configurator hash; NULL = normal line; part of line identity';
COMMENT ON COLUMN order_items.configuration IS 'V43: configurator snapshot copied from the cart at checkout (re-quoted); NULL = normal item';
COMMENT ON COLUMN order_items.config_hash IS 'V43: configurator hash; NULL = normal item';
