-- Shop map pin: optional lat/lng set by merchant (drag pin on My Store).
ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS latitude double precision,
    ADD COLUMN IF NOT EXISTS longitude double precision;
