-- V38: OTOP marketplace category + product review read index.
-- product_reviews itself already exists since V1 (rating 1..5, UNIQUE order_item_id, status PUBLISHED/HIDDEN/...),
-- so this migration only adds the public-listing index and the idempotent OTOP category row.

CREATE INDEX IF NOT EXISTS ix_product_reviews_product_status
    ON product_reviews (product_id, status);

INSERT INTO hub_categories (id, slug, name_th, name_en, name_zh, sort_order, is_active, created_date_time, modified_date_time)
VALUES (gen_random_uuid(), 'otop-products', 'สินค้า OTOP', 'OTOP Products', 'OTOP 一村一品', 14, true, now(), now())
ON CONFLICT (slug) DO NOTHING;

COMMENT ON TABLE product_reviews IS
    '[Buyer] One review per delivered/completed order item; public list shows masked reviewer names only';
