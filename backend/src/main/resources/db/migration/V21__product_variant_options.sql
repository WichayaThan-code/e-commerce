-- Variant products: option groups (e.g. Color, Size), their values (Black, White / S, M, L),
-- the variant-to-value mapping (one value per group for each SKU) and a per-variant image.
-- Additive only: no existing row is changed. product_skus.option_values / option_label stay as the
-- denormalised snapshot (kept on soft-deleted SKUs as history).

CREATE TABLE IF NOT EXISTS product_option_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT uq_product_option_groups_name UNIQUE (product_id, name)
);

CREATE INDEX IF NOT EXISTS ix_product_option_groups_product_id ON product_option_groups (product_id);

CREATE TABLE IF NOT EXISTS product_option_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    option_group_id UUID NOT NULL REFERENCES product_option_groups (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    option_value VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT uq_product_option_values_value UNIQUE (option_group_id, option_value)
);

CREATE INDEX IF NOT EXISTS ix_product_option_values_product_id ON product_option_values (product_id);

CREATE TABLE IF NOT EXISTS product_sku_option_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku_id UUID NOT NULL REFERENCES product_skus (id) ON DELETE CASCADE,
    option_group_id UUID NOT NULL REFERENCES product_option_groups (id) ON DELETE CASCADE,
    option_value_id UUID NOT NULL REFERENCES product_option_values (id) ON DELETE CASCADE,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT uq_product_sku_option_values_group UNIQUE (sku_id, option_group_id)
);

CREATE INDEX IF NOT EXISTS ix_product_sku_option_values_value ON product_sku_option_values (option_value_id);

ALTER TABLE product_skus
    ADD COLUMN IF NOT EXISTS image_id UUID REFERENCES product_images (id) ON DELETE SET NULL;

COMMENT ON TABLE product_option_groups IS '[Catalog] กลุ่มตัวเลือกสินค้า เช่น สี ไซซ์ (1-3 กลุ่มต่อสินค้า)';
COMMENT ON TABLE product_option_values IS '[Catalog] ค่าตัวเลือกในกลุ่ม เช่น ดำ ขาว / S M L';
COMMENT ON TABLE product_sku_option_values IS '[Catalog] การจับคู่ SKU (variant) กับค่าตัวเลือก 1 ค่าต่อกลุ่ม';
COMMENT ON COLUMN product_skus.image_id IS 'รูปของ variant (ต้องเป็นรูปของสินค้าเดียวกัน)';

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['product_option_groups', 'product_option_values', 'product_sku_option_values']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', 'trg_' || table_name || '_set_modified_date_time', table_name);
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_modified_date_time()',
            'trg_' || table_name || '_set_modified_date_time',
            table_name
        );
    END LOOP;
END $$;
