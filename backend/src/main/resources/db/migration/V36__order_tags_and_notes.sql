-- V36 (AB2): merchant order tags, tag assignments, bulk-action idempotency.
-- shops PK is shops.id (uuid); API shopId is shops.shop_no. orders.shop_id / order_tags.shop_id → shops.id.
-- orders.internal_note already exists from V1 — reuse only (COMMENT for clarity). Audit columns follow V1 naming.

CREATE TABLE IF NOT EXISTS order_tags (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    name character varying(40) NOT NULL,
    color character varying(7),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_order_tags PRIMARY KEY (id),
    CONSTRAINT fk_order_tags_shop_id_shops FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    CONSTRAINT order_tags_color_chk CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);
COMMENT ON TABLE order_tags IS '[Orders] V36 (AB2): per-shop order tags (merchant-only)';
CREATE UNIQUE INDEX IF NOT EXISTS ux_order_tags_shop_name ON order_tags (shop_id, lower(name));
CREATE INDEX IF NOT EXISTS ix_order_tags_shop_id ON order_tags (shop_id);

DROP TRIGGER IF EXISTS trg_order_tags_modified_date_time ON order_tags;
CREATE TRIGGER trg_order_tags_modified_date_time BEFORE UPDATE ON order_tags FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

CREATE TABLE IF NOT EXISTS order_tag_assignments (
    order_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    CONSTRAINT pk_order_tag_assignments PRIMARY KEY (order_id, tag_id),
    CONSTRAINT fk_order_tag_assignments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_tag_assignments_tag FOREIGN KEY (tag_id) REFERENCES order_tags(id) ON DELETE CASCADE
);
COMMENT ON TABLE order_tag_assignments IS '[Orders] V36 (AB2): many-to-many order ↔ tag (max 10 tags/order enforced in app)';
CREATE INDEX IF NOT EXISTS ix_order_tag_assignments_tag ON order_tag_assignments (tag_id);

-- Bulk idempotency (checkout idempotency lives on orders and cannot cover bulk)
CREATE TABLE IF NOT EXISTS order_bulk_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    action character varying(20) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response jsonb NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_order_bulk_requests PRIMARY KEY (id),
    CONSTRAINT ux_order_bulk_requests UNIQUE (shop_id, actor_user_id, idempotency_key)
);
COMMENT ON TABLE order_bulk_requests IS '[Orders] V36 (AB2): idempotent bulk accept/process/ship/cancel/tags responses';
CREATE INDEX IF NOT EXISTS ix_order_bulk_requests_shop ON order_bulk_requests (shop_id, created_date_time DESC);

CREATE INDEX IF NOT EXISTS ix_orders_shop_payment_method ON orders (shop_id, payment_method);

COMMENT ON COLUMN orders.internal_note IS 'Merchant-only note; never exposed to buyer DTOs';
