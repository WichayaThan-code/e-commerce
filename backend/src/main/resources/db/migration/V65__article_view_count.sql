-- Public article view counter (replaces reading-time display on the storefront).
ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN blog_posts.view_count IS 'Cumulative public article page views; incremented on GET /api/articles/{slug}.';
