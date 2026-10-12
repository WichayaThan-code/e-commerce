-- Variant schema and reusable SKU codes.
-- Partial unique index replaces the full (shop_id, sku_code) constraint so a soft-deleted row no longer reserves the code.

ALTER TABLE product_skus DROP CONSTRAINT uq_product_skus_shop_id_sku_code;

CREATE UNIQUE INDEX uq_product_skus_shop_sku_active
    ON product_skus (shop_id, sku_code)
    WHERE deleted_at IS NULL;

ALTER TABLE product_skus
    ADD COLUMN image_id uuid;

ALTER TABLE product_skus
    ADD CONSTRAINT fk_product_skus_image_id
    FOREIGN KEY (image_id) REFERENCES product_images (id) ON DELETE SET NULL;

ALTER TABLE products
    ADD COLUMN option_schema jsonb NOT NULL DEFAULT '[]'::jsonb;
