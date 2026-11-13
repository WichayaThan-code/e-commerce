-- V67: Platform deal campaigns for the public /deals page (TODAY / WEEKLY / FLASH / BOGO).

CREATE TABLE deal_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind VARCHAR(20) NOT NULL,
    title_th VARCHAR(150) NOT NULL,
    title_en VARCHAR(150),
    title_zh VARCHAR(150),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT ck_deal_campaigns_kind CHECK (kind IN ('TODAY', 'WEEKLY', 'FLASH', 'BOGO'))
);

CREATE INDEX ix_deal_campaigns_kind_active_window
    ON deal_campaigns (kind, is_active, starts_at, ends_at);

CREATE INDEX ix_deal_campaigns_sort
    ON deal_campaigns (sort_order ASC, created_date_time DESC);

CREATE TABLE deal_campaign_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES deal_campaigns(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    CONSTRAINT uq_deal_campaign_products UNIQUE (campaign_id, product_id)
);

CREATE INDEX ix_deal_campaign_products_campaign_sort
    ON deal_campaign_products (campaign_id, sort_order ASC, created_date_time ASC);

CREATE INDEX ix_deal_campaign_products_product
    ON deal_campaign_products (product_id);

COMMENT ON TABLE deal_campaigns IS
    '[Platform] Curated deal campaigns for /deals tabs TODAY, WEEKLY, FLASH, BOGO';
COMMENT ON TABLE deal_campaign_products IS
    '[Platform] Products attached to a deal campaign';
