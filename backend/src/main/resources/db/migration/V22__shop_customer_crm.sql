-- V22: shop customer records (CRM/OMS customers of a merchant shop).
-- Reuses V1 shop_customers; adds normalized phone/email for duplicate detection, merge tracking,
-- multiple addresses and tags. Additive only: no existing data is deleted or rewritten except the
-- new normalized columns, which are backfilled from phone/email.

-- 1. Wider source / status vocabularies (old values stay valid).
ALTER TABLE shop_customers DROP CONSTRAINT IF EXISTS ck_shop_customers_source;
ALTER TABLE shop_customers ADD CONSTRAINT ck_shop_customers_source CHECK (source IN (
    'STOREFRONT', 'MANUAL', 'OMS', 'IMPORT',
    'LINE', 'FACEBOOK', 'INSTAGRAM', 'SHOPEE', 'LAZADA', 'TIKTOK', 'WALK_IN', 'WEBSITE', 'REFERRAL', 'OTHER'));
ALTER TABLE shop_customers DROP CONSTRAINT IF EXISTS ck_shop_customers_status;
ALTER TABLE shop_customers ADD CONSTRAINT ck_shop_customers_status CHECK (status IN (
    'ACTIVE', 'INACTIVE', 'BLOCKED', 'ARCHIVED', 'MERGED'));

-- 2. Normalized contact keys + merge tracking.
ALTER TABLE shop_customers ADD COLUMN IF NOT EXISTS phone_normalized varchar(20);
ALTER TABLE shop_customers ADD COLUMN IF NOT EXISTS email_normalized varchar(255);
ALTER TABLE shop_customers ADD COLUMN IF NOT EXISTS merged_into_id uuid;
ALTER TABLE shop_customers ADD COLUMN IF NOT EXISTS merged_at timestamp with time zone;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_shop_customers_merged_into_id' AND conrelid = 'shop_customers'::regclass) THEN
        ALTER TABLE shop_customers ADD CONSTRAINT fk_shop_customers_merged_into_id
            FOREIGN KEY (merged_into_id) REFERENCES shop_customers(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_shop_customers_merged' AND conrelid = 'shop_customers'::regclass) THEN
        ALTER TABLE shop_customers ADD CONSTRAINT ck_shop_customers_merged
            CHECK ((status = 'MERGED') = (merged_into_id IS NOT NULL));
    END IF;
END $$;

UPDATE shop_customers
   SET email_normalized = NULLIF(lower(trim(email)), '')
 WHERE email IS NOT NULL AND email_normalized IS NULL;
UPDATE shop_customers
   SET phone_normalized = CASE
        WHEN d ~ '^66[0-9]{8,9}$' THEN '0' || substr(d, 3)
        WHEN d = '' THEN NULL
        ELSE d END
  FROM (SELECT id AS cid, regexp_replace(phone, '[^0-9]', '', 'g') AS d FROM shop_customers WHERE phone IS NOT NULL) n
 WHERE shop_customers.id = n.cid AND phone_normalized IS NULL;

CREATE INDEX IF NOT EXISTS ix_shop_customers_shop_id_phone_normalized ON shop_customers (shop_id, phone_normalized) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_customers_shop_id_email_normalized ON shop_customers (shop_id, email_normalized) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_customers_merged_into_id ON shop_customers (merged_into_id);
CREATE INDEX IF NOT EXISTS ix_orders_shop_customer_id ON orders (shop_customer_id);

COMMENT ON COLUMN shop_customers.source IS 'STOREFRONT|MANUAL|OMS|IMPORT|LINE|FACEBOOK|INSTAGRAM|SHOPEE|LAZADA|TIKTOK|WALK_IN|WEBSITE|REFERRAL|OTHER [CHECK enum: shop_customer_source] (V22)';
COMMENT ON COLUMN shop_customers.status IS 'ACTIVE|INACTIVE|BLOCKED|ARCHIVED|MERGED [CHECK enum: shop_customer_status] (V22)';
COMMENT ON COLUMN shop_customers.phone_normalized IS 'duplicate key: digits only, +66/66 -> 0 (V22)';
COMMENT ON COLUMN shop_customers.email_normalized IS 'duplicate key: lower(trim(email)) (V22)';
COMMENT ON COLUMN shop_customers.merged_into_id IS 'status MERGED -> surviving customer (soft merge, V22)';

-- 3. Multiple addresses per customer (Thai address structure).
CREATE TABLE IF NOT EXISTS shop_customer_addresses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    label varchar(50),
    recipient_name varchar(150) NOT NULL,
    phone varchar(30),
    address_line1 varchar(255) NOT NULL,
    address_line2 varchar(255),
    subdistrict varchar(100),
    district varchar(100) NOT NULL,
    province varchar(100) NOT NULL,
    postal_code varchar(10) NOT NULL,
    country_code char(2) DEFAULT 'TH' NOT NULL,
    is_default_shipping boolean DEFAULT false NOT NULL,
    is_default_billing boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_shop_customer_addresses PRIMARY KEY (id),
    CONSTRAINT fk_shop_customer_addresses_customer_id FOREIGN KEY (customer_id) REFERENCES shop_customers(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_shop_customer_addresses_customer_id ON shop_customer_addresses (customer_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_customer_addresses_default_shipping ON shop_customer_addresses (customer_id) WHERE is_default_shipping AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_customer_addresses_default_billing ON shop_customer_addresses (customer_id) WHERE is_default_billing AND deleted_at IS NULL;
DROP TRIGGER IF EXISTS trg_shop_customer_addresses_modified_date_time ON shop_customer_addresses;
CREATE TRIGGER trg_shop_customer_addresses_modified_date_time BEFORE UPDATE ON shop_customer_addresses FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
COMMENT ON TABLE shop_customer_addresses IS '[Shop & Team] ที่อยู่ลูกค้าระดับร้าน (หลายรายการ, PII — CUSTOMER_PII_READ) (V22)';

-- 4. Tags.
CREATE TABLE IF NOT EXISTS shop_customer_tags (
    customer_id uuid NOT NULL,
    tag varchar(50) NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_shop_customer_tags PRIMARY KEY (customer_id, tag),
    CONSTRAINT fk_shop_customer_tags_customer_id FOREIGN KEY (customer_id) REFERENCES shop_customers(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_shop_customer_tags_tag ON shop_customer_tags (tag);
COMMENT ON TABLE shop_customer_tags IS '[Shop & Team] แท็กลูกค้า (เก็บแบบ lower-case trim) (V22)';
