ALTER TABLE merchant_applications DROP CONSTRAINT IF EXISTS merchant_applications_user_id_key;

DROP INDEX IF EXISTS uq_merchant_applications_citizen_id_hash;
DROP INDEX IF EXISTS uq_shop_owners_citizen_id_hash;

ALTER TABLE shops ADD COLUMN IF NOT EXISTS cover_path VARCHAR(500);
