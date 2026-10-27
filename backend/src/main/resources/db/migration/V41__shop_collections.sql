-- V41: Shop collections (หมวดสินค้าที่ร้านคัดเอง) — a merchant-curated, ordered list of the shop's own products.
-- Limits (app.merchant.collections.*): 50 collections per shop, 500 products per collection (enforced by the service).

CREATE TABLE shop_collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    slug VARCHAR(80) NOT NULL,
    name_th VARCHAR(120) NOT NULL,
    name_en VARCHAR(120),
    description TEXT,
    cover_media_id UUID REFERENCES shop_theme_media(id) ON DELETE SET NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT uq_shop_collections_shop_slug UNIQUE (shop_id, slug),
    CONSTRAINT ck_shop_collections_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE INDEX IF NOT EXISTS ix_shop_collections_shop_sort ON shop_collections (shop_id, sort_order);
CREATE INDEX IF NOT EXISTS ix_shop_collections_shop_published ON shop_collections (shop_id, sort_order) WHERE is_published;

CREATE TABLE shop_collection_products (
    collection_id UUID NOT NULL REFERENCES shop_collections(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, product_id)
);

CREATE INDEX IF NOT EXISTS ix_shop_collection_products_sort ON shop_collection_products (collection_id, sort_order);
CREATE INDEX IF NOT EXISTS ix_shop_collection_products_product ON shop_collection_products (product_id);

DROP TRIGGER IF EXISTS trg_shop_collections_modified_date_time ON shop_collections;
CREATE TRIGGER trg_shop_collections_modified_date_time
    BEFORE UPDATE ON shop_collections
    FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

COMMENT ON TABLE shop_collections IS
    '[Merchant] Curated product collections per shop; only is_published rows are visible on the storefront';
COMMENT ON COLUMN shop_collections.cover_media_id IS
    'Optional IMAGE from shop_theme_media of the same shop (checked by the service)';
COMMENT ON TABLE shop_collection_products IS
    '[Merchant] Ordered products of a collection; product must belong to the same shop (checked by the service)';
