-- V39: Allow CUSTOM shop theme template (Phase A free tokens; config stays jsonb).

ALTER TABLE shop_themes DROP CONSTRAINT IF EXISTS shop_themes_template_key_check;
ALTER TABLE shop_themes ADD CONSTRAINT shop_themes_template_key_check
    CHECK (template_key IN ('GENERAL', 'FASHION', 'FOOD_BEVERAGE', 'CUSTOM'));

COMMENT ON COLUMN shop_themes.template_key IS
    'GENERAL | FASHION | FOOD_BEVERAGE | CUSTOM — CUSTOM uses free design tokens in config jsonb';
