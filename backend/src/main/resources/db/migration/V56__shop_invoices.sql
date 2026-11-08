-- Shop commercial invoices + tax invoices (merchant → customer).
-- Distinct from payouts/withdrawals. Issuing a document does NOT change order/payment/wallet status.
-- TAX_INVOICE requires shop_tax_profiles.vat_registered + tax_id + vat_rate (merchant-supplied; not inventing RD rate).

CREATE TABLE shop_tax_profiles (
    shop_id UUID PRIMARY KEY REFERENCES shops(id),
    legal_name VARCHAR(255),
    tax_id VARCHAR(20),
    branch_code VARCHAR(10) NOT NULL DEFAULT '00000',
    vat_registered BOOLEAN NOT NULL DEFAULT FALSE,
    vat_rate NUMERIC(7, 4),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID,
    CONSTRAINT chk_shop_tax_vat_rate CHECK (vat_rate IS NULL OR (vat_rate >= 0 AND vat_rate <= 1))
);

CREATE TABLE shop_invoice_sequences (
    shop_id UUID NOT NULL REFERENCES shops(id),
    doc_type VARCHAR(20) NOT NULL,
    year_no INT NOT NULL,
    next_seq INT NOT NULL DEFAULT 1,
    PRIMARY KEY (shop_id, doc_type, year_no),
    CONSTRAINT chk_shop_invoice_seq_type CHECK (doc_type IN ('INVOICE', 'TAX_INVOICE')),
    CONSTRAINT chk_shop_invoice_seq_positive CHECK (next_seq >= 1)
);

CREATE TABLE shop_invoices (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id),
    doc_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    document_number VARCHAR(40),
    number_year INT,
    number_seq INT,
    currency CHAR(3) NOT NULL DEFAULT 'THB',
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    source_ref VARCHAR(64),
    seller_legal_name VARCHAR(255) NOT NULL,
    seller_tax_id VARCHAR(20),
    seller_branch_code VARCHAR(10),
    seller_address TEXT NOT NULL,
    seller_vat_registered BOOLEAN NOT NULL DEFAULT FALSE,
    buyer_name VARCHAR(255) NOT NULL,
    buyer_tax_id VARCHAR(20),
    buyer_address TEXT NOT NULL,
    buyer_phone VARCHAR(30),
    buyer_email VARCHAR(255),
    subtotal NUMERIC(18, 2) NOT NULL,
    discount_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    shipping_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    taxable_base NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(18, 2) NOT NULL,
    vat_rate_applied NUMERIC(7, 4),
    note TEXT,
    idempotency_key VARCHAR(128) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    issued_by UUID,
    issued_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_shop_invoice_type CHECK (doc_type IN ('INVOICE', 'TAX_INVOICE')),
    CONSTRAINT chk_shop_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'CANCELLED')),
    CONSTRAINT chk_shop_invoice_source CHECK (source_type IN ('ORDER')),
    CONSTRAINT uq_shop_invoice_idempotency UNIQUE (shop_id, idempotency_key),
    CONSTRAINT uq_shop_invoice_number UNIQUE (shop_id, doc_type, document_number),
    CONSTRAINT chk_shop_invoice_amounts CHECK (
        subtotal >= 0 AND discount_total >= 0 AND shipping_total >= 0
        AND taxable_base >= 0 AND tax_amount >= 0 AND grand_total >= 0
    )
);

CREATE INDEX idx_shop_invoices_shop_created ON shop_invoices (shop_id, created_at DESC);
CREATE INDEX idx_shop_invoices_shop_status ON shop_invoices (shop_id, status, created_at DESC);
CREATE INDEX idx_shop_invoices_shop_type ON shop_invoices (shop_id, doc_type, created_at DESC);
CREATE INDEX idx_shop_invoices_source ON shop_invoices (shop_id, source_type, source_id);

CREATE TABLE shop_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES shop_invoices(id) ON DELETE CASCADE,
    line_no INT NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(18, 3) NOT NULL,
    unit_price NUMERIC(18, 2) NOT NULL,
    line_discount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    line_subtotal NUMERIC(18, 2) NOT NULL,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    source_type VARCHAR(32),
    source_id UUID,
    CONSTRAINT chk_shop_invoice_line_qty CHECK (quantity > 0),
    CONSTRAINT uq_shop_invoice_line_no UNIQUE (invoice_id, line_no)
);

CREATE TABLE shop_invoice_events (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES shop_invoices(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    actor_user_id UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shop_invoice_events_invoice ON shop_invoice_events (invoice_id, created_at);

INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'FINANCE_INVOICE_MANAGE', 'FINANCE', 'STORE',
     'สร้าง แก้ไขร่าง และออก/ยกเลิกใบแจ้งหนี้หรือใบกำกับภาษีของร้าน', '§6', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'FINANCE_INVOICE_MANAGE'
 WHERE role.code IN ('STORE_OWNER', 'STORE_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
