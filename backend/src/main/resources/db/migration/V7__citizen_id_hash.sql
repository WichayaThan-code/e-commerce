ALTER TABLE merchant_applications
    ADD COLUMN IF NOT EXISTS citizen_id_hash VARCHAR(64);

ALTER TABLE shop_owners
    ADD COLUMN IF NOT EXISTS citizen_id_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_applications_citizen_id_hash
    ON merchant_applications (citizen_id_hash)
    WHERE citizen_id_hash IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_owners_citizen_id_hash
    ON shop_owners (citizen_id_hash)
    WHERE citizen_id_hash IS NOT NULL;
