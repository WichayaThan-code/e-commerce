ALTER TABLE shops ADD COLUMN IF NOT EXISTS category_id BIGINT;

UPDATE shops AS shop
SET category_id = category.category_id
FROM product_categories AS category
WHERE shop.category_id IS NULL
  AND shop.category IS NOT NULL
  AND btrim(shop.category) = category.name_th;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'shops_category_id_fkey') THEN
        ALTER TABLE shops
            ADD CONSTRAINT shops_category_id_fkey
            FOREIGN KEY (category_id) REFERENCES product_categories (category_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shops_category_id ON shops (category_id);
