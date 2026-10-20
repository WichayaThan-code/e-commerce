-- V31: buyer coupon wallet (Shopee/Lazada-style "collect" before use).
-- One row per (coupon, buyer); collecting again is a no-op. Redeem limits stay on promotions.usage_limit_* / promotion_usages.
CREATE TABLE coupon_collections (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    coupon_id uuid NOT NULL,
    user_id uuid NOT NULL,
    collected_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_coupon_collections PRIMARY KEY (id),
    CONSTRAINT fk_coupon_collections_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE CASCADE,
    CONSTRAINT fk_coupon_collections_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_coupon_collections_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX ix_coupon_collections_user ON coupon_collections (user_id, collected_at DESC);

COMMENT ON TABLE coupon_collections IS 'Shop coupons collected by a buyer; public codes must be collected before they can be applied in the cart';

-- Backfill: codes already applied in active buyer carts stay usable after the upgrade
INSERT INTO coupon_collections (coupon_id, user_id)
SELECT DISTINCT c.id, ca.buyer_user_id
  FROM carts ca
  JOIN coupons c ON c.shop_id = ca.shop_id AND upper(c.code) = upper(ca.coupon_code)
 WHERE ca.buyer_user_id IS NOT NULL AND ca.coupon_code IS NOT NULL AND ca.status = 'ACTIVE'
ON CONFLICT (coupon_id, user_id) DO NOTHING;
