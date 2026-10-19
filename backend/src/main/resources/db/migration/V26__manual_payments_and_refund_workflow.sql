-- V26: manual transfer payments (slip review) + shop-handled refund workflow (additive, idempotent).
-- Reuses V1 payments / payment_proofs / refunds / media_files / shop_payment_gateways / payment_webhook_events.
-- The platform never moves money: refunds are recorded and evidenced, the shop transfers back itself.

-- 1. Slip details the V1 payment_proofs table lacks.
ALTER TABLE payment_proofs ADD COLUMN IF NOT EXISTS bank_name varchar(100);
ALTER TABLE payment_proofs ADD COLUMN IF NOT EXISTS amount_mismatch boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN payment_proofs.bank_name IS 'ธนาคารต้นทางตามที่ลูกค้าแจ้ง';
COMMENT ON COLUMN payment_proofs.amount_mismatch IS 'ยอดที่แจ้งไม่ตรงยอดออเดอร์ตอนส่งสลิป — ร้านต้องยืนยันพร้อมเหตุผลถ้าจะรับ';

-- One live (PENDING_REVIEW / VERIFIED) slip per bank reference within a shop; rejected slips may be resubmitted.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_proofs_shop_reference_live
    ON payment_proofs (shop_id, upper(reference_no))
    WHERE reference_no IS NOT NULL AND status IN ('PENDING_REVIEW', 'VERIFIED');

-- 2. Refund workflow statuses (V1 kept PENDING / SUCCEEDED / FAILED for gateway refunds; still allowed).
ALTER TABLE refunds DROP CONSTRAINT IF EXISTS ck_refunds_status;
ALTER TABLE refunds ADD CONSTRAINT ck_refunds_status CHECK (status IN (
    'PENDING', 'SUCCEEDED', 'FAILED',
    'REQUESTED', 'APPROVED', 'PROOF_UPLOADED', 'COMPLETED', 'REJECTED', 'DISPUTED'));

ALTER TABLE refunds ADD COLUMN IF NOT EXISTS requested_by uuid;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS requester_type varchar(20);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS approved_by uuid;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS approved_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS rejected_by uuid;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS rejected_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS reject_reason varchar(500);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS restock boolean;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS proof_media_file_id uuid;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS proof_reference varchar(120);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS proof_transferred_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS proof_uploaded_by uuid;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS proof_uploaded_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS completed_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS buyer_ack_status varchar(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS buyer_ack_at timestamptz;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS buyer_ack_note varchar(500);

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS ck_refunds_requester_type;
ALTER TABLE refunds ADD CONSTRAINT ck_refunds_requester_type CHECK (requester_type IS NULL OR requester_type IN ('BUYER', 'MERCHANT', 'ADMIN', 'SYSTEM'));
ALTER TABLE refunds DROP CONSTRAINT IF EXISTS ck_refunds_buyer_ack_status;
ALTER TABLE refunds ADD CONSTRAINT ck_refunds_buyer_ack_status CHECK (buyer_ack_status IN ('NONE', 'CONFIRMED', 'DISPUTED'));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_refunds_proof_media_file_id') THEN
        ALTER TABLE refunds ADD CONSTRAINT fk_refunds_proof_media_file_id
            FOREIGN KEY (proof_media_file_id) REFERENCES media_files(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_refunds_proof_media_file_id ON refunds (proof_media_file_id);
CREATE INDEX IF NOT EXISTS ix_refunds_shop_id_status ON refunds (shop_id, status);

COMMENT ON COLUMN refunds.status IS 'REQUESTED → APPROVED → PROOF_UPLOADED → COMPLETED (ลูกค้ายืนยันรับเงิน) | REJECTED | DISPUTED (ลูกค้าแย้ง) ; PENDING|SUCCEEDED|FAILED = gateway refund (V1)';
COMMENT ON COLUMN refunds.proof_media_file_id IS 'สลิปคืนเงินที่ร้านอัปโหลด (media_files.purpose=REFUND_PROOF) — ระบบไม่โอนเงินเอง';

-- 3. Refund slip purpose on media_files.
ALTER TABLE media_files DROP CONSTRAINT IF EXISTS ck_media_files_purpose;
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_purpose CHECK (purpose IN (
    'PRODUCT', 'SHOP_LOGO', 'SHOP_COVER', 'DOCUMENT', 'BLOG', 'REPORT', 'IMPORT', 'PAYMENT_SLIP',
    'PACKING_PHOTO', 'DELIVERY_PROOF', 'OTHER', 'REFUND_PROOF'));

-- 4. Payment status history (who / actor type / reason / from → to).
CREATE TABLE IF NOT EXISTS payment_status_history (
    id bigserial PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    shop_id uuid NOT NULL,
    proof_id uuid REFERENCES payment_proofs(id) ON DELETE SET NULL,
    from_status varchar(30),
    to_status varchar(30) NOT NULL,
    event varchar(60) NOT NULL,
    actor_type varchar(20) NOT NULL,
    actor_user_id uuid,
    reason varchar(500),
    metadata_json text,
    created_date_time timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    modified_date_time timestamptz NOT NULL DEFAULT now(),
    modified_by uuid,
    CONSTRAINT ck_payment_status_history_actor_type CHECK (actor_type IN ('BUYER', 'MERCHANT', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX IF NOT EXISTS ix_payment_status_history_payment_id ON payment_status_history (payment_id, id);
CREATE INDEX IF NOT EXISTS ix_payment_status_history_order_id ON payment_status_history (order_id, id);
COMMENT ON TABLE payment_status_history IS '[Payments] ประวัติสถานะการชำระ/สลิป — append-only';

-- 5. Refund status history.
CREATE TABLE IF NOT EXISTS refund_status_history (
    id bigserial PRIMARY KEY,
    refund_id uuid NOT NULL REFERENCES refunds(id) ON DELETE CASCADE,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    shop_id uuid NOT NULL,
    from_status varchar(30),
    to_status varchar(30) NOT NULL,
    event varchar(60) NOT NULL,
    actor_type varchar(20) NOT NULL,
    actor_user_id uuid,
    reason varchar(500),
    metadata_json text,
    created_date_time timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    modified_date_time timestamptz NOT NULL DEFAULT now(),
    modified_by uuid,
    CONSTRAINT ck_refund_status_history_actor_type CHECK (actor_type IN ('BUYER', 'MERCHANT', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX IF NOT EXISTS ix_refund_status_history_refund_id ON refund_status_history (refund_id, id);
CREATE INDEX IF NOT EXISTS ix_refund_status_history_order_id ON refund_status_history (order_id, id);
COMMENT ON TABLE refund_status_history IS '[Returns & Cancellations] ประวัติสถานะคำขอคืนเงิน — append-only';

DROP TRIGGER IF EXISTS trg_payment_status_history_modified_date_time ON payment_status_history;
CREATE TRIGGER trg_payment_status_history_modified_date_time BEFORE UPDATE ON payment_status_history FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
DROP TRIGGER IF EXISTS trg_refund_status_history_modified_date_time ON refund_status_history;
CREATE TRIGGER trg_refund_status_history_modified_date_time BEFORE UPDATE ON refund_status_history FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
