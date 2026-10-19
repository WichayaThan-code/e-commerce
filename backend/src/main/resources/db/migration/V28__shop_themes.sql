CREATE TABLE shop_themes (
    shop_id UUID PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
    template_key VARCHAR(30) NOT NULL DEFAULT 'GENERAL'
        CHECK (template_key IN ('GENERAL', 'FASHION', 'FOOD_BEVERAGE')),
    config JSONB NOT NULL,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by UUID
);

CREATE TRIGGER trg_shop_themes_modified_date_time
    BEFORE UPDATE ON shop_themes
    FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

-- Seed GENERAL theme for every existing shop (cover stays on shops.cover_path).
INSERT INTO shop_themes (shop_id, template_key, config, created_date_time, modified_date_time)
SELECT
    s.id,
    'GENERAL',
    '{
      "colors": {
        "primary": "#087bf5",
        "secondary": "#08245d",
        "background": "#ffffff",
        "text": "#132b50"
      },
      "font": "NOTO_SANS_THAI",
      "hero": {
        "layout": "FULL",
        "title": "",
        "subtitle": "",
        "ctaLabel": "",
        "ctaLink": ""
      },
      "sections": [
        { "key": "HERO", "enabled": true, "variant": "FULL" },
        { "key": "INTRO", "enabled": true, "variant": null },
        { "key": "FEATURED", "enabled": true, "variant": "GRID" },
        { "key": "CATALOG", "enabled": true, "variant": "GRID" },
        { "key": "HOURS", "enabled": false, "variant": null }
      ]
    }'::jsonb,
    now(),
    now()
FROM shops s
WHERE NOT EXISTS (
    SELECT 1 FROM shop_themes t WHERE t.shop_id = s.id
);
