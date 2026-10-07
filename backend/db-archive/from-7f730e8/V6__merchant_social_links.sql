ALTER TABLE merchant_applications
    ADD COLUMN line_id VARCHAR(500),
    ADD COLUMN facebook_url VARCHAR(500),
    ADD COLUMN instagram_url VARCHAR(500),
    ADD COLUMN tiktok_url VARCHAR(500);

ALTER TABLE shops
    ADD COLUMN line_id VARCHAR(500),
    ADD COLUMN facebook_url VARCHAR(500),
    ADD COLUMN instagram_url VARCHAR(500),
    ADD COLUMN tiktok_url VARCHAR(500);
