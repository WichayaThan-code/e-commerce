CREATE TABLE IF NOT EXISTS owners (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    owner_type VARCHAR(30) NOT NULL,
    title VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    citizen_id_hash VARCHAR(64),
    citizen_id_encrypted TEXT NOT NULL,
    birth_date DATE NOT NULL,
    nationality VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    house_no VARCHAR(50) NOT NULL,
    village_no VARCHAR(50),
    building VARCHAR(255),
    alley VARCHAR(255),
    road VARCHAR(255),
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    subdistrict VARCHAR(100) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    bank_account_encrypted TEXT NOT NULL,
    bank_account_name VARCHAR(255) NOT NULL,
    sensitive_review VARCHAR(30) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_owners_citizen_id_hash
    ON owners (citizen_id_hash)
    WHERE citizen_id_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_owners_user_id ON owners (user_id);

ALTER TABLE shops ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE merchant_applications ADD COLUMN IF NOT EXISTS owner_id UUID;

CREATE TABLE IF NOT EXISTS owner_audit_log (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES owners (id),
    user_id UUID NOT NULL REFERENCES users (id),
    field_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_owner_audit_owner_id ON owner_audit_log (owner_id);

DO $$
BEGIN
    IF to_regclass('public.shop_owners') IS NOT NULL THEN
        INSERT INTO owners (
            id, user_id, owner_type, title, first_name, last_name, citizen_id_hash, citizen_id_encrypted,
            birth_date, nationality, phone, email, house_no, village_no, building, alley, road,
            province, district, subdistrict, postal_code, bank_name, bank_account_encrypted, bank_account_name,
            sensitive_review, created_at, updated_at
        )
        SELECT DISTINCT ON (COALESCE(so.citizen_id_hash, so.id::text))
            so.id, s.user_id, so.owner_type, so.title, so.first_name, so.last_name, so.citizen_id_hash, so.citizen_id_encrypted,
            so.birth_date, so.nationality, so.phone, so.email, so.house_no, so.village_no, so.building, so.alley, so.road,
            so.province, so.district, so.subdistrict, so.postal_code, so.bank_name, so.bank_account_encrypted, so.bank_account_name,
            'NONE', so.created_at, so.created_at
        FROM shop_owners so
        JOIN shops s ON s.shop_id = so.shop_id
        WHERE NOT EXISTS (
            SELECT 1 FROM owners o
            WHERE o.id = so.id
               OR (so.citizen_id_hash IS NOT NULL AND o.citizen_id_hash = so.citizen_id_hash)
        )
        ORDER BY COALESCE(so.citizen_id_hash, so.id::text), so.created_at;

        UPDATE shops AS shop
        SET owner_id = owner.id
        FROM shop_owners AS source
        JOIN owners AS owner ON (
            (source.citizen_id_hash IS NOT NULL AND owner.citizen_id_hash = source.citizen_id_hash)
            OR (source.citizen_id_hash IS NULL AND owner.id = source.id)
        )
        WHERE shop.shop_id = source.shop_id
          AND shop.owner_id IS NULL;
    END IF;
END $$;

UPDATE merchant_applications AS application
SET owner_id = shop.owner_id
FROM shops AS shop
WHERE shop.application_id = application.id
  AND application.owner_id IS NULL
  AND shop.owner_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM shops WHERE owner_id IS NULL) THEN
        RAISE EXCEPTION 'shops.owner_id is still null after migrating shop_owners';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'shops' AND column_name = 'owner_id' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE shops ALTER COLUMN owner_id SET NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'shops_owner_id_fkey') THEN
        ALTER TABLE shops
            ADD CONSTRAINT shops_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES owners (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'merchant_applications_owner_id_fkey') THEN
        ALTER TABLE merchant_applications
            ADD CONSTRAINT merchant_applications_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES owners (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shops_owner_id ON shops (owner_id);
