-- Shop wallet / withdrawal ledger (platform-held funds only).
-- Current payment channels (BANK_TRANSFER / PromptPay / COD) settle to the merchant directly and
-- MUST NOT auto-credit this wallet. Credits enter only via explicit PLATFORM_HOLDING_CREDIT (or a
-- future gateway settlement source) — never from order GMV / PAID status.

CREATE TABLE shop_wallets (
    shop_id UUID PRIMARY KEY REFERENCES shops(id),
    currency CHAR(3) NOT NULL DEFAULT 'THB',
    available NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE shop_wallet_ledger (
    id BIGSERIAL PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES shops(id),
    entry_type VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'THB',
    balance_available_after NUMERIC(18, 2) NOT NULL,
    balance_reserved_after NUMERIC(18, 2) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    actor_user_id UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_shop_wallet_ledger_entry_type CHECK (entry_type IN (
        'CREDIT', 'DEBIT', 'RESERVE', 'RELEASE', 'PAYOUT'
    )),
    CONSTRAINT uq_shop_wallet_ledger_idempotency UNIQUE (shop_id, idempotency_key)
);

CREATE INDEX idx_shop_wallet_ledger_shop_created ON shop_wallet_ledger (shop_id, created_at DESC);
CREATE INDEX idx_shop_wallet_ledger_source ON shop_wallet_ledger (source_type, source_id);

CREATE TABLE shop_withdrawal_requests (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id),
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'THB',
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    account_provider VARCHAR(32) NOT NULL,
    account_bank_name VARCHAR(120),
    account_name VARCHAR(255),
    account_number_masked VARCHAR(64) NOT NULL,
    account_number_last4 VARCHAR(8),
    account_promptpay_masked VARCHAR(64),
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    reject_reason TEXT,
    paid_at TIMESTAMPTZ,
    paid_by UUID,
    transfer_reference VARCHAR(128),
    transfer_amount NUMERIC(18, 2),
    transfer_date DATE,
    external_transfer_key VARCHAR(128),
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_shop_withdrawal_status CHECK (status IN (
        'REQUESTED', 'APPROVED', 'PROCESSING', 'PAID', 'REJECTED', 'CANCELLED', 'NEEDS_REVIEW'
    )),
    CONSTRAINT uq_shop_withdrawal_idempotency UNIQUE (shop_id, idempotency_key),
    -- NULL external_transfer_key allowed many times (SQL UNIQUE); non-null values must be unique.
    CONSTRAINT uq_shop_withdrawal_external_transfer_key UNIQUE (external_transfer_key)
);

CREATE INDEX idx_shop_withdrawal_shop_requested ON shop_withdrawal_requests (shop_id, requested_at DESC);
CREATE INDEX idx_shop_withdrawal_status_requested ON shop_withdrawal_requests (status, requested_at DESC);

CREATE TABLE shop_withdrawal_events (
    id BIGSERIAL PRIMARY KEY,
    withdrawal_id BIGINT NOT NULL REFERENCES shop_withdrawal_requests(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    actor_user_id UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shop_withdrawal_events_withdrawal ON shop_withdrawal_events (withdrawal_id, created_at);

-- Permissions (store + platform). Not granted via SHOP_UPDATE.
INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'FINANCE_READ', 'FINANCE', 'STORE',
     'ดูยอดเงินพร้อมถอน ประวัติ ledger และคำขอถอนของร้าน', '§6', TRUE, '2.0'),
    (gen_random_uuid(), 'FINANCE_WITHDRAW', 'FINANCE', 'STORE',
     'ยื่นและยกเลิกคำขอถอนเงินของร้าน (ไม่รวมยืนยันโอน)', '§6', TRUE, '2.0'),
    (gen_random_uuid(), 'FINANCE_EXPORT', 'FINANCE', 'STORE',
     'ส่งออกประวัติการถอน/โอนของร้าน', '§6', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_PAYOUT_READ', 'PLATFORM', 'PLATFORM',
     'ดูคิวคำขอถอนเงินของร้านทั้งหมด', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_PAYOUT_REVIEW', 'PLATFORM', 'PLATFORM',
     'อนุมัติหรือปฏิเสธคำขอถอนเงิน', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_PAYOUT_SETTLE', 'PLATFORM', 'PLATFORM',
     'บันทึกผลโอนภายนอก (PAID / NEEDS_REVIEW) หลังโอนจริงแล้วเท่านั้น', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_PAYOUT_CREDIT', 'PLATFORM', 'PLATFORM',
     'เครดิตยอดเงินที่แพลตฟอร์มถือไว้เข้า wallet ร้าน (ไม่ใช่จากยอดขาย GMV)', '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code IN ('FINANCE_READ', 'FINANCE_EXPORT')
 WHERE role.code IN ('STORE_OWNER', 'STORE_ADMIN', 'STORE_MANAGER', 'STORE_VIEWER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'FINANCE_WITHDRAW'
 WHERE role.code IN ('STORE_OWNER', 'STORE_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code IN (
      'PLATFORM_PAYOUT_READ', 'PLATFORM_PAYOUT_REVIEW', 'PLATFORM_PAYOUT_SETTLE', 'PLATFORM_PAYOUT_CREDIT'
  )
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
