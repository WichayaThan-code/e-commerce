-- V40: Per-shop theme media library (AG2) — images / MP4 videos / WOFF2 fonts for CUSTOM themes.
-- Files live on disk under {storage-root}/theme-media/{shop uuid}/{uuid}.{ext}; this table is the index + quota ledger.

CREATE TABLE shop_theme_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    kind VARCHAR(10) NOT NULL,
    storage_path TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    sha256 VARCHAR(64) NOT NULL,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_shop_theme_media_kind CHECK (kind IN ('IMAGE', 'VIDEO', 'FONT')),
    CONSTRAINT ck_shop_theme_media_size CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS ix_shop_theme_media_shop_kind ON shop_theme_media (shop_id, kind);

-- Partial unique: the same bytes can be re-uploaded after the earlier copy was soft-deleted.
CREATE UNIQUE INDEX IF NOT EXISTS uq_shop_theme_media_shop_sha256_active
    ON shop_theme_media (shop_id, sha256) WHERE deleted_at IS NULL;

DROP TRIGGER IF EXISTS trg_shop_theme_media_modified_date_time ON shop_theme_media;
CREATE TRIGGER trg_shop_theme_media_modified_date_time
    BEFORE UPDATE ON shop_theme_media
    FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

COMMENT ON TABLE shop_theme_media IS
    '[Merchant] CUSTOM theme media library per shop (IMAGE/VIDEO/FONT); soft-deleted via deleted_at';
COMMENT ON COLUMN shop_theme_media.storage_path IS
    'Absolute server path under storage-root/theme-media/{shop uuid}/; never derived from the client filename';
