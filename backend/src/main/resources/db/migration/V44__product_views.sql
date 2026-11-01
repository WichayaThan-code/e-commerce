-- V44: product view history for recommendations / recently viewed (additive only).
-- Rows store the viewer as user_id (signed-in) or a SHA-256 hash of an anonymous browser session id; raw session ids are never stored.
CREATE TABLE IF NOT EXISTS product_views (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    session_hash VARCHAR(64) NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_views_viewer CHECK (user_id IS NOT NULL OR session_hash IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_product_views_user_time ON product_views (user_id, viewed_at DESC);
CREATE INDEX IF NOT EXISTS idx_product_views_session_time ON product_views (session_hash, viewed_at DESC);
CREATE INDEX IF NOT EXISTS idx_product_views_product ON product_views (product_id);
COMMENT ON TABLE product_views IS 'Product page views for personalized recommendations and recently-viewed (deduped per viewer per 30 min).';
COMMENT ON COLUMN product_views.session_hash IS 'SHA-256 hex of the anonymous X-View-Session id; never the raw id.';
