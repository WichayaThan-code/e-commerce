-- V45: buyer favourites (wishlist), additive only.
CREATE TABLE IF NOT EXISTS user_favorites (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_user_favorites_user_time ON user_favorites (user_id, created_at DESC);
COMMENT ON TABLE user_favorites IS 'Signed-in buyer favourites; exposed only to the owner via /api/me/favorites (no user ids in responses).';
