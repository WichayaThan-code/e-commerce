UPDATE shops AS shop
SET hub_category_id = category.id
FROM hub_categories AS category
WHERE shop.hub_category_id IS NULL
  AND shop.category IS NOT NULL
  AND btrim(shop.category) = category.name_th;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS category_id UUID REFERENCES hub_categories (id),
    ADD COLUMN IF NOT EXISTS name_th VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_zh VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description_th TEXT,
    ADD COLUMN IF NOT EXISTS description_en TEXT,
    ADD COLUMN IF NOT EXISTS description_zh TEXT;

UPDATE products
SET name_th = name
WHERE name_th IS NULL;

UPDATE products
SET description_th = description
WHERE description_th IS NULL;

ALTER TABLE products
    ALTER COLUMN name_th SET NOT NULL,
    ALTER COLUMN name DROP NOT NULL;

UPDATE product_skus
SET option_values = '{}'::jsonb
WHERE option_values IS NULL;

ALTER TABLE product_skus
    ALTER COLUMN option_values SET NOT NULL;

ALTER TABLE product_images
    ADD COLUMN IF NOT EXISTS storage_path VARCHAR(700);

UPDATE product_images AS image
SET storage_path = media.storage_key
FROM media_files AS media
WHERE image.storage_path IS NULL
  AND image.media_file_id = media.id;

ALTER TABLE product_images
    ALTER COLUMN storage_path SET NOT NULL,
    ALTER COLUMN media_file_id DROP NOT NULL;

ALTER TABLE inventory_ledger
    ALTER COLUMN reference_id TYPE VARCHAR(64) USING reference_id::text,
    ALTER COLUMN idempotency_key TYPE VARCHAR(100);
