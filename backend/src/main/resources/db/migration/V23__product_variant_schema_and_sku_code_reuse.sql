-- Team migrations V4__catalog_columns_and_translations and V5__product_variant_schema were numbered below
-- versions already applied on existing databases (V14-V22), so Flyway would refuse to start.
-- V4 duplicated V16 (content_translations) + V17 (catalog columns) and is dropped; the parts of V4/V5
-- not covered elsewhere are applied here idempotently so this runs on both existing and fresh databases.
-- product_skus.image_id comes from V21.

-- SKU codes: a soft-deleted SKU no longer reserves its code (partial unique index replaces the full constraint)
ALTER TABLE product_skus DROP CONSTRAINT IF EXISTS uq_product_skus_shop_id_sku_code;
ALTER TABLE product_skus DROP CONSTRAINT IF EXISTS uq_product_skus_shop_code;

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_skus_shop_sku_active
    ON product_skus (shop_id, sku_code)
    WHERE deleted_at IS NULL;

-- Option schema snapshot that the merchant product form (VariantEditor) reads: [{"name":..,"values":[..]}]
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS option_schema JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Backfill VARIABLE products from their live SKUs' option_values
UPDATE products AS p
SET option_schema = sub.schema
FROM (
    SELECT g.product_id,
           jsonb_agg(jsonb_build_object('name', g.name, 'values', g.vals) ORDER BY g.name) AS schema
    FROM (
        SELECT s.product_id, kv.key AS name, jsonb_agg(DISTINCT kv.value) AS vals
        FROM product_skus AS s
        CROSS JOIN LATERAL jsonb_each_text(COALESCE(s.option_values, '{}'::jsonb)) AS kv
        WHERE s.deleted_at IS NULL
        GROUP BY s.product_id, kv.key
    ) AS g
    GROUP BY g.product_id
) AS sub
WHERE p.id = sub.product_id
  AND p.product_type = 'VARIABLE'
  AND p.option_schema = '[]'::jsonb;

CREATE INDEX IF NOT EXISTS ix_products_category_id ON products (category_id);
