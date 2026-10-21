-- V34 (B3): platform admin - audit_logs reason / ip / user agent, merchant (owners) suspension status. Additive, idempotent.
-- V1 audit_logs already has actor, action, resource (target), before_data / after_data, reason_code, request_id and
-- occurred_at; it has no free-text reason and no client IP / user agent. V1/V2 owners has no status at all, while
-- shops already has suspended_at / suspended_by / suspend_reason / suspend_reason_code / restored_at / restored_by (V1).

-- 1. audit_logs: free-text reason (mandatory for admin suspend / reactivate in the app), client IP, user agent.
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS reason text;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS ip_address character varying(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent character varying(500);
COMMENT ON COLUMN audit_logs.reason IS 'V34 (B3): เหตุผลที่ผู้ดูแลกรอก (ข้อความ) — reason_code ยังเป็นรหัสสั้น';
COMMENT ON COLUMN audit_logs.ip_address IS 'V34 (B3): IP ของผู้เรียก API (request.getRemoteAddr)';
COMMENT ON COLUMN audit_logs.user_agent IS 'V34 (B3): User-Agent ของผู้เรียก (ตัดที่ 500 ตัวอักษร)';

-- audit queries of the admin API: newest first, by action / auth context
CREATE INDEX IF NOT EXISTS ix_audit_logs_occurred_at ON audit_logs USING btree (occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_logs_action_occurred_at ON audit_logs USING btree (action, occurred_at DESC);

-- 2. owners (merchant): status + suspension columns, same names as on shops.
ALTER TABLE owners ADD COLUMN IF NOT EXISTS status character varying(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE owners ADD COLUMN IF NOT EXISTS suspended_at timestamp with time zone;
ALTER TABLE owners ADD COLUMN IF NOT EXISTS suspended_by uuid;
ALTER TABLE owners ADD COLUMN IF NOT EXISTS suspend_reason text;
ALTER TABLE owners ADD COLUMN IF NOT EXISTS suspend_reason_code character varying(50);
ALTER TABLE owners ADD COLUMN IF NOT EXISTS restored_at timestamp with time zone;
ALTER TABLE owners ADD COLUMN IF NOT EXISTS restored_by uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_owners_status' AND conrelid = 'owners'::regclass) THEN
        ALTER TABLE owners ADD CONSTRAINT ck_owners_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_owners_suspended_by' AND conrelid = 'owners'::regclass) THEN
        ALTER TABLE owners ADD CONSTRAINT fk_owners_suspended_by FOREIGN KEY (suspended_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_owners_restored_by' AND conrelid = 'owners'::regclass) THEN
        ALTER TABLE owners ADD CONSTRAINT fk_owners_restored_by FOREIGN KEY (restored_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_owners_status ON owners USING btree (status);
COMMENT ON COLUMN owners.status IS 'V34 (B3): ACTIVE|SUSPENDED — ระงับผู้ขาย (ร้านที่เปิดอยู่ทั้งหมดถูกระงับด้วย suspend_reason_code = MERCHANT_SUSPENDED)';
