-- =============================================================================
-- ThaiShopFun Ecommerce — Flyway V2__shop_onboarding_owners_categories_reinstatement.sql
--
-- Adds, on top of V1__init_schema, what the features merged from branch 7f730e8 need
-- (multi-shop onboarding, central KYC owners, shop categories with Chinese names,
-- shop reinstatement requests). It replaces that branch's own V1..V13 migrations, which are
-- archived in backend/db-archive/from-7f730e8/ and were written for a different base schema.
--
-- Mapped onto existing V1 tables instead of duplicating them:
--   * branch "shops" (BIGINT shop_id)      -> V1 shops (UUID id) + shops.shop_no running number
--                                             (API keeps exposing the number as shopId)
--   * branch "product_categories"          -> V1 hub_categories + category_no running number + name_zh
--   * branch "owner_audit_log"             -> V1 audit_logs (resource_type = 'OWNER')
--   * branch created_at/updated_at columns -> created_date_time/created_by/modified_date_time/modified_by
--                                             (TIMESTAMPTZ, BEFORE UPDATE trigger set_modified_date_time())
-- New tables: owners, shop_reinstatement_requests.
--
-- Written defensively for databases that already hold data (TSF_DB): CREATE TABLE / ADD COLUMN /
-- CREATE INDEX ... IF NOT EXISTS, constraints and triggers guarded via pg_constraint / pg_trigger.
-- Unique indexes refuse to build over duplicate data with an explicit error (whole migration rolls back).
-- No BEGIN/COMMIT: Flyway wraps the migration in one transaction.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. owners — central KYC owner profile (one per citizen id), reused across a user's shops
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS owners (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    owner_type character varying(30) NOT NULL,
    title character varying(30) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    citizen_id_hash character varying(64),
    citizen_id_encrypted text NOT NULL,
    birth_date date NOT NULL,
    nationality character varying(100) NOT NULL,
    phone character varying(30) NOT NULL,
    email character varying(255) NOT NULL,
    house_no character varying(50) NOT NULL,
    village_no character varying(50),
    building character varying(255),
    alley character varying(255),
    road character varying(255),
    province character varying(100) NOT NULL,
    district character varying(100) NOT NULL,
    subdistrict character varying(100) NOT NULL,
    postal_code character varying(10) NOT NULL,
    bank_name character varying(100) NOT NULL,
    bank_account_encrypted text NOT NULL,
    bank_account_name character varying(255) NOT NULL,
    sensitive_review character varying(30) DEFAULT 'NONE'::character varying NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_owners PRIMARY KEY (id),
    CONSTRAINT ck_owners_owner_type CHECK ((owner_type IN ('PERSON', 'COMPANY'))),
    CONSTRAINT ck_owners_sensitive_review CHECK ((sensitive_review IN ('NONE', 'PENDING_REVIEW'))),
    CONSTRAINT fk_owners_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_owners_user_id ON owners USING btree (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_owners_citizen_id_hash ON owners USING btree (citizen_id_hash) WHERE (citizen_id_hash IS NOT NULL);

COMMENT ON TABLE owners IS '[Merchant Onboarding] เจ้าของร้าน (KYC) กลาง 1 คน/เลขบัตร ใช้ซ้ำได้หลายร้านของผู้ใช้เดียวกัน (from 7f730e8)';
COMMENT ON COLUMN owners.citizen_id_hash IS 'SHA-256 ของเลขบัตร (digits only) — UNIQUE เมื่อไม่ NULL; ตัวเลขจริงเก็บเข้ารหัสใน citizen_id_encrypted';
COMMENT ON COLUMN owners.sensitive_review IS 'NONE|PENDING_REVIEW — PENDING_REVIEW เมื่อแก้เลขบัตร/บัญชีธนาคารหลังมีร้านที่อนุมัติแล้ว';

-- -----------------------------------------------------------------------------
-- 2. merchant_applications — social links, citizen id hash, link to central owner,
--    one DRAFT per user (multi-shop: several submitted/approved applications per user are allowed)
-- -----------------------------------------------------------------------------
ALTER TABLE merchant_applications
    ADD COLUMN IF NOT EXISTS line_id character varying(500),
    ADD COLUMN IF NOT EXISTS facebook_url character varying(500),
    ADD COLUMN IF NOT EXISTS instagram_url character varying(500),
    ADD COLUMN IF NOT EXISTS tiktok_url character varying(500),
    ADD COLUMN IF NOT EXISTS citizen_id_hash character varying(64),
    ADD COLUMN IF NOT EXISTS owner_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_merchant_applications_owner_id' AND conrelid = 'merchant_applications'::regclass) THEN
        ALTER TABLE merchant_applications
            ADD CONSTRAINT fk_merchant_applications_owner_id FOREIGN KEY (owner_id) REFERENCES owners(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_merchant_applications_owner_id ON merchant_applications USING btree (owner_id);

-- V1 allowed one *open* application per user (DRAFT/SUBMITTED/UNDER_REVIEW/NEEDS_INFORMATION).
-- Multi-shop onboarding needs a new DRAFT while another shop is still under review, so the rule becomes
-- "one DRAFT per user"; it also makes concurrent draft creation race-safe.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM merchant_applications WHERE status = 'DRAFT' GROUP BY user_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V2: some users have more than one DRAFT merchant application; resolve before migrating';
    END IF;
END $$;
DROP INDEX IF EXISTS uq_merchant_applications_user_id_open;
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_applications_user_id_draft ON merchant_applications USING btree (user_id) WHERE ((status)::text = 'DRAFT'::text);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM merchant_applications WHERE shop_name IS NOT NULL GROUP BY lower(shop_name) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V2: duplicate merchant_applications.shop_name (case-insensitive); resolve before migrating';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_applications_shop_name_lower ON merchant_applications USING btree (lower((shop_name)::text));

COMMENT ON COLUMN merchant_applications.owner_id IS 'เจ้าของ (owners.id) ที่ผูกกับใบสมัคร; ข้อมูล owner_* เป็น snapshot ตอนผูก';
COMMENT ON COLUMN merchant_applications.citizen_id_hash IS 'SHA-256 ของเลขบัตร (digits only) — ไม่ unique (ใบสมัครหลายร้านของเจ้าของเดียวกัน)';

-- -----------------------------------------------------------------------------
-- 3. shops — running shop number (public shopId), onboarding/storefront columns, KYC owner link,
--    statuses used by the onboarding/review flow
-- -----------------------------------------------------------------------------
ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS shop_no bigint GENERATED BY DEFAULT AS IDENTITY,
    ADD COLUMN IF NOT EXISTS owner_id uuid,
    ADD COLUMN IF NOT EXISTS category character varying(100),
    ADD COLUMN IF NOT EXISTS sale_type character varying(30),
    ADD COLUMN IF NOT EXISTS website character varying(500),
    ADD COLUMN IF NOT EXISTS line_id character varying(500),
    ADD COLUMN IF NOT EXISTS facebook_url character varying(500),
    ADD COLUMN IF NOT EXISTS instagram_url character varying(500),
    ADD COLUMN IF NOT EXISTS tiktok_url character varying(500),
    ADD COLUMN IF NOT EXISTS logo_path character varying(500),
    ADD COLUMN IF NOT EXISTS cover_path character varying(500),
    ADD COLUMN IF NOT EXISTS opening_hours character varying(800);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_shops_shop_no' AND conrelid = 'shops'::regclass) THEN
        ALTER TABLE shops ADD CONSTRAINT uq_shops_shop_no UNIQUE (shop_no);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_shops_owner_id' AND conrelid = 'shops'::regclass) THEN
        ALTER TABLE shops ADD CONSTRAINT fk_shops_owner_id FOREIGN KEY (owner_id) REFERENCES owners(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_shops_sale_type' AND conrelid = 'shops'::regclass) THEN
        ALTER TABLE shops ADD CONSTRAINT ck_shops_sale_type CHECK ((sale_type IN ('OWNER', 'RESELLER')));
    END IF;
END $$;

-- Status: ACTIVE (= approved/operating), SUSPENDED, CLOSED from V1 + PENDING_REVIEW / REJECTED for the review flow.
ALTER TABLE shops DROP CONSTRAINT IF EXISTS ck_shops_status;
ALTER TABLE shops ADD CONSTRAINT ck_shops_status CHECK ((status IN ('PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'CLOSED')));

CREATE INDEX IF NOT EXISTS ix_shops_owner_id ON shops USING btree (owner_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM shops GROUP BY lower(name) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V2: duplicate shops.name (case-insensitive); resolve before migrating';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_shops_name_lower ON shops USING btree (lower((name)::text));

COMMENT ON COLUMN shops.shop_no IS 'เลขร้านแบบ running number (identity) — ใช้เป็น shopId ใน API/URL; PK ยังเป็น id (uuid)';
COMMENT ON COLUMN shops.status IS 'PENDING_REVIEW|ACTIVE|REJECTED|SUSPENDED|CLOSED [CHECK]; API แสดง ACTIVE เป็น APPROVED';
COMMENT ON COLUMN shops.owner_id IS 'เจ้าของ KYC (owners.id); ผู้ใช้เจ้าของบัญชีร้านยังเป็น owner_user_id';
COMMENT ON COLUMN shops.category IS 'ชื่อหมวดร้าน (hub_categories.name_th) ตามที่เลือกตอนสมัคร/ตั้งค่าร้าน';
COMMENT ON COLUMN shops.opening_hours IS 'เวลาเปิด-ปิด 7 วัน รูปแบบ open,HH:mm,HH:mm;... (จันทร์..อาทิตย์)';

-- -----------------------------------------------------------------------------
-- 4. hub_categories — numeric id used by the UI + Chinese name
-- -----------------------------------------------------------------------------
ALTER TABLE hub_categories
    ADD COLUMN IF NOT EXISTS category_no bigint GENERATED BY DEFAULT AS IDENTITY,
    ADD COLUMN IF NOT EXISTS name_zh character varying(150);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_hub_categories_category_no' AND conrelid = 'hub_categories'::regclass) THEN
        ALTER TABLE hub_categories ADD CONSTRAINT uq_hub_categories_category_no UNIQUE (category_no);
    END IF;
END $$;

COMMENT ON COLUMN hub_categories.category_no IS 'เลขหมวดแบบ running number — id ตัวเลขที่ API /api/categories ส่งให้ UI';
COMMENT ON COLUMN hub_categories.name_zh IS 'ชื่อหมวดภาษาจีน';

-- -----------------------------------------------------------------------------
-- 5. shop_reinstatement_requests — merchant asks admin to lift a suspension
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shop_reinstatement_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    user_id uuid NOT NULL,
    message character varying(1000) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_shop_reinstatement_requests PRIMARY KEY (id),
    CONSTRAINT ck_shop_reinstatement_requests_status CHECK ((status IN ('PENDING', 'APPROVED', 'REJECTED'))),
    CONSTRAINT fk_shop_reinstatement_requests_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_reinstatement_requests_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_shop_reinstatement_requests_shop_id_status ON shop_reinstatement_requests USING btree (shop_id, status);
CREATE INDEX IF NOT EXISTS ix_shop_reinstatement_requests_user_id ON shop_reinstatement_requests USING btree (user_id);

COMMENT ON TABLE shop_reinstatement_requests IS '[Shop & Team] คำร้องขอเปิดร้านที่ถูกระงับอีกครั้ง (from 7f730e8)';

-- -----------------------------------------------------------------------------
-- 6. audit triggers for the new tables (same convention as V1)
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_owners_modified_date_time' AND tgrelid = 'owners'::regclass) THEN
        CREATE TRIGGER trg_owners_modified_date_time BEFORE UPDATE ON owners FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_shop_reinstatement_requests_modified_date_time' AND tgrelid = 'shop_reinstatement_requests'::regclass) THEN
        CREATE TRIGGER trg_shop_reinstatement_requests_modified_date_time BEFORE UPDATE ON shop_reinstatement_requests FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
    END IF;
END $$;
