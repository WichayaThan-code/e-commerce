ALTER TABLE shop_members
    DROP CONSTRAINT IF EXISTS ck_shop_members_role;

ALTER TABLE shop_members
    ADD CONSTRAINT ck_shop_members_role CHECK (
        role IN ('STORE_OWNER', 'STORE_ADMIN', 'STORE_MANAGER', 'STORE_SALES', 'STORE_WAREHOUSE', 'STORE_VIEWER')
        OR role ~ '^STORE_CUSTOM_[A-F0-9]{12}$'
    );
