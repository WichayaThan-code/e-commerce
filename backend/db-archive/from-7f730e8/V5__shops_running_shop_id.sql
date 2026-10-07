-- Replace shops.id (UUID) with shops.shop_id, a running number primary key.
-- Existing shops keep their rows; shop_owners.shop_id is rewritten to the same number.
-- Rollback: restore a database backup taken before this migration. The UUID key is not recoverable after this step.

ALTER TABLE shop_owners DROP CONSTRAINT IF EXISTS shop_owners_shop_id_fkey;

ALTER TABLE shops ADD COLUMN shop_id BIGINT;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS running_no
    FROM shops
)
UPDATE shops AS shop
SET shop_id = numbered.running_no
FROM numbered
WHERE shop.id = numbered.id;

ALTER TABLE shop_owners ADD COLUMN new_shop_id BIGINT;

UPDATE shop_owners AS owner
SET new_shop_id = shop.shop_id
FROM shops AS shop
WHERE owner.shop_id = shop.id;

ALTER TABLE shops DROP CONSTRAINT shops_pkey;
ALTER TABLE shops DROP COLUMN id;
ALTER TABLE shops ALTER COLUMN shop_id SET NOT NULL;
ALTER TABLE shops ADD PRIMARY KEY (shop_id);

CREATE SEQUENCE shops_shop_id_seq;
ALTER SEQUENCE shops_shop_id_seq OWNED BY shops.shop_id;
ALTER TABLE shops ALTER COLUMN shop_id SET DEFAULT nextval('shops_shop_id_seq');
SELECT setval(
    'shops_shop_id_seq',
    GREATEST(COALESCE((SELECT MAX(shop_id) FROM shops), 1), 1),
    EXISTS (SELECT 1 FROM shops)
);

ALTER TABLE shop_owners DROP COLUMN shop_id;
ALTER TABLE shop_owners RENAME COLUMN new_shop_id TO shop_id;
ALTER TABLE shop_owners ALTER COLUMN shop_id SET NOT NULL;
ALTER TABLE shop_owners ADD CONSTRAINT uq_shop_owners_shop_id UNIQUE (shop_id);
ALTER TABLE shop_owners
    ADD CONSTRAINT shop_owners_shop_id_fkey
    FOREIGN KEY (shop_id) REFERENCES shops (shop_id) ON DELETE CASCADE;
