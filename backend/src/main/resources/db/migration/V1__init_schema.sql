-- =============================================================================
-- ThaiShopFun Ecommerce — PostgreSQL schema (consolidated initial migration)
-- Flyway V1__init_schema.sql
--
-- Creates the COMPLETE schema in one go on an EMPTY database. It is equivalent to the
-- former migrations applied in order (archived in backend/db-archive/):
--     V1__create_auth_tables.sql
--     V2__create_merchant_applications.sql
--     V3__ecommerce_schema_v3.sql   (schema v3, 2026-10-04 authz)
-- Generated 2026-10-06 with: pg_dump --schema-only --no-owner --no-privileges
-- from a fresh database on which V1+V2+V3 were applied, then cleaned (psql meta-commands,
-- session SETs and the search_path override removed; "public." prefixes stripped;
-- CHECK enum lists written back as IN (...); audit columns of users / refresh_tokens /
-- merchant_applications placed at the end of the table, same column order as TSF_DB).
--
-- Contents: 2 functions, 70 tables (+7 bigserial sequences), defaults, PK/UNIQUE/CHECK
-- constraints, indexes, BEFORE UPDATE audit triggers, 2 deferred constraint triggers
-- (single ACTIVE STORE_OWNER per shop), foreign keys, table/column comments.
-- Target: PostgreSQL 15+ (validated on 18). Schema: public (Flyway default schema).
--
-- Plain DDL — NOT idempotent: run exactly once, on an empty database.
-- No BEGIN/COMMIT: Flyway wraps the migration in a transaction (for psql use -1).
--
-- Reference data (platform_settings, authz catalog auth_roles / auth_permissions /
-- auth_role_permissions, optional PLATFORM_ADMIN) is NOT in this file:
-- run src/main/resources/db/seed/seed_reference_data.sql afterwards.
--
-- RULE: never edit this file once applied. Future schema changes go in a new
-- migration V2__<description>.sql, V3__..., etc.
-- =============================================================================

-- Thai timezone (session + database default) — carried over from former V3.
SET TIME ZONE 'Asia/Bangkok';
DO $$ BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'Asia/Bangkok');
EXCEPTION WHEN insufficient_privilege THEN
    RAISE WARNING 'no privilege to ALTER DATABASE % SET timezone — set it manually (session timezone is Asia/Bangkok)', current_database();
END $$;

--
-- Name: check_shop_single_owner(); Type: FUNCTION
--

CREATE FUNCTION check_shop_single_owner() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_shops UUID[];
    v_shop UUID;
    v_owner UUID;
    v_status VARCHAR(30);
    v_deleted TIMESTAMPTZ;
    v_cnt INTEGER;
    v_match INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'shops' THEN
        v_shops := ARRAY[NEW.id];
    ELSIF TG_OP = 'DELETE' THEN
        v_shops := ARRAY[OLD.shop_id];
    ELSIF TG_OP = 'UPDATE' AND OLD.shop_id IS DISTINCT FROM NEW.shop_id THEN
        v_shops := ARRAY[OLD.shop_id, NEW.shop_id];
    ELSE
        v_shops := ARRAY[NEW.shop_id];
    END IF;
    FOREACH v_shop IN ARRAY v_shops LOOP
        SELECT owner_user_id, status, deleted_at INTO v_owner, v_status, v_deleted FROM shops WHERE id = v_shop;
        CONTINUE WHEN NOT FOUND OR v_status = 'CLOSED' OR v_deleted IS NOT NULL;
        SELECT count(*), count(*) FILTER (WHERE user_id = v_owner) INTO v_cnt, v_match
          FROM shop_members WHERE shop_id = v_shop AND role = 'STORE_OWNER' AND status = 'ACTIVE';
        IF v_cnt <> 1 OR v_match <> 1 THEN
            RAISE EXCEPTION 'shop % must have exactly one ACTIVE STORE_OWNER matching shops.owner_user_id (found %, matching %)', v_shop, v_cnt, v_match
                USING ERRCODE = 'check_violation';
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;

--
-- Name: FUNCTION check_shop_single_owner(); Type: COMMENT
--

COMMENT ON FUNCTION check_shop_single_owner() IS 'authz constraint trigger (DEFERRABLE INITIALLY DEFERRED): ร้านที่ไม่ CLOSED/ไม่ถูกลบ ต้องมี ACTIVE STORE_OWNER 1 คน = shops.owner_user_id — ตรวจตอน COMMIT';

--
-- Name: set_modified_date_time(); Type: FUNCTION
--

CREATE FUNCTION set_modified_date_time() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.modified_date_time := now();
    RETURN NEW;
END;
$$;

--
-- Name: FUNCTION set_modified_date_time(); Type: COMMENT
--

COMMENT ON FUNCTION set_modified_date_time() IS 'BEFORE UPDATE trigger (ทุกตาราง): ตั้ง modified_date_time = now() อัตโนมัติ; modified_by แอปเป็นผู้ตั้ง (ecommerce schema v3 audit standard)';

--
-- Name: audit_logs; Type: TABLE
--

CREATE TABLE audit_logs (
    id bigint NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_id uuid,
    actor_type character varying(30) NOT NULL,
    shop_id uuid,
    action character varying(80) NOT NULL,
    resource_type character varying(40) NOT NULL,
    resource_id uuid,
    permission character varying(80),
    outcome character varying(20) NOT NULL,
    reason_code character varying(50),
    before_data jsonb,
    after_data jsonb,
    request_id character varying(64),
    policy_version character varying(20),
    auth_context character varying(20),
    service_name character varying(80),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_audit_logs_actor_type CHECK ((actor_type IN ('USER', 'SERVICE', 'SYSTEM', 'OMS'))),
    CONSTRAINT ck_audit_logs_auth_context CHECK ((auth_context IN ('PUBLIC', 'ACCOUNT', 'SHOP', 'PLATFORM', 'SERVICE'))),
    CONSTRAINT ck_audit_logs_outcome CHECK ((outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))),
    CONSTRAINT ck_audit_logs_service_name CHECK ((((actor_type)::text <> 'SERVICE'::text) OR (service_name IS NOT NULL)))
);

--
-- Name: TABLE audit_logs; Type: COMMENT
--

COMMENT ON TABLE audit_logs IS '[Audit & Platform] append-only business audit';

--
-- Name: COLUMN audit_logs.id; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.id IS 'append-only business audit';

--
-- Name: COLUMN audit_logs.actor_type; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.actor_type IS 'USER|SERVICE|SYSTEM|OMS [CHECK enum: audit_actor_type]';

--
-- Name: COLUMN audit_logs.action; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.action IS 'เช่น INVENTORY_ADJUST|ORDER_CANCEL|OWNERSHIP_TRANSFER';

--
-- Name: COLUMN audit_logs.resource_id; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.resource_id IS 'targetId (AUTHZ 08)';

--
-- Name: COLUMN audit_logs.outcome; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.outcome IS 'SUCCESS|DENIED|FAILURE [CHECK enum: audit_outcome]';

--
-- Name: COLUMN audit_logs.before_data; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.before_data IS 'เฉพาะ field ที่จำเป็น — ห้ามมี token/PII (redact ที่แอป)';

--
-- Name: COLUMN audit_logs.auth_context; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.auth_context IS 'PUBLIC|ACCOUNT|SHOP|PLATFORM|SERVICE [CHECK enum: audit_auth_context]';

--
-- Name: COLUMN audit_logs.service_name; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.service_name IS 'authz: service identity เมื่อ actor_type=SERVICE (AUTHZ 07) — ck_audit_logs_service_name';

--
-- Name: COLUMN audit_logs.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN audit_logs.created_by; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN audit_logs.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN audit_logs.modified_by; Type: COMMENT
--

COMMENT ON COLUMN audit_logs.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: audit_logs_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE audit_logs_id_seq OWNED BY audit_logs.id;

--
-- Name: auth_permissions; Type: TABLE
--

CREATE TABLE auth_permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(80) NOT NULL,
    module character varying(40) NOT NULL,
    scope character varying(20) NOT NULL,
    description_th character varying(500),
    doc_section character varying(20),
    is_active boolean DEFAULT true NOT NULL,
    policy_version character varying(20) NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_auth_permissions_code_format CHECK (((code)::text ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$'::text)),
    CONSTRAINT ck_auth_permissions_scope CHECK ((scope IN ('PLATFORM', 'OWN_ACCOUNT', 'OWN_APPLICATION', 'OWN_RESOURCE', 'STORE', 'PUBLIC')))
);

--
-- Name: TABLE auth_permissions; Type: COMMENT
--

COMMENT ON TABLE auth_permissions IS '[Auth & Identity] authz catalog (READ-ONLY reference): รหัสสิทธิ์ MODULE_ACTION — ไม่ใช้ตรวจสิทธิ์ runtime (catalog จริงอยู่ใน Repository + policyVersion)';

--
-- Name: COLUMN auth_permissions.id; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.id IS 'authz catalog (READ-ONLY reference): รหัสสิทธิ์ MODULE_ACTION — ไม่ใช้ตรวจสิทธิ์ runtime (catalog จริงอยู่ใน Repository + policyVersion)';

--
-- Name: COLUMN auth_permissions.code; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.code IS 'UPPER_SNAKE_CASE MODULE_ACTION เช่น PRODUCT_CREATE (ck_auth_permissions_code_format)';

--
-- Name: COLUMN auth_permissions.module; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.module IS 'โมดูลจากส่วนหน้าของรหัส เช่น SHOP, PRODUCT, ORDER, PLATFORM, BUYER';

--
-- Name: COLUMN auth_permissions.scope; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.scope IS 'PLATFORM|OWN_ACCOUNT|OWN_APPLICATION|OWN_RESOURCE|STORE|PUBLIC [CHECK enum: auth_permission_scope]';

--
-- Name: COLUMN auth_permissions.description_th; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.description_th IS 'ความหมาย/เงื่อนไขตามเอกสาร';

--
-- Name: COLUMN auth_permissions.doc_section; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.doc_section IS 'หัวข้อในเอกสาร เช่น §4';

--
-- Name: COLUMN auth_permissions.is_active; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.is_active IS 'false = เลิกใช้ (ห้ามลบเพื่อเก็บประวัติ audit_logs.permission)';

--
-- Name: COLUMN auth_permissions.policy_version; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.policy_version IS 'policyVersion ที่ประกาศรหัสนี้';

--
-- Name: COLUMN auth_permissions.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN auth_permissions.created_by; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN auth_permissions.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN auth_permissions.modified_by; Type: COMMENT
--

COMMENT ON COLUMN auth_permissions.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: auth_role_permissions; Type: TABLE
--

CREATE TABLE auth_role_permissions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    policy_version character varying(20) NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE auth_role_permissions; Type: COMMENT
--

COMMENT ON TABLE auth_role_permissions IS '[Auth & Identity] authz catalog (READ-ONLY reference): Role × Permission matrix ตามเอกสาร — seed sync โดย seed_reference_data.sql / Flyway V3';

--
-- Name: COLUMN auth_role_permissions.id; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.id IS 'authz catalog (READ-ONLY reference): Role × Permission matrix ตามเอกสาร — seed sync โดย seed_reference_data.sql / Flyway V3';

--
-- Name: COLUMN auth_role_permissions.role_id; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.role_id IS 'UNIQUE(role_id,permission_id)';

--
-- Name: COLUMN auth_role_permissions.policy_version; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.policy_version IS 'policyVersion ของ mapping';

--
-- Name: COLUMN auth_role_permissions.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN auth_role_permissions.created_by; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN auth_role_permissions.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN auth_role_permissions.modified_by; Type: COMMENT
--

COMMENT ON COLUMN auth_role_permissions.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: auth_roles; Type: TABLE
--

CREATE TABLE auth_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(40) NOT NULL,
    context character varying(20) NOT NULL,
    name_th character varying(150),
    description_th character varying(500),
    is_assignable boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    policy_version character varying(20) NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_auth_roles_code_format CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_auth_roles_context CHECK ((context IN ('PUBLIC', 'ACCOUNT', 'APPLICANT', 'STORE', 'PLATFORM')))
);

--
-- Name: TABLE auth_roles; Type: COMMENT
--

COMMENT ON TABLE auth_roles IS '[Auth & Identity] authz catalog (READ-ONLY reference): บทบาทตามเอกสาร Role and Permission Matrix v1.1 — ไม่ใช่ source of truth; enforcement อยู่ในโค้ด (Spring Security) แก้ผ่าน migration/seed เท่านั้น';

--
-- Name: COLUMN auth_roles.id; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.id IS 'authz catalog (READ-ONLY reference): บทบาทตามเอกสาร Role and Permission Matrix v1.1 — ไม่ใช่ source of truth; enforcement อยู่ในโค้ด (Spring Security) แก้ผ่าน migration/seed เท่านั้น';

--
-- Name: COLUMN auth_roles.code; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.code IS 'รหัสบทบาท เช่น STORE_OWNER, PLATFORM_ADMIN, BUYER (ck_auth_roles_code_format)';

--
-- Name: COLUMN auth_roles.context; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.context IS 'PUBLIC|ACCOUNT|APPLICANT|STORE|PLATFORM [CHECK enum: auth_role_context]';

--
-- Name: COLUMN auth_roles.name_th; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.name_th IS 'ชื่อบทบาท (TH)';

--
-- Name: COLUMN auth_roles.description_th; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.description_th IS 'ความรับผิดชอบตาม Baseline';

--
-- Name: COLUMN auth_roles.is_assignable; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.is_assignable IS 'true = ค่าที่เก็บใน shop_members.role / platform_roles.role; false = บริบทบัญชี (GUEST/BUYER/APPLICANT)';

--
-- Name: COLUMN auth_roles.sort_order; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.sort_order IS 'ลำดับแสดงผล';

--
-- Name: COLUMN auth_roles.policy_version; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.policy_version IS 'policyVersion ของ catalog ในโค้ด เช่น 1.0';

--
-- Name: COLUMN auth_roles.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN auth_roles.created_by; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN auth_roles.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN auth_roles.modified_by; Type: COMMENT
--

COMMENT ON COLUMN auth_roles.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: background_jobs; Type: TABLE
--

CREATE TABLE background_jobs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    job_type character varying(80) NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    status character varying(30) DEFAULT 'QUEUED'::character varying NOT NULL,
    priority smallint DEFAULT 5 NOT NULL,
    resource_type character varying(40),
    resource_id uuid,
    idempotency_key character varying(120),
    correlation_id character varying(64),
    request_id character varying(64),
    payload_ref jsonb,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 4 NOT NULL,
    lease_owner character varying(80),
    lease_expires_at timestamp with time zone,
    cancel_requested_at timestamp with time zone,
    progress jsonb,
    result_summary jsonb,
    error_code character varying(64),
    error_message character varying(500),
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    requested_by_user_id uuid,
    auth_context character varying(20),
    required_permission character varying(80),
    permission_version_at_request integer,
    policy_version character varying(20),
    result_file_id uuid,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_background_jobs_auth_context CHECK ((auth_context IN ('ACCOUNT', 'SHOP', 'PLATFORM', 'SERVICE'))),
    CONSTRAINT ck_background_jobs_status CHECK ((status IN ('QUEUED', 'RUNNING', 'RETRYING', 'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'CANCELLED')))
);

--
-- Name: TABLE background_jobs; Type: COMMENT
--

COMMENT ON TABLE background_jobs IS '[Notifications & Jobs] Durable job catalog';

--
-- Name: COLUMN background_jobs.id; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.id IS 'Durable job catalog';

--
-- Name: COLUMN background_jobs.job_type; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.job_type IS 'email.send.v1|image.resize.v1|product.import.v1|file.cleanup.v1|report.export.v1|oms.sync.v1';

--
-- Name: COLUMN background_jobs.status; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.status IS 'QUEUED|RUNNING|RETRYING|SUCCEEDED|PARTIALLY_SUCCEEDED|FAILED|CANCELLED [CHECK enum: job_status]';

--
-- Name: COLUMN background_jobs.priority; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.priority IS '1=สูงสุด';

--
-- Name: COLUMN background_jobs.payload_ref; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.payload_ref IS 'reference IDs เท่านั้น ไม่ใส่ secret';

--
-- Name: COLUMN background_jobs.progress; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.progress IS 'processedRows/totalRows ฯลฯ';

--
-- Name: COLUMN background_jobs.error_code; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.error_code IS 'เช่น AUTHORIZATION_REVOKED (worker ตรวจสิทธิ์ซ้ำไม่ผ่าน), VALIDATION_FAILED, PROVIDER_ERROR';

--
-- Name: COLUMN background_jobs.error_message; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.error_message IS 'ไม่มี secret';

--
-- Name: COLUMN background_jobs.requested_by_user_id; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.requested_by_user_id IS 'authz: ผู้สั่งงาน (user-initiated import/export) — worker ใช้ตรวจสิทธิ์ซ้ำ';

--
-- Name: COLUMN background_jobs.auth_context; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.auth_context IS 'ACCOUNT|SHOP|PLATFORM|SERVICE [CHECK enum: job_auth_context]';

--
-- Name: COLUMN background_jobs.required_permission; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.required_permission IS 'authz: permission ที่ worker ต้องตรวจซ้ำก่อนทำ/ก่อนเผยแพร่ไฟล์ เช่น PRODUCT_IMPORT';

--
-- Name: COLUMN background_jobs.permission_version_at_request; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.permission_version_at_request IS 'authz: shop_members/users.permission_version ตอนสั่ง — ต่างจากปัจจุบันให้ตรวจใหม่';

--
-- Name: COLUMN background_jobs.policy_version; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.policy_version IS 'authz: policyVersion ตอนสั่ง';

--
-- Name: COLUMN background_jobs.result_file_id; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.result_file_id IS 'authz: ไฟล์ผลลัพธ์ (media_files.required_permission)';

--
-- Name: COLUMN background_jobs.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN background_jobs.created_by; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN background_jobs.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN background_jobs.modified_by; Type: COMMENT
--

COMMENT ON COLUMN background_jobs.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: blog_categories; Type: TABLE
--

CREATE TABLE blog_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(100) NOT NULL,
    name_th character varying(150) NOT NULL,
    name_en character varying(150),
    is_active boolean DEFAULT true NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE blog_categories; Type: COMMENT
--

COMMENT ON TABLE blog_categories IS '[Content] blog_categories — บล็อก/คอนเทนต์แพลตฟอร์ม';

--
-- Name: COLUMN blog_categories.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN blog_categories.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN blog_categories.created_by; Type: COMMENT
--

COMMENT ON COLUMN blog_categories.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN blog_categories.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN blog_categories.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN blog_categories.modified_by; Type: COMMENT
--

COMMENT ON COLUMN blog_categories.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: blog_posts; Type: TABLE
--

CREATE TABLE blog_posts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    category_id uuid,
    slug character varying(150) NOT NULL,
    title character varying(255) NOT NULL,
    excerpt character varying(500),
    body_md text NOT NULL,
    cover_media_id uuid,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    author_id uuid,
    published_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_blog_posts_status CHECK ((status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')))
);

--
-- Name: TABLE blog_posts; Type: COMMENT
--

COMMENT ON TABLE blog_posts IS '[Content] บทความแพลตฟอร์ม';

--
-- Name: COLUMN blog_posts.id; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.id IS 'บทความแพลตฟอร์ม';

--
-- Name: COLUMN blog_posts.body_md; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.body_md IS 'Markdown content';

--
-- Name: COLUMN blog_posts.status; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.status IS 'DRAFT|PUBLISHED|ARCHIVED [CHECK enum: blog_post_status]';

--
-- Name: COLUMN blog_posts.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN blog_posts.created_by; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN blog_posts.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN blog_posts.modified_by; Type: COMMENT
--

COMMENT ON COLUMN blog_posts.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: buyer_addresses; Type: TABLE
--

CREATE TABLE buyer_addresses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    buyer_user_id uuid NOT NULL,
    label character varying(50),
    recipient_name character varying(150) NOT NULL,
    phone character varying(30) NOT NULL,
    address_line1 character varying(255) NOT NULL,
    address_line2 character varying(255),
    subdistrict character varying(100),
    district character varying(100) NOT NULL,
    province character varying(100) NOT NULL,
    postal_code character varying(10) NOT NULL,
    country_code character(2) DEFAULT 'TH'::bpchar NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE buyer_addresses; Type: COMMENT
--

COMMENT ON TABLE buyer_addresses IS '[Cart & Checkout] สมุดที่อยู่ผู้ซื้อ';

--
-- Name: COLUMN buyer_addresses.id; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.id IS 'สมุดที่อยู่ผู้ซื้อ';

--
-- Name: COLUMN buyer_addresses.label; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.label IS 'บ้าน/ที่ทำงาน';

--
-- Name: COLUMN buyer_addresses.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN buyer_addresses.created_by; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN buyer_addresses.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN buyer_addresses.modified_by; Type: COMMENT
--

COMMENT ON COLUMN buyer_addresses.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: cart_items; Type: TABLE
--

CREATE TABLE cart_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    cart_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price_snapshot numeric(18,2),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_cart_items_quantity CHECK ((quantity > 0))
);

--
-- Name: TABLE cart_items; Type: COMMENT
--

COMMENT ON TABLE cart_items IS '[Cart & Checkout] cart_items — ตะกร้าต่อร้าน, checkout idempotent, สมุดที่อยู่';

--
-- Name: COLUMN cart_items.cart_id; Type: COMMENT
--

COMMENT ON COLUMN cart_items.cart_id IS 'ON DELETE CASCADE; UNIQUE(cart_id,sku_id)';

--
-- Name: COLUMN cart_items.product_id; Type: COMMENT
--

COMMENT ON COLUMN cart_items.product_id IS 'denorm';

--
-- Name: COLUMN cart_items.quantity; Type: COMMENT
--

COMMENT ON COLUMN cart_items.quantity IS '>0';

--
-- Name: COLUMN cart_items.unit_price_snapshot; Type: COMMENT
--

COMMENT ON COLUMN cart_items.unit_price_snapshot IS 'ราคาตอนใส่ตะกร้า (อ้างอิง)';

--
-- Name: COLUMN cart_items.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN cart_items.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN cart_items.created_by; Type: COMMENT
--

COMMENT ON COLUMN cart_items.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN cart_items.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN cart_items.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN cart_items.modified_by; Type: COMMENT
--

COMMENT ON COLUMN cart_items.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: carts; Type: TABLE
--

CREATE TABLE carts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    buyer_user_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_carts_status CHECK ((status IN ('ACTIVE', 'CONVERTED', 'ABANDONED')))
);

--
-- Name: TABLE carts; Type: COMMENT
--

COMMENT ON TABLE carts IS '[Cart & Checkout] ตะกร้า — 1 ตะกร้าต่อร้านต่อผู้ซื้อ';

--
-- Name: COLUMN carts.id; Type: COMMENT
--

COMMENT ON COLUMN carts.id IS 'ตะกร้า — 1 ตะกร้าต่อร้านต่อผู้ซื้อ';

--
-- Name: COLUMN carts.buyer_user_id; Type: COMMENT
--

COMMENT ON COLUMN carts.buyer_user_id IS 'UNIQUE(buyer_user_id,shop_id) WHERE active';

--
-- Name: COLUMN carts.shop_id; Type: COMMENT
--

COMMENT ON COLUMN carts.shop_id IS 'ตะกร้าต่อร้าน (ร้านละ PG)';

--
-- Name: COLUMN carts.status; Type: COMMENT
--

COMMENT ON COLUMN carts.status IS 'ACTIVE|CONVERTED|ABANDONED [CHECK enum: cart_status]';

--
-- Name: COLUMN carts.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN carts.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN carts.created_by; Type: COMMENT
--

COMMENT ON COLUMN carts.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN carts.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN carts.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN carts.modified_by; Type: COMMENT
--

COMMENT ON COLUMN carts.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: checkouts; Type: TABLE
--

CREATE TABLE checkouts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    buyer_user_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    cart_id uuid NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    shipping_address_id uuid,
    shipping_method_id uuid,
    coupon_code character varying(64),
    subtotal numeric(18,2) DEFAULT 0 NOT NULL,
    discount_total numeric(18,2) DEFAULT 0 NOT NULL,
    shipping_fee numeric(18,2) DEFAULT 0 NOT NULL,
    grand_total numeric(18,2) DEFAULT 0 NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    order_id uuid,
    expires_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_checkouts_status CHECK ((status IN ('PENDING', 'COMPLETED', 'EXPIRED', 'FAILED')))
);

--
-- Name: TABLE checkouts; Type: COMMENT
--

COMMENT ON TABLE checkouts IS '[Cart & Checkout] session checkout ก่อนสร้างออเดอร์';

--
-- Name: COLUMN checkouts.id; Type: COMMENT
--

COMMENT ON COLUMN checkouts.id IS 'session checkout ก่อนสร้างออเดอร์';

--
-- Name: COLUMN checkouts.idempotency_key; Type: COMMENT
--

COMMENT ON COLUMN checkouts.idempotency_key IS 'กันสร้างออเดอร์ซ้ำ';

--
-- Name: COLUMN checkouts.status; Type: COMMENT
--

COMMENT ON COLUMN checkouts.status IS 'PENDING|COMPLETED|EXPIRED|FAILED [CHECK enum: checkout_status]';

--
-- Name: COLUMN checkouts.subtotal; Type: COMMENT
--

COMMENT ON COLUMN checkouts.subtotal IS 'คำนวณฝั่ง server';

--
-- Name: COLUMN checkouts.order_id; Type: COMMENT
--

COMMENT ON COLUMN checkouts.order_id IS 'หลัง convert สำเร็จ';

--
-- Name: COLUMN checkouts.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN checkouts.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN checkouts.created_by; Type: COMMENT
--

COMMENT ON COLUMN checkouts.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN checkouts.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN checkouts.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN checkouts.modified_by; Type: COMMENT
--

COMMENT ON COLUMN checkouts.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: coupons; Type: TABLE
--

CREATE TABLE coupons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    promotion_id uuid NOT NULL,
    code character varying(64) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE coupons; Type: COMMENT
--

COMMENT ON TABLE coupons IS '[Promotions] coupons — ส่วนลด/คูปองระดับร้าน';

--
-- Name: COLUMN coupons.code; Type: COMMENT
--

COMMENT ON COLUMN coupons.code IS 'UNIQUE(shop_id,code)';

--
-- Name: COLUMN coupons.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN coupons.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN coupons.created_by; Type: COMMENT
--

COMMENT ON COLUMN coupons.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN coupons.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN coupons.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN coupons.modified_by; Type: COMMENT
--

COMMENT ON COLUMN coupons.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: hub_categories; Type: TABLE
--

CREATE TABLE hub_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(100) NOT NULL,
    name_th character varying(150) NOT NULL,
    name_en character varying(150),
    parent_id uuid,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE hub_categories; Type: COMMENT
--

COMMENT ON TABLE hub_categories IS '[Shop & Team] หมวดร้านในหน้า hub (คล้าย Grab)';

--
-- Name: COLUMN hub_categories.id; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.id IS 'หมวดร้านในหน้า hub (คล้าย Grab)';

--
-- Name: COLUMN hub_categories.parent_id; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.parent_id IS 'hierarchy';

--
-- Name: COLUMN hub_categories.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN hub_categories.created_by; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN hub_categories.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN hub_categories.modified_by; Type: COMMENT
--

COMMENT ON COLUMN hub_categories.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: integration_events; Type: TABLE
--

CREATE TABLE integration_events (
    id bigint NOT NULL,
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    source_system character varying(40) NOT NULL,
    target_system character varying(40) NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_type character varying(40) NOT NULL,
    aggregate_id uuid,
    external_ref character varying(120),
    payload jsonb NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    delivered_at timestamp with time zone,
    correlation_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_integration_events_status CHECK ((status IN ('PENDING', 'DELIVERED', 'ACKED', 'FAILED', 'DEAD')))
);

--
-- Name: TABLE integration_events; Type: COMMENT
--

COMMENT ON TABLE integration_events IS '[OMS Integration] event bus รวมสำหรับ integration (OMS + อื่น)';

--
-- Name: COLUMN integration_events.id; Type: COMMENT
--

COMMENT ON COLUMN integration_events.id IS 'event bus รวมสำหรับ integration (OMS + อื่น)';

--
-- Name: COLUMN integration_events.source_system; Type: COMMENT
--

COMMENT ON COLUMN integration_events.source_system IS 'ECOMMERCE|OMS|PAYMENT_GATEWAY|CARRIER';

--
-- Name: COLUMN integration_events.target_system; Type: COMMENT
--

COMMENT ON COLUMN integration_events.target_system IS 'ECOMMERCE|OMS';

--
-- Name: COLUMN integration_events.status; Type: COMMENT
--

COMMENT ON COLUMN integration_events.status IS 'PENDING|DELIVERED|ACKED|FAILED|DEAD [CHECK enum: integration_event_status]';

--
-- Name: COLUMN integration_events.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN integration_events.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN integration_events.created_by; Type: COMMENT
--

COMMENT ON COLUMN integration_events.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN integration_events.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN integration_events.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN integration_events.modified_by; Type: COMMENT
--

COMMENT ON COLUMN integration_events.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: integration_events_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE integration_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: integration_events_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE integration_events_id_seq OWNED BY integration_events.id;

--
-- Name: inventory_ledger; Type: TABLE
--

CREATE TABLE inventory_ledger (
    id bigint NOT NULL,
    shop_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    entry_type character varying(40) NOT NULL,
    quantity_delta integer NOT NULL,
    on_hand_before integer NOT NULL,
    on_hand_after integer NOT NULL,
    reserved_qty_before integer,
    reserved_qty_after integer,
    reason character varying(500),
    reference_type character varying(40),
    reference_id uuid,
    reservation_id uuid,
    order_item_id uuid,
    idempotency_key character varying(120),
    actor_id uuid,
    actor_type character varying(30),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_inventory_ledger_adjust_reason CHECK ((((entry_type)::text <> 'ADJUST'::text) OR (reason IS NOT NULL))),
    CONSTRAINT ck_inventory_ledger_entry_type CHECK ((entry_type IN ('RECEIVE', 'ADJUST', 'RESERVE', 'RELEASE', 'SALE_CONFIRM', 'COMMIT_SALE', 'RETURN', 'CANCEL', 'RESTOCK_REFUND')))
);

--
-- Name: TABLE inventory_ledger; Type: COMMENT
--

COMMENT ON TABLE inventory_ledger IS '[Inventory] append-only ledger — ห้ามแก้ยอดตรง';

--
-- Name: COLUMN inventory_ledger.id; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.id IS 'append-only ledger — ห้ามแก้ยอดตรง';

--
-- Name: COLUMN inventory_ledger.entry_type; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.entry_type IS 'RECEIVE|ADJUST|RESERVE|RELEASE|SALE_CONFIRM|COMMIT_SALE|RETURN|CANCEL|RESTOCK_REFUND [CHECK enum: inventory_entry_type]';

--
-- Name: COLUMN inventory_ledger.quantity_delta; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.quantity_delta IS 'บวกเข้า / ลบออก (SALE_CONFIRM: on_hand↓ reserved↓)';

--
-- Name: COLUMN inventory_ledger.reserved_qty_before; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.reserved_qty_before IS 'เดิม reserved_before — rename ให้สอดคล้อง reserved_qty';

--
-- Name: COLUMN inventory_ledger.reason; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.reason IS 'บังคับ ADJUST; SALE_CONFIRM=sale_confirm; RESTOCK_REFUND หลังคืนเงิน — authz: ADJUST ต้องมี reason (ck_inventory_ledger_adjust_reason)';

--
-- Name: COLUMN inventory_ledger.reference_type; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.reference_type IS 'ORDER|ORDER_ITEM|CHECKOUT|RESERVATION|IMPORT|MANUAL|REFUND';

--
-- Name: COLUMN inventory_ledger.reservation_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.reservation_id IS 'ลิงก์แถวจองที่เกี่ยวข้อง';

--
-- Name: COLUMN inventory_ledger.order_item_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.order_item_id IS 'บรรทัดออเดอร์ตอน SALE_CONFIRM / RELEASE';

--
-- Name: COLUMN inventory_ledger.idempotency_key; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.idempotency_key IS 'กันซ้ำ';

--
-- Name: COLUMN inventory_ledger.actor_type; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.actor_type IS 'USER|SERVICE|SYSTEM';

--
-- Name: COLUMN inventory_ledger.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN inventory_ledger.created_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN inventory_ledger.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN inventory_ledger.modified_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_ledger.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: inventory_ledger_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE inventory_ledger_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: inventory_ledger_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE inventory_ledger_id_seq OWNED BY inventory_ledger.id;

--
-- Name: inventory_levels; Type: TABLE
--

CREATE TABLE inventory_levels (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    on_hand integer DEFAULT 0 NOT NULL,
    reserved_qty integer DEFAULT 0 NOT NULL,
    available integer DEFAULT 0 NOT NULL,
    low_stock_threshold integer,
    version integer DEFAULT 0 NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE inventory_levels; Type: COMMENT
--

COMMENT ON TABLE inventory_levels IS '[Inventory] ยอดคงเหลือต่อ SKU';

--
-- Name: COLUMN inventory_levels.id; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.id IS 'ยอดคงเหลือต่อ SKU';

--
-- Name: COLUMN inventory_levels.sku_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.sku_id IS '1:1 กับ SKU';

--
-- Name: COLUMN inventory_levels.on_hand; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.on_hand IS 'ของจริงในคลัง (ลดเมื่อ merchant confirm / SALE_CONFIRM)';

--
-- Name: COLUMN inventory_levels.reserved_qty; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.reserved_qty IS 'จำนวนที่จองอยู่ (ACTIVE+PAID_HOLD); available = on_hand - reserved_qty';

--
-- Name: COLUMN inventory_levels.available; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.available IS 'stored consistently: on_hand - reserved_qty (อัปเดตใน TX เดียวกับ ledger)';

--
-- Name: COLUMN inventory_levels.version; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.version IS 'optimistic lock / fencing';

--
-- Name: COLUMN inventory_levels.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN inventory_levels.created_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN inventory_levels.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN inventory_levels.modified_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_levels.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: inventory_reservations; Type: TABLE
--

CREATE TABLE inventory_reservations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    cart_id uuid,
    checkout_id uuid,
    order_id uuid,
    order_item_id uuid,
    quantity integer NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    expires_at timestamp with time zone,
    reserved_at timestamp with time zone DEFAULT now() NOT NULL,
    paid_hold_at timestamp with time zone,
    committed_at timestamp with time zone,
    released_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_inventory_reservations_quantity CHECK ((quantity > 0)),
    CONSTRAINT ck_inventory_reservations_status CHECK ((status IN ('ACTIVE', 'PAID_HOLD', 'COMMITTED', 'RELEASED', 'EXPIRED')))
);

--
-- Name: TABLE inventory_reservations; Type: COMMENT
--

COMMENT ON TABLE inventory_reservations IS '[Inventory] จองสต็อกในแอป: checkout → pay hold → confirm deduct / cancel release';

--
-- Name: COLUMN inventory_reservations.id; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.id IS 'จองสต็อกในแอป: checkout → pay hold → confirm deduct / cancel release';

--
-- Name: COLUMN inventory_reservations.cart_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.cart_id IS 'จองช่วงใส่ตะกร้า/เริ่ม checkout (ถ้ามี)';

--
-- Name: COLUMN inventory_reservations.checkout_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.checkout_id IS 'จองตอน payment intent';

--
-- Name: COLUMN inventory_reservations.order_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.order_id IS 'ผูกออเดอร์หลังสร้าง';

--
-- Name: COLUMN inventory_reservations.order_item_id; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.order_item_id IS 'FK รายการออเดอร์ — ใช้ตอน deduct/release ต่อบรรทัด';

--
-- Name: COLUMN inventory_reservations.quantity; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.quantity IS '>0 (= reserved_qty ของแถวนี้)';

--
-- Name: COLUMN inventory_reservations.status; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.status IS 'ACTIVE|PAID_HOLD|COMMITTED|RELEASED|EXPIRED [CHECK enum: inventory_reservation_status]';

--
-- Name: COLUMN inventory_reservations.expires_at; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.expires_at IS 'หมดอายุถ้าไม่ชำระ / ไม่ confirm ตามนโยบาย';

--
-- Name: COLUMN inventory_reservations.reserved_at; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.reserved_at IS 'เวลา RESERVE';

--
-- Name: COLUMN inventory_reservations.paid_hold_at; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.paid_hold_at IS 'เวลาเปลี่ยนเป็น PAID_HOLD หลังจ่ายสำเร็จ';

--
-- Name: COLUMN inventory_reservations.committed_at; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.committed_at IS 'เวลา DEDUCT หลัง merchant confirm';

--
-- Name: COLUMN inventory_reservations.released_at; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.released_at IS 'เวลา RELEASE (cancel/expire)';

--
-- Name: COLUMN inventory_reservations.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN inventory_reservations.created_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN inventory_reservations.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN inventory_reservations.modified_by; Type: COMMENT
--

COMMENT ON COLUMN inventory_reservations.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: media_files; Type: TABLE
--

CREATE TABLE media_files (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    uploader_id uuid,
    purpose character varying(40) NOT NULL,
    storage_provider character varying(30) DEFAULT 'LOCAL'::character varying NOT NULL,
    storage_key character varying(700) NOT NULL,
    original_filename character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    sha256 character varying(64),
    width_px integer,
    height_px integer,
    visibility character varying(20) DEFAULT 'PRIVATE'::character varying NOT NULL,
    status character varying(30) DEFAULT 'READY'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    source_job_id uuid,
    required_permission character varying(80),
    expires_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_media_files_purpose CHECK ((purpose IN ('PRODUCT', 'SHOP_LOGO', 'SHOP_COVER', 'DOCUMENT', 'BLOG', 'REPORT', 'IMPORT', 'PAYMENT_SLIP', 'PACKING_PHOTO', 'DELIVERY_PROOF', 'OTHER')))
);

--
-- Name: TABLE media_files; Type: COMMENT
--

COMMENT ON TABLE media_files IS '[Media] ไฟล์สื่อกลาง';

--
-- Name: COLUMN media_files.id; Type: COMMENT
--

COMMENT ON COLUMN media_files.id IS 'ไฟล์สื่อกลาง';

--
-- Name: COLUMN media_files.shop_id; Type: COMMENT
--

COMMENT ON COLUMN media_files.shop_id IS 'null = platform-owned';

--
-- Name: COLUMN media_files.purpose; Type: COMMENT
--

COMMENT ON COLUMN media_files.purpose IS 'PRODUCT|SHOP_LOGO|SHOP_COVER|DOCUMENT|BLOG|REPORT|IMPORT|PAYMENT_SLIP|PACKING_PHOTO|DELIVERY_PROOF|OTHER (v3 +PAYMENT_SLIP, PACKING_PHOTO, DELIVERY_PROOF) [CHECK enum: media_purpose]';

--
-- Name: COLUMN media_files.storage_provider; Type: COMMENT
--

COMMENT ON COLUMN media_files.storage_provider IS 'LOCAL|S3|GCS';

--
-- Name: COLUMN media_files.visibility; Type: COMMENT
--

COMMENT ON COLUMN media_files.visibility IS 'PRIVATE|PUBLIC';

--
-- Name: COLUMN media_files.status; Type: COMMENT
--

COMMENT ON COLUMN media_files.status IS 'UPLOADING|READY|FAILED|DELETED';

--
-- Name: COLUMN media_files.source_job_id; Type: COMMENT
--

COMMENT ON COLUMN media_files.source_job_id IS 'authz: job ที่สร้างไฟล์ (report/export/import report)';

--
-- Name: COLUMN media_files.required_permission; Type: COMMENT
--

COMMENT ON COLUMN media_files.required_permission IS 'authz: permission ที่ต้องมี ณ เวลาดาวน์โหลด เช่น REPORT_EXPORT, ORDER_EXPORT';

--
-- Name: COLUMN media_files.expires_at; Type: COMMENT
--

COMMENT ON COLUMN media_files.expires_at IS 'authz: หมดอายุไฟล์ที่ระบบสร้าง (signed URL อายุสั้นเท่านั้น)';

--
-- Name: COLUMN media_files.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN media_files.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN media_files.created_by; Type: COMMENT
--

COMMENT ON COLUMN media_files.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN media_files.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN media_files.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN media_files.modified_by; Type: COMMENT
--

COMMENT ON COLUMN media_files.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: media_variants; Type: TABLE
--

CREATE TABLE media_variants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    media_file_id uuid NOT NULL,
    variant_version character varying(40) NOT NULL,
    storage_key character varying(700) NOT NULL,
    width_px integer,
    height_px integer,
    file_size bigint,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE media_variants; Type: COMMENT
--

COMMENT ON TABLE media_variants IS '[Media] image resize derivatives';

--
-- Name: COLUMN media_variants.id; Type: COMMENT
--

COMMENT ON COLUMN media_variants.id IS 'image resize derivatives';

--
-- Name: COLUMN media_variants.media_file_id; Type: COMMENT
--

COMMENT ON COLUMN media_variants.media_file_id IS 'UNIQUE(media_file_id,variant_version)';

--
-- Name: COLUMN media_variants.variant_version; Type: COMMENT
--

COMMENT ON COLUMN media_variants.variant_version IS 'thumb-v1|medium-v1';

--
-- Name: COLUMN media_variants.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN media_variants.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN media_variants.created_by; Type: COMMENT
--

COMMENT ON COLUMN media_variants.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN media_variants.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN media_variants.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN media_variants.modified_by; Type: COMMENT
--

COMMENT ON COLUMN media_variants.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: merchant_application_reviews; Type: TABLE
--

CREATE TABLE merchant_application_reviews (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    reviewer_id uuid NOT NULL,
    action character varying(20) NOT NULL,
    from_status character varying(30) NOT NULL,
    to_status character varying(30) NOT NULL,
    reason_code character varying(50),
    note text,
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    request_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_merchant_application_reviews_action CHECK ((action IN ('START_REVIEW', 'REQUEST_INFO', 'APPROVE', 'REJECT'))),
    CONSTRAINT ck_merchant_application_reviews_from_status CHECK ((from_status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_INFORMATION', 'APPROVED', 'REJECTED', 'WITHDRAWN'))),
    CONSTRAINT ck_merchant_application_reviews_to_status CHECK ((to_status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_INFORMATION', 'APPROVED', 'REJECTED', 'WITHDRAWN')))
);

--
-- Name: TABLE merchant_application_reviews; Type: COMMENT
--

COMMENT ON TABLE merchant_application_reviews IS '[Merchant Onboarding] authz: ประวัติการพิจารณาใบสมัครทุกรอบ (ผู้ตรวจ เหตุผล เวลา ผล) — append-only';

--
-- Name: COLUMN merchant_application_reviews.id; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.id IS 'authz: ประวัติการพิจารณาใบสมัครทุกรอบ (ผู้ตรวจ เหตุผล เวลา ผล) — append-only';

--
-- Name: COLUMN merchant_application_reviews.application_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.application_id IS 'ใบสมัคร';

--
-- Name: COLUMN merchant_application_reviews.reviewer_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.reviewer_id IS 'Platform Admin (PLATFORM_APPLICATION_REVIEW) — แอปตรวจว่า ≠ ผู้สมัคร';

--
-- Name: COLUMN merchant_application_reviews.action; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.action IS 'START_REVIEW|REQUEST_INFO|APPROVE|REJECT [CHECK enum: application_review_action]';

--
-- Name: COLUMN merchant_application_reviews.from_status; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.from_status IS 'สถานะก่อนพิจารณา (application_status) [CHECK enum: application_status]';

--
-- Name: COLUMN merchant_application_reviews.to_status; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.to_status IS 'สถานะหลังพิจารณา (application_status) [CHECK enum: application_status]';

--
-- Name: COLUMN merchant_application_reviews.reason_code; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.reason_code IS 'รหัสเหตุผล';

--
-- Name: COLUMN merchant_application_reviews.note; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.note IS 'หมายเหตุผู้ตรวจ (ห้าม PII เกินจำเป็น)';

--
-- Name: COLUMN merchant_application_reviews.decided_at; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.decided_at IS 'เวลาตัดสินใจ';

--
-- Name: COLUMN merchant_application_reviews.request_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.request_id IS 'correlation กับ audit_logs.request_id';

--
-- Name: COLUMN merchant_application_reviews.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN merchant_application_reviews.created_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN merchant_application_reviews.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN merchant_application_reviews.modified_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_application_reviews.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: merchant_applications; Type: TABLE
--

CREATE TABLE merchant_applications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    reference_no character varying(30),
    shop_name character varying(100),
    shop_name_en character varying(100),
    shop_description character varying(500),
    category character varying(100),
    sale_type character varying(30),
    website character varying(500),
    logo_path character varying(500),
    shop_address character varying(500),
    shop_province character varying(100),
    shop_district character varying(100),
    shop_subdistrict character varying(100),
    shop_postal_code character varying(10),
    owner_type character varying(30),
    owner_title character varying(30),
    owner_first_name character varying(100),
    owner_last_name character varying(100),
    citizen_id_encrypted text,
    birth_date date,
    nationality character varying(100),
    owner_phone character varying(30),
    owner_email character varying(255),
    owner_house_no character varying(50),
    owner_village_no character varying(50),
    owner_building character varying(255),
    owner_alley character varying(255),
    owner_road character varying(255),
    owner_province character varying(100),
    owner_district character varying(100),
    owner_subdistrict character varying(100),
    owner_postal_code character varying(10),
    bank_name character varying(100),
    bank_account_encrypted text,
    bank_account_name character varying(255),
    reviewer_id uuid,
    review_note text,
    shop_id uuid,
    submitted_at timestamp with time zone,
    reviewed_at timestamp with time zone,
    withdrawn_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_merchant_applications_owner_type CHECK ((owner_type IN ('PERSON', 'COMPANY'))),
    CONSTRAINT ck_merchant_applications_reviewer_not_applicant CHECK (((reviewer_id IS NULL) OR (reviewer_id <> user_id))),
    CONSTRAINT ck_merchant_applications_sale_type CHECK ((sale_type IN ('OWNER', 'RESELLER'))),
    CONSTRAINT ck_merchant_applications_status CHECK ((status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_INFORMATION', 'APPROVED', 'REJECTED', 'WITHDRAWN')))
);

--
-- Name: TABLE merchant_applications; Type: COMMENT
--

COMMENT ON TABLE merchant_applications IS '[Merchant Onboarding] ใบสมัครเปิดร้าน (align V2)';

--
-- Name: COLUMN merchant_applications.id; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.id IS 'ใบสมัครเปิดร้าน (align V2)';

--
-- Name: COLUMN merchant_applications.user_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.user_id IS 'ผู้สมัคร (จาก session เท่านั้น); authz: 1 ใบที่ยังดำเนินการต่อ user — partial unique uq_merchant_applications_user_id_open (DRAFT|SUBMITTED|UNDER_REVIEW|NEEDS_INFORMATION)';

--
-- Name: COLUMN merchant_applications.status; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.status IS 'DRAFT|SUBMITTED|UNDER_REVIEW|NEEDS_INFORMATION|APPROVED|REJECTED|WITHDRAWN [CHECK enum: application_status]';

--
-- Name: COLUMN merchant_applications.reference_no; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.reference_no IS 'เลขอ้างอิงสาธารณะ';

--
-- Name: COLUMN merchant_applications.shop_name; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.shop_name IS 'ชื่อร้าน (TH)';

--
-- Name: COLUMN merchant_applications.shop_name_en; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.shop_name_en IS 'ชื่อร้าน (EN)';

--
-- Name: COLUMN merchant_applications.category; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.category IS 'หมวดร้านเบื้องต้น';

--
-- Name: COLUMN merchant_applications.sale_type; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.sale_type IS 'OWNER|RESELLER [CHECK enum: sale_type]';

--
-- Name: COLUMN merchant_applications.owner_type; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.owner_type IS 'PERSON|COMPANY [CHECK enum: owner_type]';

--
-- Name: COLUMN merchant_applications.citizen_id_encrypted; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.citizen_id_encrypted IS 'เลขบัตรเข้ารหัส — ห้ามค้นด้วยค่าดิบ';

--
-- Name: COLUMN merchant_applications.bank_name; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.bank_name IS 'ข้อมูลธนาคารอ้างอิง (ไม่ใช่ settlement)';

--
-- Name: COLUMN merchant_applications.bank_account_encrypted; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.bank_account_encrypted IS 'เลขบัญชีเข้ารหัส';

--
-- Name: COLUMN merchant_applications.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN merchant_applications.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN merchant_applications.reviewer_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.reviewer_id IS 'Platform Admin ผู้ตรวจล่าสุด — ห้ามเท่ากับ user_id (ck_merchant_applications_reviewer_not_applicant)';

--
-- Name: COLUMN merchant_applications.review_note; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.review_note IS 'เหตุผลล่าสุด approve/reject/needs_info — ประวัติทุกรอบใน merchant_application_reviews';

--
-- Name: COLUMN merchant_applications.shop_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.shop_id IS 'ตั้งหลัง APPROVED (กันสร้างร้านซ้ำ)';

--
-- Name: COLUMN merchant_applications.withdrawn_at; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.withdrawn_at IS 'authz: เวลาที่ผู้สมัครถอน (SHOP_APPLICATION_WITHDRAW) — สถานะสิ้นสุด WITHDRAWN';

--
-- Name: COLUMN merchant_applications.created_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN merchant_applications.modified_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_applications.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: merchant_documents; Type: TABLE
--

CREATE TABLE merchant_documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    application_id uuid NOT NULL,
    document_type character varying(30) NOT NULL,
    original_filename character varying(255) NOT NULL,
    stored_filename character varying(255) NOT NULL,
    storage_path character varying(700) NOT NULL,
    content_type character varying(100) NOT NULL,
    file_size bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    uploaded_at timestamp with time zone DEFAULT now() NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_merchant_documents_document_type CHECK ((document_type IN ('LOGO', 'IDENTITY', 'PORTRAIT', 'ADDRESS', 'BUSINESS')))
);

--
-- Name: TABLE merchant_documents; Type: COMMENT
--

COMMENT ON TABLE merchant_documents IS '[Merchant Onboarding] merchant_documents — สมัครเปิดร้าน + เอกสาร KYC (align Flyway V2); authz: ประวัติการพิจารณาทุกรอบ, 1 ใบเปิดต่อ user, ห้าม self-review';

--
-- Name: COLUMN merchant_documents.application_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.application_id IS 'ON DELETE CASCADE';

--
-- Name: COLUMN merchant_documents.document_type; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.document_type IS 'LOGO|IDENTITY|PORTRAIT|ADDRESS|BUSINESS; UNIQUE(application_id,document_type) [CHECK enum: document_type]';

--
-- Name: COLUMN merchant_documents.storage_path; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.storage_path IS 'path ภายใต้ STORAGE_ROOT';

--
-- Name: COLUMN merchant_documents.file_size; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.file_size IS 'bytes';

--
-- Name: COLUMN merchant_documents.sha256; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.sha256 IS 'integrity checksum';

--
-- Name: COLUMN merchant_documents.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN merchant_documents.created_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN merchant_documents.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN merchant_documents.modified_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_documents.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: merchant_order_confirmations; Type: TABLE
--

CREATE TABLE merchant_order_confirmations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    confirmed_by_user_id uuid NOT NULL,
    action character varying(30) DEFAULT 'CONFIRM'::character varying NOT NULL,
    confirmed_at timestamp with time zone DEFAULT now() NOT NULL,
    note character varying(500),
    ip_address inet,
    user_agent character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_merchant_order_confirmations_action CHECK ((action IN ('CONFIRM', 'REJECT')))
);

--
-- Name: TABLE merchant_order_confirmations; Type: COMMENT
--

COMMENT ON TABLE merchant_order_confirmations IS '[Orders] audit: ร้านยืนยันออเดอร์';

--
-- Name: COLUMN merchant_order_confirmations.id; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.id IS 'audit: ร้านยืนยันออเดอร์';

--
-- Name: COLUMN merchant_order_confirmations.order_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.order_id IS '1 ออเดอร์อาจมีหลายแถวถ้า reject แล้ว confirm ใหม่ (ปกติ 1)';

--
-- Name: COLUMN merchant_order_confirmations.confirmed_by_user_id; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.confirmed_by_user_id IS 'สมาชิกร้านที่กดยืนยัน';

--
-- Name: COLUMN merchant_order_confirmations.action; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.action IS 'CONFIRM|REJECT [CHECK enum: merchant_confirm_action]';

--
-- Name: COLUMN merchant_order_confirmations.note; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.note IS 'เหตุผล reject หรือหมายเหตุ';

--
-- Name: COLUMN merchant_order_confirmations.ip_address; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.ip_address IS 'optional audit';

--
-- Name: COLUMN merchant_order_confirmations.user_agent; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.user_agent IS 'optional audit';

--
-- Name: COLUMN merchant_order_confirmations.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN merchant_order_confirmations.created_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN merchant_order_confirmations.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN merchant_order_confirmations.modified_by; Type: COMMENT
--

COMMENT ON COLUMN merchant_order_confirmations.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: notifications; Type: TABLE
--

CREATE TABLE notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    user_id uuid,
    channel character varying(30) DEFAULT 'EMAIL'::character varying NOT NULL,
    template_key character varying(80) NOT NULL,
    template_version integer DEFAULT 1 NOT NULL,
    payload_safe jsonb,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    provider_reference character varying(120),
    idempotency_key character varying(120),
    sent_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_notifications_status CHECK ((status IN ('PENDING', 'QUEUED', 'SENT', 'FAILED')))
);

--
-- Name: TABLE notifications; Type: COMMENT
--

COMMENT ON TABLE notifications IS '[Notifications & Jobs] เหตุการณ์แจ้งเตือนธุรกิจ';

--
-- Name: COLUMN notifications.id; Type: COMMENT
--

COMMENT ON COLUMN notifications.id IS 'เหตุการณ์แจ้งเตือนธุรกิจ';

--
-- Name: COLUMN notifications.user_id; Type: COMMENT
--

COMMENT ON COLUMN notifications.user_id IS 'ผู้รับ';

--
-- Name: COLUMN notifications.channel; Type: COMMENT
--

COMMENT ON COLUMN notifications.channel IS 'EMAIL|IN_APP|SMS';

--
-- Name: COLUMN notifications.template_key; Type: COMMENT
--

COMMENT ON COLUMN notifications.template_key IS 'เช่น order.confirmed.v1';

--
-- Name: COLUMN notifications.payload_safe; Type: COMMENT
--

COMMENT ON COLUMN notifications.payload_safe IS 'ไม่มี PII/secret';

--
-- Name: COLUMN notifications.status; Type: COMMENT
--

COMMENT ON COLUMN notifications.status IS 'PENDING|QUEUED|SENT|FAILED [CHECK enum: notification_status]';

--
-- Name: COLUMN notifications.idempotency_key; Type: COMMENT
--

COMMENT ON COLUMN notifications.idempotency_key IS 'dedup ตามเหตุการณ์+ผู้รับ';

--
-- Name: COLUMN notifications.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN notifications.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN notifications.created_by; Type: COMMENT
--

COMMENT ON COLUMN notifications.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN notifications.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN notifications.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN notifications.modified_by; Type: COMMENT
--

COMMENT ON COLUMN notifications.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: oms_connections; Type: TABLE
--

CREATE TABLE oms_connections (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    provider_name character varying(80) DEFAULT 'THAISHOPFUN_OMS'::character varying NOT NULL,
    external_shop_id character varying(100),
    api_base_url character varying(500),
    credentials_encrypted text,
    webhook_url character varying(500),
    webhook_secret_encrypted text,
    status character varying(30) DEFAULT 'INACTIVE'::character varying NOT NULL,
    last_heartbeat_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_oms_connections_status CHECK ((status IN ('INACTIVE', 'ACTIVE', 'ERROR')))
);

--
-- Name: TABLE oms_connections; Type: COMMENT
--

COMMENT ON TABLE oms_connections IS '[OMS Integration] การเชื่อมต่อร้าน↔OMS instance ภายนอก';

--
-- Name: COLUMN oms_connections.id; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.id IS 'การเชื่อมต่อร้าน↔OMS instance ภายนอก';

--
-- Name: COLUMN oms_connections.shop_id; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.shop_id IS '1 connection ต่อร้านใน Phase 1';

--
-- Name: COLUMN oms_connections.provider_name; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.provider_name IS 'ชื่อระบบ OMS ภายนอก';

--
-- Name: COLUMN oms_connections.external_shop_id; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.external_shop_id IS 'shop ID ใน OMS';

--
-- Name: COLUMN oms_connections.credentials_encrypted; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.credentials_encrypted IS 'API key/secret เข้ารหัส';

--
-- Name: COLUMN oms_connections.webhook_url; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.webhook_url IS 'endpoint ที่ Ecommerce รับ event จาก OMS';

--
-- Name: COLUMN oms_connections.status; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.status IS 'INACTIVE|ACTIVE|ERROR [CHECK enum: oms_connection_status]';

--
-- Name: COLUMN oms_connections.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN oms_connections.created_by; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN oms_connections.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN oms_connections.modified_by; Type: COMMENT
--

COMMENT ON COLUMN oms_connections.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: oms_order_links; Type: TABLE
--

CREATE TABLE oms_order_links (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    order_id uuid NOT NULL,
    oms_connection_id uuid NOT NULL,
    external_order_id character varying(100) NOT NULL,
    external_channel character varying(40),
    external_channel_order_ref character varying(100),
    sync_direction character varying(20) DEFAULT 'PUSH'::character varying NOT NULL,
    sync_status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    last_synced_at timestamp with time zone,
    last_error_code character varying(64),
    last_error_message character varying(500),
    external_payload_hash character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_oms_order_links_sync_status CHECK ((sync_status IN ('NOT_SYNCED', 'PENDING', 'SYNCED', 'FAILED', 'STALE', 'IGNORED')))
);

--
-- Name: TABLE oms_order_links; Type: COMMENT
--

COMMENT ON TABLE oms_order_links IS '[OMS Integration] ผูกออเดอร์ Ecommerce ↔ OMS';

--
-- Name: COLUMN oms_order_links.id; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.id IS 'ผูกออเดอร์ Ecommerce ↔ OMS';

--
-- Name: COLUMN oms_order_links.order_id; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.order_id IS '1:1 กับ orders';

--
-- Name: COLUMN oms_order_links.external_order_id; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.external_order_id IS 'OMS order ID; UNIQUE(oms_connection_id,external_order_id)';

--
-- Name: COLUMN oms_order_links.external_channel; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.external_channel IS 'ช่องทางใน OMS';

--
-- Name: COLUMN oms_order_links.external_channel_order_ref; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.external_channel_order_ref IS 'เลข Shopee/TikTok ฯลฯ';

--
-- Name: COLUMN oms_order_links.sync_direction; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.sync_direction IS 'PUSH|PULL|BIDIRECTIONAL';

--
-- Name: COLUMN oms_order_links.sync_status; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.sync_status IS 'PENDING|SYNCED|FAILED|STALE|IGNORED [CHECK enum: oms_sync_status]';

--
-- Name: COLUMN oms_order_links.external_payload_hash; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.external_payload_hash IS 'detect stale';

--
-- Name: COLUMN oms_order_links.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN oms_order_links.created_by; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN oms_order_links.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN oms_order_links.modified_by; Type: COMMENT
--

COMMENT ON COLUMN oms_order_links.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: oms_shipment_links; Type: TABLE
--

CREATE TABLE oms_shipment_links (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    shipment_id uuid NOT NULL,
    oms_connection_id uuid NOT NULL,
    external_shipment_id character varying(100) NOT NULL,
    external_tracking_number character varying(100),
    label_external_url character varying(700),
    sync_status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    last_synced_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_oms_shipment_links_sync_status CHECK ((sync_status IN ('NOT_SYNCED', 'PENDING', 'SYNCED', 'FAILED', 'STALE', 'IGNORED')))
);

--
-- Name: TABLE oms_shipment_links; Type: COMMENT
--

COMMENT ON TABLE oms_shipment_links IS '[OMS Integration] ผูก shipment ↔ OMS';

--
-- Name: COLUMN oms_shipment_links.id; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.id IS 'ผูก shipment ↔ OMS';

--
-- Name: COLUMN oms_shipment_links.external_shipment_id; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.external_shipment_id IS 'UNIQUE(oms_connection_id,external_shipment_id)';

--
-- Name: COLUMN oms_shipment_links.label_external_url; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.label_external_url IS 'URL label จาก OMS/carrier';

--
-- Name: COLUMN oms_shipment_links.sync_status; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.sync_status IS '[CHECK enum: oms_sync_status]';

--
-- Name: COLUMN oms_shipment_links.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN oms_shipment_links.created_by; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN oms_shipment_links.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN oms_shipment_links.modified_by; Type: COMMENT
--

COMMENT ON COLUMN oms_shipment_links.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: oms_sync_checkpoints; Type: TABLE
--

CREATE TABLE oms_sync_checkpoints (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    oms_connection_id uuid NOT NULL,
    resource_type character varying(40) NOT NULL,
    cursor_value character varying(200),
    last_success_at timestamp with time zone,
    last_attempt_at timestamp with time zone,
    last_error character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE oms_sync_checkpoints; Type: COMMENT
--

COMMENT ON TABLE oms_sync_checkpoints IS '[OMS Integration] cursor สำหรับ pull sync จาก OMS';

--
-- Name: COLUMN oms_sync_checkpoints.id; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.id IS 'cursor สำหรับ pull sync จาก OMS';

--
-- Name: COLUMN oms_sync_checkpoints.resource_type; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.resource_type IS 'ORDERS|SHIPMENTS|INVENTORY|PRODUCTS';

--
-- Name: COLUMN oms_sync_checkpoints.cursor_value; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.cursor_value IS 'หน้า/timestamp/ID ล่าสุด';

--
-- Name: COLUMN oms_sync_checkpoints.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN oms_sync_checkpoints.created_by; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN oms_sync_checkpoints.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN oms_sync_checkpoints.modified_by; Type: COMMENT
--

COMMENT ON COLUMN oms_sync_checkpoints.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: oms_webhook_inbox; Type: TABLE
--

CREATE TABLE oms_webhook_inbox (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    oms_connection_id uuid,
    shop_id uuid,
    external_event_id character varying(120) NOT NULL,
    event_type character varying(80) NOT NULL,
    external_order_id character varying(100),
    external_shipment_id character varying(100),
    payload jsonb NOT NULL,
    signature_valid boolean DEFAULT false NOT NULL,
    process_status character varying(30) DEFAULT 'RECEIVED'::character varying NOT NULL,
    processed_at timestamp with time zone,
    error_message character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_oms_webhook_inbox_process_status CHECK ((process_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')))
);

--
-- Name: TABLE oms_webhook_inbox; Type: COMMENT
--

COMMENT ON TABLE oms_webhook_inbox IS '[OMS Integration] รับ event จาก OMS (at-least-once)';

--
-- Name: COLUMN oms_webhook_inbox.id; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.id IS 'รับ event จาก OMS (at-least-once)';

--
-- Name: COLUMN oms_webhook_inbox.external_event_id; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.external_event_id IS 'dedup; UNIQUE(oms_connection_id,external_event_id)';

--
-- Name: COLUMN oms_webhook_inbox.event_type; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.event_type IS 'order.status_changed|shipment.updated|inventory.adjusted|label.ready';

--
-- Name: COLUMN oms_webhook_inbox.payload; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.payload IS 'payload จาก OMS (sanitize)';

--
-- Name: COLUMN oms_webhook_inbox.process_status; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.process_status IS 'RECEIVED|PROCESSED|IGNORED|FAILED [CHECK enum: webhook_process_status]';

--
-- Name: COLUMN oms_webhook_inbox.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN oms_webhook_inbox.created_by; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN oms_webhook_inbox.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN oms_webhook_inbox.modified_by; Type: COMMENT
--

COMMENT ON COLUMN oms_webhook_inbox.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_addresses; Type: TABLE
--

CREATE TABLE order_addresses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    address_type character varying(20) NOT NULL,
    recipient_name character varying(150) NOT NULL,
    phone character varying(30) NOT NULL,
    address_line1 character varying(255) NOT NULL,
    address_line2 character varying(255),
    subdistrict character varying(100),
    district character varying(100) NOT NULL,
    province character varying(100) NOT NULL,
    postal_code character varying(10) NOT NULL,
    country_code character(2) DEFAULT 'TH'::bpchar NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE order_addresses; Type: COMMENT
--

COMMENT ON TABLE order_addresses IS '[Orders] ที่อยู่ snapshot ของออเดอร์';

--
-- Name: COLUMN order_addresses.id; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.id IS 'ที่อยู่ snapshot ของออเดอร์';

--
-- Name: COLUMN order_addresses.order_id; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.order_id IS 'UNIQUE(order_id,address_type)';

--
-- Name: COLUMN order_addresses.address_type; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.address_type IS 'SHIPPING|BILLING';

--
-- Name: COLUMN order_addresses.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN order_addresses.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_addresses.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN order_addresses.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_addresses.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_cancellations; Type: TABLE
--

CREATE TABLE order_cancellations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    requester_type character varying(30) NOT NULL,
    reason_code character varying(50) NOT NULL,
    reason_note text,
    status character varying(30) DEFAULT 'REQUESTED'::character varying NOT NULL,
    processed_by uuid,
    processed_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_order_cancellations_status CHECK ((status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED')))
);

--
-- Name: TABLE order_cancellations; Type: COMMENT
--

COMMENT ON TABLE order_cancellations IS '[Returns & Cancellations] order_cancellations — ยกเลิก/คืนสินค้า/คืนเงินผ่าน PG ร้าน';

--
-- Name: COLUMN order_cancellations.order_id; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.order_id IS '1 cancel request ต่อออเดอร์';

--
-- Name: COLUMN order_cancellations.requester_type; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.requester_type IS 'BUYER|STORE|PLATFORM|SYSTEM';

--
-- Name: COLUMN order_cancellations.status; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.status IS 'REQUESTED|APPROVED|REJECTED|COMPLETED [CHECK enum: cancel_status]';

--
-- Name: COLUMN order_cancellations.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN order_cancellations.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_cancellations.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN order_cancellations.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_cancellations.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_financial_snapshots; Type: TABLE
--

CREATE TABLE order_financial_snapshots (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    subtotal numeric(18,2) DEFAULT 0 NOT NULL,
    discount_total numeric(18,2) DEFAULT 0 NOT NULL,
    shipping_fee numeric(18,2) DEFAULT 0 NOT NULL,
    tax_total numeric(18,2) DEFAULT 0 NOT NULL,
    grand_total numeric(18,2) DEFAULT 0 NOT NULL,
    item_count integer DEFAULT 0 NOT NULL,
    total_qty integer DEFAULT 0 NOT NULL,
    payment_status character varying(30) DEFAULT 'UNPAID'::character varying NOT NULL,
    paid_at timestamp with time zone,
    buyer_province_hint character varying(100),
    buyer_district_hint character varying(100),
    buyer_postal_hint character varying(10),
    snapshot_at timestamp with time zone DEFAULT now() NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_order_financial_snapshots_payment_status CHECK ((payment_status IN ('UNPAID', 'PENDING', 'PAID', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED')))
);

--
-- Name: TABLE order_financial_snapshots; Type: COMMENT
--

COMMENT ON TABLE order_financial_snapshots IS '[Orders] ยอดเงิน denorm สำหรับ seller list — ไม่ต้อง join order_items';

--
-- Name: COLUMN order_financial_snapshots.id; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.id IS 'ยอดเงิน denorm สำหรับ seller list — ไม่ต้อง join order_items';

--
-- Name: COLUMN order_financial_snapshots.order_id; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.order_id IS '1:1 กับ orders';

--
-- Name: COLUMN order_financial_snapshots.shop_id; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.shop_id IS 'tenant';

--
-- Name: COLUMN order_financial_snapshots.item_count; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.item_count IS 'จำนวนบรรทัด (ไม่เปิดเผย SKU)';

--
-- Name: COLUMN order_financial_snapshots.total_qty; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.total_qty IS 'จำนวนชิ้นรวม (ไม่เปิดเผยรายการ)';

--
-- Name: COLUMN order_financial_snapshots.payment_status; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.payment_status IS 'copy จาก orders.payment_status [CHECK enum: payment_status (order)]';

--
-- Name: COLUMN order_financial_snapshots.buyer_province_hint; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.buyer_province_hint IS 'จังหวัดผู้ซื้อ — เปิดก่อน confirm (ไม่ใช่ที่อยู่เต็ม)';

--
-- Name: COLUMN order_financial_snapshots.buyer_district_hint; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.buyer_district_hint IS 'อำเภอ/เขต — logistics hint ก่อน confirm';

--
-- Name: COLUMN order_financial_snapshots.buyer_postal_hint; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.buyer_postal_hint IS 'รหัสไปรษณีย์ — hint ก่อน confirm (ไม่เปิดชื่อ/โทร/บ้านเลขที่)';

--
-- Name: COLUMN order_financial_snapshots.snapshot_at; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.snapshot_at IS 'เวลา snapshot (สร้างตอน place/pay)';

--
-- Name: COLUMN order_financial_snapshots.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN order_financial_snapshots.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_financial_snapshots.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN order_financial_snapshots.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_financial_snapshots.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_item_reveal_logs; Type: TABLE
--

CREATE TABLE order_item_reveal_logs (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    revealed_by_user_id uuid,
    reveal_reason character varying(40) DEFAULT 'MERCHANT_CONFIRM'::character varying NOT NULL,
    revealed_at timestamp with time zone DEFAULT now() NOT NULL,
    request_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_order_item_reveal_logs_reveal_reason CHECK ((reveal_reason IN ('MERCHANT_CONFIRM', 'ADMIN_OVERRIDE', 'SYSTEM')))
);

--
-- Name: TABLE order_item_reveal_logs; Type: COMMENT
--

COMMENT ON TABLE order_item_reveal_logs IS '[Orders] audit เมื่อเปิดเผย line items ให้ seller';

--
-- Name: COLUMN order_item_reveal_logs.id; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.id IS 'audit เมื่อเปิดเผย line items ให้ seller';

--
-- Name: COLUMN order_item_reveal_logs.revealed_by_user_id; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.revealed_by_user_id IS 'null ถ้า system (auto หลัง confirm)';

--
-- Name: COLUMN order_item_reveal_logs.reveal_reason; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.reveal_reason IS 'MERCHANT_CONFIRM|ADMIN_OVERRIDE|SYSTEM [CHECK enum: item_reveal_reason]';

--
-- Name: COLUMN order_item_reveal_logs.request_id; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.request_id IS 'correlation';

--
-- Name: COLUMN order_item_reveal_logs.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN order_item_reveal_logs.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_item_reveal_logs.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN order_item_reveal_logs.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_item_reveal_logs.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_item_reveal_logs_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE order_item_reveal_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: order_item_reveal_logs_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE order_item_reveal_logs_id_seq OWNED BY order_item_reveal_logs.id;

--
-- Name: order_items; Type: TABLE
--

CREATE TABLE order_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    product_id uuid,
    sku_id uuid,
    sku_code_snapshot character varying(64) NOT NULL,
    product_name_snapshot character varying(255) NOT NULL,
    option_label_snapshot character varying(255),
    unit_price numeric(18,2) NOT NULL,
    quantity integer NOT NULL,
    discount_amount numeric(18,2) DEFAULT 0 NOT NULL,
    line_total numeric(18,2) NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    image_url_snapshot character varying(700),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_order_items_quantity CHECK ((quantity > 0))
);

--
-- Name: TABLE order_items; Type: COMMENT
--

COMMENT ON TABLE order_items IS '[Orders] source of truth ของรายการ — seller UI ห้าม SELECT จนกว่า merchant_confirmed_at';

--
-- Name: COLUMN order_items.id; Type: COMMENT
--

COMMENT ON COLUMN order_items.id IS 'source of truth ของรายการ — seller UI ห้าม SELECT จนกว่า merchant_confirmed_at';

--
-- Name: COLUMN order_items.order_id; Type: COMMENT
--

COMMENT ON COLUMN order_items.order_id IS 'ON DELETE RESTRICT';

--
-- Name: COLUMN order_items.shop_id; Type: COMMENT
--

COMMENT ON COLUMN order_items.shop_id IS 'denorm tenant';

--
-- Name: COLUMN order_items.product_id; Type: COMMENT
--

COMMENT ON COLUMN order_items.product_id IS 'nullable ถ้าสินค้าถูกลบภายหลัง';

--
-- Name: COLUMN order_items.unit_price; Type: COMMENT
--

COMMENT ON COLUMN order_items.unit_price IS 'ราคาต่อชิ้นตอนสั่ง';

--
-- Name: COLUMN order_items.quantity; Type: COMMENT
--

COMMENT ON COLUMN order_items.quantity IS '>0';

--
-- Name: COLUMN order_items.line_total; Type: COMMENT
--

COMMENT ON COLUMN order_items.line_total IS '(unit_price*qty)-discount';

--
-- Name: COLUMN order_items.image_url_snapshot; Type: COMMENT
--

COMMENT ON COLUMN order_items.image_url_snapshot IS 'รูปสินค้าตอนสั่ง — เปิดเผยหลัง merchant confirm เท่านั้น';

--
-- Name: COLUMN order_items.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_items.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN order_items.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_items.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_items.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_items.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN order_items.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_items.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_status_history; Type: TABLE
--

CREATE TABLE order_status_history (
    id bigint NOT NULL,
    order_id uuid NOT NULL,
    from_status character varying(40),
    to_status character varying(40) NOT NULL,
    changed_by uuid,
    changed_by_type character varying(30),
    reason character varying(500),
    request_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_order_status_history_from_status CHECK ((from_status IN ('PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED', 'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'DRAFT', 'CONFIRMED', 'PAID'))),
    CONSTRAINT ck_order_status_history_to_status CHECK ((to_status IN ('PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED', 'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'DRAFT', 'CONFIRMED', 'PAID')))
);

--
-- Name: TABLE order_status_history; Type: COMMENT
--

COMMENT ON TABLE order_status_history IS '[Orders] ประวัติเปลี่ยนสถานะ';

--
-- Name: COLUMN order_status_history.id; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.id IS 'ประวัติเปลี่ยนสถานะ';

--
-- Name: COLUMN order_status_history.from_status; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.from_status IS '[CHECK enum: order_status + order_status (legacy)]';

--
-- Name: COLUMN order_status_history.to_status; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.to_status IS '[CHECK enum: order_status + order_status (legacy)]';

--
-- Name: COLUMN order_status_history.changed_by_type; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.changed_by_type IS 'USER|BUYER|SYSTEM|OMS|WEBHOOK';

--
-- Name: COLUMN order_status_history.request_id; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.request_id IS 'correlation';

--
-- Name: COLUMN order_status_history.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN order_status_history.created_by; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN order_status_history.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN order_status_history.modified_by; Type: COMMENT
--

COMMENT ON COLUMN order_status_history.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: order_status_history_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE order_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: order_status_history_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE order_status_history_id_seq OWNED BY order_status_history.id;

--
-- Name: orders; Type: TABLE
--

CREATE TABLE orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    order_number character varying(40) NOT NULL,
    buyer_user_id uuid,
    shop_customer_id uuid,
    channel character varying(40) DEFAULT 'STOREFRONT'::character varying NOT NULL,
    status character varying(40) DEFAULT 'PENDING_PAYMENT'::character varying NOT NULL,
    payment_status character varying(30) DEFAULT 'UNPAID'::character varying NOT NULL,
    fulfillment_status character varying(30) DEFAULT 'UNFULFILLED'::character varying NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    subtotal numeric(18,2) DEFAULT 0 NOT NULL,
    discount_total numeric(18,2) DEFAULT 0 NOT NULL,
    shipping_fee numeric(18,2) DEFAULT 0 NOT NULL,
    tax_total numeric(18,2) DEFAULT 0 NOT NULL,
    grand_total numeric(18,2) DEFAULT 0 NOT NULL,
    coupon_code character varying(64),
    buyer_note text,
    internal_note text,
    idempotency_key character varying(120),
    placed_at timestamp with time zone,
    paid_at timestamp with time zone,
    merchant_confirm_status character varying(30) DEFAULT 'NOT_REQUIRED'::character varying NOT NULL,
    merchant_confirmed_at timestamp with time zone,
    items_revealed_at timestamp with time zone,
    confirm_deadline_at timestamp with time zone,
    buyer_received_at timestamp with time zone,
    auto_complete_at timestamp with time zone,
    completed_at timestamp with time zone,
    completion_source character varying(20),
    cancelled_at timestamp with time zone,
    cancel_reason character varying(500),
    oms_external_order_id character varying(100),
    oms_sync_status character varying(30) DEFAULT 'NOT_SYNCED'::character varying NOT NULL,
    oms_synced_at timestamp with time zone,
    oms_channel_ref character varying(100),
    row_version integer DEFAULT 0 NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_orders_channel CHECK ((channel IN ('STOREFRONT', 'SHOPEE', 'TIKTOK', 'LAZADA', 'LINE_OA', 'MANUAL', 'OMS_IMPORT'))),
    CONSTRAINT ck_orders_completion_source CHECK ((completion_source IN ('BUYER_CONFIRM', 'AUTO', 'ADMIN'))),
    CONSTRAINT ck_orders_fulfillment_status CHECK ((fulfillment_status IN ('UNFULFILLED', 'PARTIAL', 'FULFILLED', 'RETURNED'))),
    CONSTRAINT ck_orders_merchant_confirm_status CHECK ((merchant_confirm_status IN ('NOT_REQUIRED', 'AWAITING', 'CONFIRMED', 'REJECTED', 'EXPIRED'))),
    CONSTRAINT ck_orders_oms_sync_status CHECK ((oms_sync_status IN ('NOT_SYNCED', 'PENDING', 'SYNCED', 'FAILED', 'STALE', 'IGNORED'))),
    CONSTRAINT ck_orders_payment_status CHECK ((payment_status IN ('UNPAID', 'PENDING', 'PAID', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED'))),
    CONSTRAINT ck_orders_status CHECK ((status IN ('PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED', 'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDED')))
);

--
-- Name: TABLE orders; Type: COMMENT
--

COMMENT ON TABLE orders IS '[Orders] ออเดอร์หลัก — multi-channel ready';

--
-- Name: COLUMN orders.id; Type: COMMENT
--

COMMENT ON COLUMN orders.id IS 'ออเดอร์หลัก — multi-channel ready';

--
-- Name: COLUMN orders.shop_id; Type: COMMENT
--

COMMENT ON COLUMN orders.shop_id IS 'tenant; idx(shop_id,created_date_time)';

--
-- Name: COLUMN orders.order_number; Type: COMMENT
--

COMMENT ON COLUMN orders.order_number IS 'เลขออเดอร์สาธารณะ; UNIQUE(shop_id,order_number)';

--
-- Name: COLUMN orders.buyer_user_id; Type: COMMENT
--

COMMENT ON COLUMN orders.buyer_user_id IS 'null ได้ถ้าออเดอร์จากช่องทางภายนอกผ่าน OMS';

--
-- Name: COLUMN orders.shop_customer_id; Type: COMMENT
--

COMMENT ON COLUMN orders.shop_customer_id IS 'authz: ลูกค้าระดับร้าน (ออเดอร์ MANUAL/OMS หรือ storefront ที่ผูกแล้ว)';

--
-- Name: COLUMN orders.channel; Type: COMMENT
--

COMMENT ON COLUMN orders.channel IS 'STOREFRONT|SHOPEE|TIKTOK|LAZADA|LINE_OA|MANUAL|OMS_IMPORT [CHECK enum: order_channel]';

--
-- Name: COLUMN orders.status; Type: COMMENT
--

COMMENT ON COLUMN orders.status IS 'PENDING_PAYMENT|PAID_AWAITING_MERCHANT_CONFIRM|MERCHANT_CONFIRMED|READY_TO_PACK|PACKING|PACKED|DISPATCHED|DELIVERED|COMPLETED|CANCELLED|REFUNDED (v3: COMPLETED หลัง DELIVERED) [CHECK enum: order_status]';

--
-- Name: COLUMN orders.payment_status; Type: COMMENT
--

COMMENT ON COLUMN orders.payment_status IS 'UNPAID|PENDING|PAID|FAILED|REFUNDED|PARTIALLY_REFUNDED [CHECK enum: payment_status (order)]';

--
-- Name: COLUMN orders.fulfillment_status; Type: COMMENT
--

COMMENT ON COLUMN orders.fulfillment_status IS 'UNFULFILLED|PARTIAL|FULFILLED|RETURNED [CHECK enum: fulfillment_status]';

--
-- Name: COLUMN orders.buyer_note; Type: COMMENT
--

COMMENT ON COLUMN orders.buyer_note IS 'หมายเหตุจากลูกค้า';

--
-- Name: COLUMN orders.internal_note; Type: COMMENT
--

COMMENT ON COLUMN orders.internal_note IS 'หมายเหตุภายในร้าน — ไม่ expose buyer';

--
-- Name: COLUMN orders.idempotency_key; Type: COMMENT
--

COMMENT ON COLUMN orders.idempotency_key IS 'จาก checkout';

--
-- Name: COLUMN orders.placed_at; Type: COMMENT
--

COMMENT ON COLUMN orders.placed_at IS 'เวลายืนยันออเดอร์';

--
-- Name: COLUMN orders.paid_at; Type: COMMENT
--

COMMENT ON COLUMN orders.paid_at IS 'ตั้งเมื่อ payment CAPTURED → status=PAID_AWAITING_MERCHANT_CONFIRM';

--
-- Name: COLUMN orders.merchant_confirm_status; Type: COMMENT
--

COMMENT ON COLUMN orders.merchant_confirm_status IS 'NOT_REQUIRED|AWAITING|CONFIRMED|REJECTED|EXPIRED — storefront ใช้ AWAITING หลังจ่าย [CHECK enum: merchant_confirm_status]';

--
-- Name: COLUMN orders.merchant_confirmed_at; Type: COMMENT
--

COMMENT ON COLUMN orders.merchant_confirmed_at IS 'ร้านกดยืนยันออเดอร์ → เปิดเผย line items + ตัดสต็อก + push OMS';

--
-- Name: COLUMN orders.items_revealed_at; Type: COMMENT
--

COMMENT ON COLUMN orders.items_revealed_at IS 'เมื่อไหร่ที่ seller UI เปิดเห็น SKU/qty (ปกติ = merchant_confirmed_at)';

--
-- Name: COLUMN orders.confirm_deadline_at; Type: COMMENT
--

COMMENT ON COLUMN orders.confirm_deadline_at IS 'หมดเวลายืนยันของร้าน (ถ้ามี SLA) → expire/auto-cancel ตามนโยบาย';

--
-- Name: COLUMN orders.buyer_received_at; Type: COMMENT
--

COMMENT ON COLUMN orders.buyer_received_at IS 'v3: ลูกค้ากด ''ได้รับสินค้าแล้ว'' (null ถ้า auto-complete)';

--
-- Name: COLUMN orders.auto_complete_at; Type: COMMENT
--

COMMENT ON COLUMN orders.auto_complete_at IS 'v3: กำหนด auto-complete = shipments.delivered_at + N วัน (platform_settings); job ปิดออเดอร์';

--
-- Name: COLUMN orders.completed_at; Type: COMMENT
--

COMMENT ON COLUMN orders.completed_at IS 'v3: เวลาเข้า status=COMPLETED';

--
-- Name: COLUMN orders.completion_source; Type: COMMENT
--

COMMENT ON COLUMN orders.completion_source IS 'v3: BUYER_CONFIRM|AUTO|ADMIN — null จน COMPLETED [CHECK enum: completion_source]';

--
-- Name: COLUMN orders.oms_external_order_id; Type: COMMENT
--

COMMENT ON COLUMN orders.oms_external_order_id IS 'OMS plug: ID ออเดอร์ในระบบ OMS ภายนอก';

--
-- Name: COLUMN orders.oms_sync_status; Type: COMMENT
--

COMMENT ON COLUMN orders.oms_sync_status IS 'OMS plug: NOT_SYNCED|PENDING|SYNCED|FAILED|STALE [CHECK enum: oms_sync_status]';

--
-- Name: COLUMN orders.oms_synced_at; Type: COMMENT
--

COMMENT ON COLUMN orders.oms_synced_at IS 'OMS plug: sync ล่าสุด';

--
-- Name: COLUMN orders.oms_channel_ref; Type: COMMENT
--

COMMENT ON COLUMN orders.oms_channel_ref IS 'OMS plug: เลขอ้างอิงช่องทางต้นทาง';

--
-- Name: COLUMN orders.row_version; Type: COMMENT
--

COMMENT ON COLUMN orders.row_version IS 'authz: optimistic lock (JPA @Version) — ORDER_UPDATE แข่งกับการชำระ/สถานะ → 409 INVALID_STATE';

--
-- Name: COLUMN orders.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN orders.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN orders.created_by; Type: COMMENT
--

COMMENT ON COLUMN orders.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook — buyer หรือ staff';

--
-- Name: COLUMN orders.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN orders.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN orders.modified_by; Type: COMMENT
--

COMMENT ON COLUMN orders.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: otp_verifications; Type: TABLE
--

CREATE TABLE otp_verifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    channel character varying(20) NOT NULL,
    destination_masked character varying(255) NOT NULL,
    destination_hash character varying(64) NOT NULL,
    purpose character varying(30) NOT NULL,
    code_hash character varying(255) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    verified_at timestamp with time zone,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    ip_address inet,
    step_up_action character varying(80),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_otp_verifications_channel CHECK ((channel IN ('SMS', 'EMAIL'))),
    CONSTRAINT ck_otp_verifications_purpose CHECK ((purpose IN ('SIGNUP', 'LOGIN', 'PHONE_VERIFY', 'EMAIL_VERIFY', 'PASSWORD_RESET', 'STEP_UP'))),
    CONSTRAINT ck_otp_verifications_status CHECK ((status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'FAILED')))
);

--
-- Name: TABLE otp_verifications; Type: COMMENT
--

COMMENT ON TABLE otp_verifications IS '[Auth & Identity] v3: ประวัติการส่ง/ยืนยัน OTP — หลักฐานขั้น signup+verify';

--
-- Name: COLUMN otp_verifications.id; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.id IS 'v3: ประวัติการส่ง/ยืนยัน OTP — หลักฐานขั้น signup+verify';

--
-- Name: COLUMN otp_verifications.user_id; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.user_id IS 'null ได้ (ขอ OTP ก่อนสร้าง user / pre-signup)';

--
-- Name: COLUMN otp_verifications.channel; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.channel IS 'SMS|EMAIL [CHECK enum: otp_channel]';

--
-- Name: COLUMN otp_verifications.destination_masked; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.destination_masked IS 'เช่น 08x-xxx-1234 / s***@mail.com — แสดงผล/ตรวจสอบ';

--
-- Name: COLUMN otp_verifications.destination_hash; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.destination_hash IS 'SHA-256(normalized phone/email) — rate-limit/lookup ไม่เก็บค่าดิบ; idx(destination_hash,purpose,created_date_time)';

--
-- Name: COLUMN otp_verifications.purpose; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.purpose IS 'SIGNUP|LOGIN|PHONE_VERIFY|EMAIL_VERIFY|PASSWORD_RESET|STEP_UP [CHECK enum: otp_purpose]';

--
-- Name: COLUMN otp_verifications.code_hash; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.code_hash IS 'hash ของรหัส OTP — ห้ามเก็บ/ log รหัสจริง';

--
-- Name: COLUMN otp_verifications.attempts; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.attempts IS 'จำนวนครั้งที่กรอก';

--
-- Name: COLUMN otp_verifications.max_attempts; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.max_attempts IS 'เกิน → FAILED';

--
-- Name: COLUMN otp_verifications.expires_at; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.expires_at IS 'เช่น now()+5 นาที; เลย → EXPIRED';

--
-- Name: COLUMN otp_verifications.verified_at; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.verified_at IS 'ยืนยันสำเร็จ → ตั้ง users.phone_verified_at / email_verified_at';

--
-- Name: COLUMN otp_verifications.status; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.status IS 'PENDING|VERIFIED|EXPIRED|FAILED [CHECK enum: otp_status]';

--
-- Name: COLUMN otp_verifications.ip_address; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.ip_address IS 'IP ผู้ขอ — anti-abuse';

--
-- Name: COLUMN otp_verifications.step_up_action; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.step_up_action IS 'authz: permission/action ที่ต้องยืนยันตัวตนซ้ำ เมื่อ purpose=STEP_UP เช่น OWNERSHIP_TRANSFER, PLATFORM_ACCOUNT_SUSPEND, PLATFORM_SETTING_UPDATE';

--
-- Name: COLUMN otp_verifications.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN otp_verifications.created_by; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN otp_verifications.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN otp_verifications.modified_by; Type: COMMENT
--

COMMENT ON COLUMN otp_verifications.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: outbox_events; Type: TABLE
--

CREATE TABLE outbox_events (
    id bigint NOT NULL,
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    aggregate_type character varying(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    payload jsonb NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    publish_attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    published_at timestamp with time zone,
    correlation_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_outbox_events_status CHECK ((status IN ('PENDING', 'PUBLISHED', 'FAILED')))
);

--
-- Name: TABLE outbox_events; Type: COMMENT
--

COMMENT ON TABLE outbox_events IS '[Notifications & Jobs] Transactional outbox — durable dispatch';

--
-- Name: COLUMN outbox_events.id; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.id IS 'Transactional outbox — durable dispatch';

--
-- Name: COLUMN outbox_events.event_id; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.event_id IS 'idempotent publish key';

--
-- Name: COLUMN outbox_events.aggregate_type; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.aggregate_type IS 'ORDER|PAYMENT|SHIPMENT|PRODUCT|INVENTORY|OMS';

--
-- Name: COLUMN outbox_events.event_type; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.event_type IS 'order.created|order.paid|shipment.dispatched|oms.order.push';

--
-- Name: COLUMN outbox_events.payload; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.payload IS 'reference + minimal fields';

--
-- Name: COLUMN outbox_events.status; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.status IS 'PENDING|PUBLISHED|FAILED [CHECK enum: outbox_status]';

--
-- Name: COLUMN outbox_events.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN outbox_events.created_by; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN outbox_events.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN outbox_events.modified_by; Type: COMMENT
--

COMMENT ON COLUMN outbox_events.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: outbox_events_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE outbox_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: outbox_events_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE outbox_events_id_seq OWNED BY outbox_events.id;

--
-- Name: password_reset_tokens; Type: TABLE
--

CREATE TABLE password_reset_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE password_reset_tokens; Type: COMMENT
--

COMMENT ON TABLE password_reset_tokens IS '[Auth & Identity] password_reset_tokens — บัญชีผู้ใช้กลาง (buyer เดียวใช้ได้ทุกร้าน), auth token, platform admin role (ประวัติ grant/revoke); v3: ประวัติ OTP (signup/verify/STEP_UP); authz: catalog บทบาท/สิทธิ์ (read-only reference — enforcement อยู่ในโค้ด)';

--
-- Name: COLUMN password_reset_tokens.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN password_reset_tokens.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN password_reset_tokens.created_by; Type: COMMENT
--

COMMENT ON COLUMN password_reset_tokens.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN password_reset_tokens.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN password_reset_tokens.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN password_reset_tokens.modified_by; Type: COMMENT
--

COMMENT ON COLUMN password_reset_tokens.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: payment_proofs; Type: TABLE
--

CREATE TABLE payment_proofs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    payment_id uuid NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    uploaded_by_user_id uuid,
    media_file_id uuid,
    proof_type character varying(30) DEFAULT 'SLIP'::character varying NOT NULL,
    reference_no character varying(120),
    amount_declared numeric(18,2),
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    transferred_at timestamp with time zone,
    status character varying(30) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    verified_by uuid,
    verified_at timestamp with time zone,
    reject_reason character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_payment_proofs_proof_type CHECK ((proof_type IN ('SLIP', 'GATEWAY_RECEIPT'))),
    CONSTRAINT ck_payment_proofs_status CHECK ((status IN ('PENDING_REVIEW', 'VERIFIED', 'REJECTED')))
);

--
-- Name: TABLE payment_proofs; Type: COMMENT
--

COMMENT ON TABLE payment_proofs IS '[Payments] v3: หลักฐานการชำระ — สลิปโอน / ใบเสร็จ gateway';

--
-- Name: COLUMN payment_proofs.id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.id IS 'v3: หลักฐานการชำระ — สลิปโอน / ใบเสร็จ gateway';

--
-- Name: COLUMN payment_proofs.payment_id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.payment_id IS 'payment ที่หลักฐานนี้อ้างถึง; idx';

--
-- Name: COLUMN payment_proofs.order_id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.order_id IS 'denorm เพื่อ query ตามออเดอร์';

--
-- Name: COLUMN payment_proofs.shop_id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.shop_id IS 'tenant; idx(shop_id,status,created_date_time) — คิวตรวจสลิปของร้าน';

--
-- Name: COLUMN payment_proofs.uploaded_by_user_id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.uploaded_by_user_id IS 'ลูกค้าที่อัปโหลดสลิป; null = system (gateway receipt)';

--
-- Name: COLUMN payment_proofs.media_file_id; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.media_file_id IS 'ไฟล์สลิป (media_files.purpose=PAYMENT_SLIP); null ได้สำหรับ GATEWAY_RECEIPT';

--
-- Name: COLUMN payment_proofs.proof_type; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.proof_type IS 'SLIP|GATEWAY_RECEIPT [CHECK enum: payment_proof_type]';

--
-- Name: COLUMN payment_proofs.reference_no; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.reference_no IS 'เลขอ้างอิงธนาคาร/QR หรือ receipt id ของ gateway';

--
-- Name: COLUMN payment_proofs.amount_declared; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.amount_declared IS 'ยอดที่ลูกค้าแจ้ง/บนสลิป — เทียบกับ payments.amount';

--
-- Name: COLUMN payment_proofs.transferred_at; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.transferred_at IS 'เวลาโอนตามสลิป';

--
-- Name: COLUMN payment_proofs.status; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.status IS 'PENDING_REVIEW|VERIFIED|REJECTED — VERIFIED → payments CAPTURED → order PAID_AWAITING_MERCHANT_CONFIRM [CHECK enum: payment_proof_status]';

--
-- Name: COLUMN payment_proofs.verified_by; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.verified_by IS 'สมาชิกร้าน/แอดมินที่ตรวจสลิป (null = auto/gateway)';

--
-- Name: COLUMN payment_proofs.verified_at; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.verified_at IS 'เวลาตรวจ (VERIFIED หรือ REJECTED)';

--
-- Name: COLUMN payment_proofs.reject_reason; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.reject_reason IS 'เหตุผลปฏิเสธ — ลูกค้าอัปโหลดใหม่ได้ (แถวใหม่)';

--
-- Name: COLUMN payment_proofs.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN payment_proofs.created_by; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN payment_proofs.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN payment_proofs.modified_by; Type: COMMENT
--

COMMENT ON COLUMN payment_proofs.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: payment_webhook_events; Type: TABLE
--

CREATE TABLE payment_webhook_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid,
    provider character varying(50) NOT NULL,
    event_id character varying(120) NOT NULL,
    event_type character varying(80) NOT NULL,
    payment_id uuid,
    order_id uuid,
    signature_valid boolean DEFAULT false NOT NULL,
    payload_safe jsonb,
    processed_at timestamp with time zone,
    process_status character varying(30) DEFAULT 'RECEIVED'::character varying NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_payment_webhook_events_process_status CHECK ((process_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')))
);

--
-- Name: TABLE payment_webhook_events; Type: COMMENT
--

COMMENT ON TABLE payment_webhook_events IS '[Payments] inbox webhook จาก PG ของร้าน';

--
-- Name: COLUMN payment_webhook_events.id; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.id IS 'inbox webhook จาก PG ของร้าน';

--
-- Name: COLUMN payment_webhook_events.event_id; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.event_id IS 'provider event id; UNIQUE(provider,event_id) dedup';

--
-- Name: COLUMN payment_webhook_events.signature_valid; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.signature_valid IS 'ตรวจลายเซ็นแล้วหรือยัง';

--
-- Name: COLUMN payment_webhook_events.payload_safe; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.payload_safe IS 'redacted';

--
-- Name: COLUMN payment_webhook_events.process_status; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.process_status IS 'RECEIVED|PROCESSED|IGNORED|FAILED [CHECK enum: webhook_process_status]';

--
-- Name: COLUMN payment_webhook_events.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN payment_webhook_events.created_by; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN payment_webhook_events.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN payment_webhook_events.modified_by; Type: COMMENT
--

COMMENT ON COLUMN payment_webhook_events.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: payments; Type: TABLE
--

CREATE TABLE payments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    gateway_id uuid,
    provider character varying(50) NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    status character varying(30) DEFAULT 'INTENT'::character varying NOT NULL,
    provider_payment_id character varying(120),
    provider_charge_id character varying(120),
    idempotency_key character varying(120) NOT NULL,
    intent_id character varying(120),
    authorized_at timestamp with time zone,
    captured_at timestamp with time zone,
    paid_at timestamp with time zone,
    attempt_no integer DEFAULT 1 NOT NULL,
    failure_code character varying(64),
    raw_response_safe jsonb,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_payments_provider CHECK ((provider IN ('OMISE', '2C2P', 'PROMPTPAY', 'BANK_TRANSFER', 'CUSTOM'))),
    CONSTRAINT ck_payments_status CHECK ((status IN ('INTENT', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED')))
);

--
-- Name: TABLE payments; Type: COMMENT
--

COMMENT ON TABLE payments IS '[Payments] การชำระเงิน — เงินไหลเข้าร้านตรง';

--
-- Name: COLUMN payments.id; Type: COMMENT
--

COMMENT ON COLUMN payments.id IS 'การชำระเงิน — เงินไหลเข้าร้านตรง';

--
-- Name: COLUMN payments.provider; Type: COMMENT
--

COMMENT ON COLUMN payments.provider IS 'OMISE|2C2P|PROMPTPAY|BANK_TRANSFER [CHECK enum: payment_provider]';

--
-- Name: COLUMN payments.status; Type: COMMENT
--

COMMENT ON COLUMN payments.status IS 'INTENT|PENDING|AUTHORIZED|CAPTURED|FAILED|CANCELLED|REFUNDED — INTENT=สร้าง intent ตอน checkout [CHECK enum: payment_record_status]';

--
-- Name: COLUMN payments.provider_payment_id; Type: COMMENT
--

COMMENT ON COLUMN payments.provider_payment_id IS 'ID จาก PG; UNIQUE(provider,provider_payment_id)';

--
-- Name: COLUMN payments.idempotency_key; Type: COMMENT
--

COMMENT ON COLUMN payments.idempotency_key IS 'BUYER_PAYMENT_CREATE';

--
-- Name: COLUMN payments.intent_id; Type: COMMENT
--

COMMENT ON COLUMN payments.intent_id IS 'PG payment intent / charge session id';

--
-- Name: COLUMN payments.authorized_at; Type: COMMENT
--

COMMENT ON COLUMN payments.authorized_at IS 'เวลา AUTHORIZED';

--
-- Name: COLUMN payments.captured_at; Type: COMMENT
--

COMMENT ON COLUMN payments.captured_at IS 'เวลา CAPTURED (= paid); webhook → order PAID_AWAITING_MERCHANT_CONFIRM';

--
-- Name: COLUMN payments.paid_at; Type: COMMENT
--

COMMENT ON COLUMN payments.paid_at IS 'alias เชิงธุรกิจของ captured_at';

--
-- Name: COLUMN payments.attempt_no; Type: COMMENT
--

COMMENT ON COLUMN payments.attempt_no IS 'v3: ครั้งที่ชำระของออเดอร์ — จ่ายไม่สำเร็จ (FAILED) แล้ว retry = แถว payments ใหม่ attempt_no+1';

--
-- Name: COLUMN payments.failure_code; Type: COMMENT
--

COMMENT ON COLUMN payments.failure_code IS 'รหัสความล้มเหลวจาก PG (หลักฐาน payment failed)';

--
-- Name: COLUMN payments.raw_response_safe; Type: COMMENT
--

COMMENT ON COLUMN payments.raw_response_safe IS 'redacted provider payload';

--
-- Name: COLUMN payments.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN payments.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN payments.created_by; Type: COMMENT
--

COMMENT ON COLUMN payments.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN payments.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN payments.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN payments.modified_by; Type: COMMENT
--

COMMENT ON COLUMN payments.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: platform_roles; Type: TABLE
--

CREATE TABLE platform_roles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    role character varying(40) NOT NULL,
    granted_by uuid,
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    revoked_at timestamp with time zone,
    grant_reason character varying(500),
    revoked_by uuid,
    revoke_reason character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_platform_roles_role CHECK (((role)::text = 'PLATFORM_ADMIN'::text))
);

--
-- Name: TABLE platform_roles; Type: COMMENT
--

COMMENT ON TABLE platform_roles IS '[Auth & Identity] บทบาทระดับแพลตฟอร์ม (แยกจาก store role)';

--
-- Name: COLUMN platform_roles.id; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.id IS 'บทบาทระดับแพลตฟอร์ม (แยกจาก store role)';

--
-- Name: COLUMN platform_roles.user_id; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.user_id IS 'authz: 0..1 role ที่ยัง active ต่อ user — partial unique uq_platform_roles_user_id_active (WHERE revoked_at IS NULL); แต่งตั้งซ้ำหลังเพิกถอนได้ (เก็บประวัติ)';

--
-- Name: COLUMN platform_roles.role; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.role IS 'PLATFORM_ADMIN [CHECK enum: platform_role]';

--
-- Name: COLUMN platform_roles.granted_by; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.granted_by IS 'ผู้แต่งตั้ง (กระบวนการภายในเท่านั้น — ไม่มี Public API)';

--
-- Name: COLUMN platform_roles.revoked_at; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.revoked_at IS 'เพิกถอน';

--
-- Name: COLUMN platform_roles.grant_reason; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.grant_reason IS 'authz: เหตุผล/อ้างอิงคำสั่งแต่งตั้ง';

--
-- Name: COLUMN platform_roles.revoked_by; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.revoked_by IS 'authz: ผู้เพิกถอน';

--
-- Name: COLUMN platform_roles.revoke_reason; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.revoke_reason IS 'authz: เหตุผลการเพิกถอน';

--
-- Name: COLUMN platform_roles.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN platform_roles.created_by; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN platform_roles.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN platform_roles.modified_by; Type: COMMENT
--

COMMENT ON COLUMN platform_roles.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: platform_settings; Type: TABLE
--

CREATE TABLE platform_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    setting_key character varying(100) NOT NULL,
    setting_value jsonb NOT NULL,
    is_admin_editable boolean DEFAULT false NOT NULL,
    value_schema jsonb,
    description character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE platform_settings; Type: COMMENT
--

COMMENT ON TABLE platform_settings IS '[Audit & Platform] platform_settings — audit trail + การตั้งค่าระบบ';

--
-- Name: COLUMN platform_settings.setting_key; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.setting_key IS 'allowlist keys only (is_admin_editable)';

--
-- Name: COLUMN platform_settings.setting_value; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.setting_value IS 'ห้ามเก็บ secret';

--
-- Name: COLUMN platform_settings.is_admin_editable; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.is_admin_editable IS 'authz: true = PLATFORM_SETTING_UPDATE แก้ได้ (allowlist); false = แก้ผ่าน migration/ops เท่านั้น';

--
-- Name: COLUMN platform_settings.value_schema; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.value_schema IS 'authz: JSON Schema สำหรับ validate setting_value ก่อนบันทึก';

--
-- Name: COLUMN platform_settings.description; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.description IS 'คำอธิบายค่า';

--
-- Name: COLUMN platform_settings.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN platform_settings.created_by; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN platform_settings.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN platform_settings.modified_by; Type: COMMENT
--

COMMENT ON COLUMN platform_settings.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: product_category_map; Type: TABLE
--

CREATE TABLE product_category_map (
    product_id uuid NOT NULL,
    shop_category_id uuid NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE product_category_map; Type: COMMENT
--

COMMENT ON TABLE product_category_map IS '[Catalog] M:N product↔shop_category';

--
-- Name: COLUMN product_category_map.product_id; Type: COMMENT
--

COMMENT ON COLUMN product_category_map.product_id IS 'M:N product↔shop_category';

--
-- Name: COLUMN product_category_map.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_category_map.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN product_category_map.created_by; Type: COMMENT
--

COMMENT ON COLUMN product_category_map.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN product_category_map.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_category_map.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN product_category_map.modified_by; Type: COMMENT
--

COMMENT ON COLUMN product_category_map.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: product_images; Type: TABLE
--

CREATE TABLE product_images (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    media_file_id uuid NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    alt_text character varying(255),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE product_images; Type: COMMENT
--

COMMENT ON TABLE product_images IS '[Catalog] product_images — สินค้า/SKU/หมวดในร้าน';

--
-- Name: COLUMN product_images.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_images.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN product_images.created_by; Type: COMMENT
--

COMMENT ON COLUMN product_images.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN product_images.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_images.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN product_images.modified_by; Type: COMMENT
--

COMMENT ON COLUMN product_images.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: product_imports; Type: TABLE
--

CREATE TABLE product_imports (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    file_id uuid NOT NULL,
    job_id uuid,
    status character varying(30) DEFAULT 'VALIDATING'::character varying NOT NULL,
    total_rows integer,
    succeeded_rows integer DEFAULT 0 NOT NULL,
    failed_rows integer DEFAULT 0 NOT NULL,
    checkpoint_row integer DEFAULT 0 NOT NULL,
    error_report_file_id uuid,
    finished_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE product_imports; Type: COMMENT
--

COMMENT ON TABLE product_imports IS '[Notifications & Jobs] นำเข้าสินค้าจำนวนมาก';

--
-- Name: COLUMN product_imports.id; Type: COMMENT
--

COMMENT ON COLUMN product_imports.id IS 'นำเข้าสินค้าจำนวนมาก';

--
-- Name: COLUMN product_imports.status; Type: COMMENT
--

COMMENT ON COLUMN product_imports.status IS 'VALIDATING|PREVIEW|APPLYING|SUCCEEDED|PARTIALLY_SUCCEEDED|FAILED|CANCELLED';

--
-- Name: COLUMN product_imports.checkpoint_row; Type: COMMENT
--

COMMENT ON COLUMN product_imports.checkpoint_row IS 'resume จากแถวนี้';

--
-- Name: COLUMN product_imports.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_imports.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN product_imports.created_by; Type: COMMENT
--

COMMENT ON COLUMN product_imports.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN product_imports.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_imports.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN product_imports.modified_by; Type: COMMENT
--

COMMENT ON COLUMN product_imports.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: product_reviews; Type: TABLE
--

CREATE TABLE product_reviews (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    product_id uuid NOT NULL,
    order_id uuid,
    order_item_id uuid,
    buyer_user_id uuid NOT NULL,
    rating smallint NOT NULL,
    title character varying(150),
    body text,
    status character varying(30) DEFAULT 'PUBLISHED'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_product_reviews_rating CHECK (((rating >= 1) AND (rating <= 5))),
    CONSTRAINT ck_product_reviews_status CHECK ((status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'REJECTED')))
);

--
-- Name: TABLE product_reviews; Type: COMMENT
--

COMMENT ON TABLE product_reviews IS '[Reviews] product_reviews — รีวิวสินค้าหลังซื้อ';

--
-- Name: COLUMN product_reviews.order_id; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.order_id IS 'ยืนยันว่าซื้อจริง';

--
-- Name: COLUMN product_reviews.order_item_id; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.order_item_id IS 'UNIQUE(order_item_id) รีวิวครั้งเดียว';

--
-- Name: COLUMN product_reviews.rating; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.rating IS '1-5';

--
-- Name: COLUMN product_reviews.status; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.status IS 'PENDING|PUBLISHED|HIDDEN|REJECTED [CHECK enum: review_status]';

--
-- Name: COLUMN product_reviews.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN product_reviews.created_by; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN product_reviews.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN product_reviews.modified_by; Type: COMMENT
--

COMMENT ON COLUMN product_reviews.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: product_skus; Type: TABLE
--

CREATE TABLE product_skus (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    sku_code character varying(64) NOT NULL,
    barcode character varying(64),
    option_label character varying(255),
    option_values jsonb DEFAULT '{}'::jsonb,
    price numeric(18,2) NOT NULL,
    cost_price numeric(18,2),
    compare_at_price numeric(18,2),
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    weight_grams integer,
    is_active boolean DEFAULT true NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE product_skus; Type: COMMENT
--

COMMENT ON TABLE product_skus IS '[Catalog] SKU / variant';

--
-- Name: COLUMN product_skus.id; Type: COMMENT
--

COMMENT ON COLUMN product_skus.id IS 'SKU / variant';

--
-- Name: COLUMN product_skus.shop_id; Type: COMMENT
--

COMMENT ON COLUMN product_skus.shop_id IS 'denorm tenant สำหรับ query/constraint';

--
-- Name: COLUMN product_skus.sku_code; Type: COMMENT
--

COMMENT ON COLUMN product_skus.sku_code IS 'UNIQUE(shop_id,sku_code)';

--
-- Name: COLUMN product_skus.option_label; Type: COMMENT
--

COMMENT ON COLUMN product_skus.option_label IS 'เช่น สี:แดง / ไซส์:M';

--
-- Name: COLUMN product_skus.option_values; Type: COMMENT
--

COMMENT ON COLUMN product_skus.option_values IS '{"color":"red","size":"M"}';

--
-- Name: COLUMN product_skus.price; Type: COMMENT
--

COMMENT ON COLUMN product_skus.price IS 'ราคาขาย';

--
-- Name: COLUMN product_skus.cost_price; Type: COMMENT
--

COMMENT ON COLUMN product_skus.cost_price IS 'ต้นทุน (ไม่ expose public)';

--
-- Name: COLUMN product_skus.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_skus.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN product_skus.created_by; Type: COMMENT
--

COMMENT ON COLUMN product_skus.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN product_skus.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN product_skus.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN product_skus.modified_by; Type: COMMENT
--

COMMENT ON COLUMN product_skus.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: products; Type: TABLE
--

CREATE TABLE products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    slug character varying(150) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    status character varying(30) DEFAULT 'DRAFT'::character varying NOT NULL,
    product_type character varying(30) DEFAULT 'SIMPLE'::character varying NOT NULL,
    brand character varying(100),
    base_price numeric(18,2),
    compare_at_price numeric(18,2),
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    weight_grams integer,
    is_taxable boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    archived_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_products_product_type CHECK ((product_type IN ('SIMPLE', 'VARIABLE'))),
    CONSTRAINT ck_products_status CHECK ((status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')))
);

--
-- Name: TABLE products; Type: COMMENT
--

COMMENT ON TABLE products IS '[Catalog] สินค้า (product master)';

--
-- Name: COLUMN products.id; Type: COMMENT
--

COMMENT ON COLUMN products.id IS 'สินค้า (product master)';

--
-- Name: COLUMN products.shop_id; Type: COMMENT
--

COMMENT ON COLUMN products.shop_id IS 'tenant; idx(shop_id,status)';

--
-- Name: COLUMN products.slug; Type: COMMENT
--

COMMENT ON COLUMN products.slug IS 'UNIQUE(shop_id,slug)';

--
-- Name: COLUMN products.status; Type: COMMENT
--

COMMENT ON COLUMN products.status IS 'DRAFT|PUBLISHED|ARCHIVED [CHECK enum: product_status]';

--
-- Name: COLUMN products.product_type; Type: COMMENT
--

COMMENT ON COLUMN products.product_type IS 'SIMPLE|VARIABLE [CHECK enum: product_type]';

--
-- Name: COLUMN products.base_price; Type: COMMENT
--

COMMENT ON COLUMN products.base_price IS 'ราคาฐาน (SIMPLE); VARIABLE ใช้ที่ SKU';

--
-- Name: COLUMN products.compare_at_price; Type: COMMENT
--

COMMENT ON COLUMN products.compare_at_price IS 'ราคาขีดฆ่า';

--
-- Name: COLUMN products.weight_grams; Type: COMMENT
--

COMMENT ON COLUMN products.weight_grams IS 'น้ำหนักจัดส่ง';

--
-- Name: COLUMN products.archived_at; Type: COMMENT
--

COMMENT ON COLUMN products.archived_at IS 'PRODUCT_ARCHIVE = soft archive';

--
-- Name: COLUMN products.deleted_at; Type: COMMENT
--

COMMENT ON COLUMN products.deleted_at IS 'ห้ามลบประวัติในออเดอร์';

--
-- Name: COLUMN products.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN products.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN products.created_by; Type: COMMENT
--

COMMENT ON COLUMN products.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN products.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN products.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN products.modified_by; Type: COMMENT
--

COMMENT ON COLUMN products.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: promotion_usages; Type: TABLE
--

CREATE TABLE promotion_usages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    promotion_id uuid NOT NULL,
    coupon_id uuid,
    order_id uuid NOT NULL,
    buyer_user_id uuid,
    discount_amount numeric(18,2) NOT NULL,
    used_at timestamp with time zone DEFAULT now() NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE promotion_usages; Type: COMMENT
--

COMMENT ON TABLE promotion_usages IS '[Promotions] promotion_usages — ส่วนลด/คูปองระดับร้าน';

--
-- Name: COLUMN promotion_usages.order_id; Type: COMMENT
--

COMMENT ON COLUMN promotion_usages.order_id IS 'UNIQUE(order_id,promotion_id)';

--
-- Name: COLUMN promotion_usages.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN promotion_usages.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN promotion_usages.created_by; Type: COMMENT
--

COMMENT ON COLUMN promotion_usages.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN promotion_usages.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN promotion_usages.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN promotion_usages.modified_by; Type: COMMENT
--

COMMENT ON COLUMN promotion_usages.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: promotions; Type: TABLE
--

CREATE TABLE promotions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    promo_type character varying(30) NOT NULL,
    value numeric(18,2) NOT NULL,
    min_order_amount numeric(18,2),
    max_discount_amount numeric(18,2),
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone,
    is_active boolean DEFAULT true NOT NULL,
    usage_limit_total integer,
    usage_limit_per_buyer integer,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_promotions_promo_type CHECK ((promo_type IN ('PERCENT', 'FIXED_AMOUNT', 'FREE_SHIPPING')))
);

--
-- Name: TABLE promotions; Type: COMMENT
--

COMMENT ON TABLE promotions IS '[Promotions] แคมเปญส่วนลดของร้าน';

--
-- Name: COLUMN promotions.id; Type: COMMENT
--

COMMENT ON COLUMN promotions.id IS 'แคมเปญส่วนลดของร้าน';

--
-- Name: COLUMN promotions.promo_type; Type: COMMENT
--

COMMENT ON COLUMN promotions.promo_type IS 'PERCENT|FIXED_AMOUNT|FREE_SHIPPING [CHECK enum: promo_type]';

--
-- Name: COLUMN promotions.value; Type: COMMENT
--

COMMENT ON COLUMN promotions.value IS '% หรือจำนวนเงิน';

--
-- Name: COLUMN promotions.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN promotions.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN promotions.created_by; Type: COMMENT
--

COMMENT ON COLUMN promotions.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN promotions.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN promotions.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN promotions.modified_by; Type: COMMENT
--

COMMENT ON COLUMN promotions.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: refresh_tokens; Type: TABLE
--

CREATE TABLE refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    user_agent character varying(500),
    ip_address inet,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE refresh_tokens; Type: COMMENT
--

COMMENT ON TABLE refresh_tokens IS '[Auth & Identity] refresh_tokens — บัญชีผู้ใช้กลาง (buyer เดียวใช้ได้ทุกร้าน), auth token, platform admin role (ประวัติ grant/revoke); v3: ประวัติ OTP (signup/verify/STEP_UP); authz: catalog บทบาท/สิทธิ์ (read-only reference — enforcement อยู่ในโค้ด)';

--
-- Name: COLUMN refresh_tokens.id; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.id IS 'PK';

--
-- Name: COLUMN refresh_tokens.user_id; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.user_id IS 'เจ้าของ token; ON DELETE CASCADE';

--
-- Name: COLUMN refresh_tokens.token_hash; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 ของ refresh token';

--
-- Name: COLUMN refresh_tokens.expires_at; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.expires_at IS 'หมดอายุ; idx';

--
-- Name: COLUMN refresh_tokens.revoked_at; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.revoked_at IS 'เพิกถอนเมื่อ logout/ระงับ';

--
-- Name: COLUMN refresh_tokens.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN refresh_tokens.user_agent; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.user_agent IS 'device fingerprint (optional)';

--
-- Name: COLUMN refresh_tokens.ip_address; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.ip_address IS 'IP ตอนออก token';

--
-- Name: COLUMN refresh_tokens.created_by; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN refresh_tokens.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN refresh_tokens.modified_by; Type: COMMENT
--

COMMENT ON COLUMN refresh_tokens.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: refunds; Type: TABLE
--

CREATE TABLE refunds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    payment_id uuid,
    return_request_id uuid,
    shop_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    provider_refund_id character varying(120),
    idempotency_key character varying(120) NOT NULL,
    initiated_by uuid,
    reason character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_refunds_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_refunds_status CHECK ((status IN ('PENDING', 'SUCCEEDED', 'FAILED')))
);

--
-- Name: TABLE refunds; Type: COMMENT
--

COMMENT ON TABLE refunds IS '[Returns & Cancellations] คืนเงินผ่าน PG ของร้าน (ไม่ใช่ platform settlement)';

--
-- Name: COLUMN refunds.id; Type: COMMENT
--

COMMENT ON COLUMN refunds.id IS 'คืนเงินผ่าน PG ของร้าน (ไม่ใช่ platform settlement)';

--
-- Name: COLUMN refunds.amount; Type: COMMENT
--

COMMENT ON COLUMN refunds.amount IS '> 0 — ยอดคืนผ่าน PG ของร้าน (แพลตฟอร์มไม่ถือเงิน); วงเงินคงเหลือตรวจที่แอป (ORDER_REFUND)';

--
-- Name: COLUMN refunds.status; Type: COMMENT
--

COMMENT ON COLUMN refunds.status IS 'PENDING|SUCCEEDED|FAILED [CHECK enum: refund_status]';

--
-- Name: COLUMN refunds.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN refunds.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN refunds.created_by; Type: COMMENT
--

COMMENT ON COLUMN refunds.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN refunds.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN refunds.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN refunds.modified_by; Type: COMMENT
--

COMMENT ON COLUMN refunds.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: return_items; Type: TABLE
--

CREATE TABLE return_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    return_request_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    quantity integer NOT NULL,
    condition_note character varying(255),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE return_items; Type: COMMENT
--

COMMENT ON TABLE return_items IS '[Returns & Cancellations] return_items — ยกเลิก/คืนสินค้า/คืนเงินผ่าน PG ร้าน';

--
-- Name: COLUMN return_items.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN return_items.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — คอลัมน์ใหม่';

--
-- Name: COLUMN return_items.created_by; Type: COMMENT
--

COMMENT ON COLUMN return_items.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN return_items.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN return_items.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN return_items.modified_by; Type: COMMENT
--

COMMENT ON COLUMN return_items.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: return_requests; Type: TABLE
--

CREATE TABLE return_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    buyer_user_id uuid NOT NULL,
    status character varying(30) DEFAULT 'REQUESTED'::character varying NOT NULL,
    reason_code character varying(50) NOT NULL,
    reason_note text,
    oms_external_return_id character varying(100),
    oms_sync_status character varying(30) DEFAULT 'NOT_SYNCED'::character varying NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_return_requests_oms_sync_status CHECK ((oms_sync_status IN ('NOT_SYNCED', 'PENDING', 'SYNCED', 'FAILED', 'STALE', 'IGNORED'))),
    CONSTRAINT ck_return_requests_status CHECK ((status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'REFUNDED', 'CLOSED')))
);

--
-- Name: TABLE return_requests; Type: COMMENT
--

COMMENT ON TABLE return_requests IS '[Returns & Cancellations] คำขอคืนสินค้า';

--
-- Name: COLUMN return_requests.id; Type: COMMENT
--

COMMENT ON COLUMN return_requests.id IS 'คำขอคืนสินค้า';

--
-- Name: COLUMN return_requests.status; Type: COMMENT
--

COMMENT ON COLUMN return_requests.status IS 'REQUESTED|APPROVED|REJECTED|RECEIVED|REFUNDED|CLOSED [CHECK enum: return_status]';

--
-- Name: COLUMN return_requests.oms_external_return_id; Type: COMMENT
--

COMMENT ON COLUMN return_requests.oms_external_return_id IS 'OMS plug';

--
-- Name: COLUMN return_requests.oms_sync_status; Type: COMMENT
--

COMMENT ON COLUMN return_requests.oms_sync_status IS '[CHECK enum: oms_sync_status]';

--
-- Name: COLUMN return_requests.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN return_requests.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN return_requests.created_by; Type: COMMENT
--

COMMENT ON COLUMN return_requests.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN return_requests.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN return_requests.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN return_requests.modified_by; Type: COMMENT
--

COMMENT ON COLUMN return_requests.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipment_delivery_proofs; Type: TABLE
--

CREATE TABLE shipment_delivery_proofs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shipment_id uuid NOT NULL,
    media_file_id uuid,
    proof_type character varying(30) NOT NULL,
    receiver_name character varying(150),
    delivered_at timestamp with time zone NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shipment_delivery_proofs_proof_type CHECK ((proof_type IN ('PHOTO', 'SIGNATURE', 'OTP', 'CARRIER_POD')))
);

--
-- Name: TABLE shipment_delivery_proofs; Type: COMMENT
--

COMMENT ON TABLE shipment_delivery_proofs IS '[Shipping] v3: หลักฐานส่งถึง (POD)';

--
-- Name: COLUMN shipment_delivery_proofs.id; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.id IS 'v3: หลักฐานส่งถึง (POD)';

--
-- Name: COLUMN shipment_delivery_proofs.shipment_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.shipment_id IS '1 shipment มีได้หลาย proof (รูป+ลายเซ็น)';

--
-- Name: COLUMN shipment_delivery_proofs.media_file_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.media_file_id IS 'รูป/ลายเซ็น (media_files.purpose=DELIVERY_PROOF); null สำหรับ OTP / CARRIER_POD ref';

--
-- Name: COLUMN shipment_delivery_proofs.proof_type; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.proof_type IS 'PHOTO|SIGNATURE|OTP|CARRIER_POD [CHECK enum: delivery_proof_type]';

--
-- Name: COLUMN shipment_delivery_proofs.receiver_name; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.receiver_name IS 'ชื่อผู้รับตาม carrier (PII — จำกัดสิทธิ์อ่าน)';

--
-- Name: COLUMN shipment_delivery_proofs.delivered_at; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.delivered_at IS 'เวลาส่งถึงตามหลักฐาน';

--
-- Name: COLUMN shipment_delivery_proofs.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shipment_delivery_proofs.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipment_delivery_proofs.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN shipment_delivery_proofs.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_delivery_proofs.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipment_items; Type: TABLE
--

CREATE TABLE shipment_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shipment_id uuid NOT NULL,
    order_item_id uuid NOT NULL,
    quantity integer NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE shipment_items; Type: COMMENT
--

COMMENT ON TABLE shipment_items IS '[Shipping] shipment_items — วิธีส่ง + งานแพ็ก/dispatch + OMS shipment refs; v3: tracking timeline, POD, รูปแพ็ก/label';

--
-- Name: COLUMN shipment_items.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_items.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shipment_items.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_items.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipment_items.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_items.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN shipment_items.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_items.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipment_media; Type: TABLE
--

CREATE TABLE shipment_media (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shipment_id uuid NOT NULL,
    media_file_id uuid NOT NULL,
    media_type character varying(30) DEFAULT 'PACKING_PHOTO'::character varying NOT NULL,
    uploaded_by uuid,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shipment_media_media_type CHECK ((media_type IN ('PACKING_PHOTO', 'LABEL', 'OTHER')))
);

--
-- Name: TABLE shipment_media; Type: COMMENT
--

COMMENT ON TABLE shipment_media IS '[Shipping] v3: รูปหลักฐานแพ็ก/ใบปะหน้า ต่อ shipment';

--
-- Name: COLUMN shipment_media.id; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.id IS 'v3: รูปหลักฐานแพ็ก/ใบปะหน้า ต่อ shipment';

--
-- Name: COLUMN shipment_media.shipment_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.shipment_id IS 'idx(shipment_id)';

--
-- Name: COLUMN shipment_media.media_file_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.media_file_id IS 'media_files.purpose=PACKING_PHOTO (หรือ OTHER)';

--
-- Name: COLUMN shipment_media.media_type; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.media_type IS 'PACKING_PHOTO|LABEL|OTHER [CHECK enum: shipment_media_type]';

--
-- Name: COLUMN shipment_media.uploaded_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.uploaded_by IS 'พนักงานคลัง/ร้านที่ถ่าย';

--
-- Name: COLUMN shipment_media.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shipment_media.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipment_media.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN shipment_media.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_media.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipment_tracking_events; Type: TABLE
--

CREATE TABLE shipment_tracking_events (
    id bigint NOT NULL,
    shipment_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    source character varying(30) NOT NULL,
    carrier_status_code character varying(80),
    normalized_status character varying(40) NOT NULL,
    description character varying(500),
    location character varying(255),
    event_at timestamp with time zone NOT NULL,
    raw_payload_safe jsonb,
    external_event_id character varying(120),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shipment_tracking_events_normalized_status CHECK ((normalized_status IN ('READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED', 'RETURNED'))),
    CONSTRAINT ck_shipment_tracking_events_source CHECK ((source IN ('CARRIER_WEBHOOK', 'CARRIER_POLL', 'MERCHANT', 'SYSTEM', 'OMS')))
);

--
-- Name: TABLE shipment_tracking_events; Type: COMMENT
--

COMMENT ON TABLE shipment_tracking_events IS '[Shipping] v3: timeline การขนส่ง (append-only)';

--
-- Name: COLUMN shipment_tracking_events.id; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.id IS 'v3: timeline การขนส่ง (append-only)';

--
-- Name: COLUMN shipment_tracking_events.shipment_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.shipment_id IS 'UNIQUE(shipment_id,external_event_id); idx(shipment_id,event_at)';

--
-- Name: COLUMN shipment_tracking_events.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.shop_id IS 'tenant';

--
-- Name: COLUMN shipment_tracking_events.source; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.source IS 'CARRIER_WEBHOOK|CARRIER_POLL|MERCHANT|SYSTEM|OMS [CHECK enum: tracking_event_source]';

--
-- Name: COLUMN shipment_tracking_events.carrier_status_code; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.carrier_status_code IS 'สถานะดิบของ carrier/OMS';

--
-- Name: COLUMN shipment_tracking_events.normalized_status; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.normalized_status IS 'shipment_status enum (DISPATCHED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|DELIVERY_FAILED|RETURNED …) [CHECK enum: shipment_status]';

--
-- Name: COLUMN shipment_tracking_events.description; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.description IS 'ข้อความแสดงลูกค้า';

--
-- Name: COLUMN shipment_tracking_events.location; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.location IS 'สาขา/ศูนย์คัดแยก';

--
-- Name: COLUMN shipment_tracking_events.event_at; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.event_at IS 'เวลาเกิดเหตุการณ์ตาม carrier';

--
-- Name: COLUMN shipment_tracking_events.raw_payload_safe; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.raw_payload_safe IS 'payload redacted (ไม่มี PII ผู้รับ)';

--
-- Name: COLUMN shipment_tracking_events.external_event_id; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.external_event_id IS 'id event จาก carrier/OMS — dedup UNIQUE(shipment_id,external_event_id)';

--
-- Name: COLUMN shipment_tracking_events.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง) — เวลาบันทึกเข้าระบบ';

--
-- Name: COLUMN shipment_tracking_events.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipment_tracking_events.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN shipment_tracking_events.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipment_tracking_events.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipment_tracking_events_id_seq; Type: SEQUENCE
--

CREATE SEQUENCE shipment_tracking_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: shipment_tracking_events_id_seq; Type: SEQUENCE OWNED BY
--

ALTER SEQUENCE shipment_tracking_events_id_seq OWNED BY shipment_tracking_events.id;

--
-- Name: shipments; Type: TABLE
--

CREATE TABLE shipments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    shop_id uuid NOT NULL,
    shipping_method_id uuid,
    status character varying(40) DEFAULT 'READY_TO_PACK'::character varying NOT NULL,
    carrier_code character varying(50),
    tracking_number character varying(100),
    label_url character varying(700),
    packed_at timestamp with time zone,
    picked_up_at timestamp with time zone,
    dispatched_at timestamp with time zone,
    delivered_at timestamp with time zone,
    last_tracking_event_at timestamp with time zone,
    packed_by uuid,
    dispatched_by uuid,
    oms_external_shipment_id character varying(100),
    oms_sync_status character varying(30) DEFAULT 'NOT_SYNCED'::character varying NOT NULL,
    oms_synced_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shipments_oms_sync_status CHECK ((oms_sync_status IN ('NOT_SYNCED', 'PENDING', 'SYNCED', 'FAILED', 'STALE', 'IGNORED'))),
    CONSTRAINT ck_shipments_status CHECK ((status IN ('READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED', 'RETURNED')))
);

--
-- Name: TABLE shipments; Type: COMMENT
--

COMMENT ON TABLE shipments IS '[Shipping] งานจัดส่ง';

--
-- Name: COLUMN shipments.id; Type: COMMENT
--

COMMENT ON COLUMN shipments.id IS 'งานจัดส่ง';

--
-- Name: COLUMN shipments.status; Type: COMMENT
--

COMMENT ON COLUMN shipments.status IS 'READY_TO_PACK|PACKING|PACKED|DISPATCHED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|DELIVERY_FAILED|CANCELLED|RETURNED (v3 +OUT_FOR_DELIVERY, DELIVERY_FAILED) [CHECK enum: shipment_status]';

--
-- Name: COLUMN shipments.tracking_number; Type: COMMENT
--

COMMENT ON COLUMN shipments.tracking_number IS 'idx';

--
-- Name: COLUMN shipments.label_url; Type: COMMENT
--

COMMENT ON COLUMN shipments.label_url IS 'URL ใบปะหน้า (OMS/carrier)';

--
-- Name: COLUMN shipments.picked_up_at; Type: COMMENT
--

COMMENT ON COLUMN shipments.picked_up_at IS 'v3: carrier รับพัสดุ (จาก tracking event แรกที่เป็น pickup)';

--
-- Name: COLUMN shipments.delivered_at; Type: COMMENT
--

COMMENT ON COLUMN shipments.delivered_at IS 'ส่งถึง → orders DELIVERED + ตั้ง orders.auto_complete_at';

--
-- Name: COLUMN shipments.last_tracking_event_at; Type: COMMENT
--

COMMENT ON COLUMN shipments.last_tracking_event_at IS 'v3: denorm event_at ล่าสุดจาก shipment_tracking_events';

--
-- Name: COLUMN shipments.oms_external_shipment_id; Type: COMMENT
--

COMMENT ON COLUMN shipments.oms_external_shipment_id IS 'OMS plug: shipment ID ภายนอก';

--
-- Name: COLUMN shipments.oms_sync_status; Type: COMMENT
--

COMMENT ON COLUMN shipments.oms_sync_status IS 'OMS plug [CHECK enum: oms_sync_status]';

--
-- Name: COLUMN shipments.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipments.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shipments.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipments.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipments.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipments.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shipments.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipments.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shipping_methods; Type: TABLE
--

CREATE TABLE shipping_methods (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    carrier_code character varying(50),
    fee_type character varying(30) DEFAULT 'FLAT'::character varying NOT NULL,
    fee_amount numeric(18,2) DEFAULT 0 NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    min_order_amount numeric(18,2),
    is_active boolean DEFAULT true NOT NULL,
    estimated_days_min integer,
    estimated_days_max integer,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE shipping_methods; Type: COMMENT
--

COMMENT ON TABLE shipping_methods IS '[Shipping] วิธีจัดส่งของร้าน';

--
-- Name: COLUMN shipping_methods.id; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.id IS 'วิธีจัดส่งของร้าน';

--
-- Name: COLUMN shipping_methods.name; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.name IS 'เช่น Kerry / Flash / EMS';

--
-- Name: COLUMN shipping_methods.carrier_code; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.carrier_code IS 'KERRY|FLASH|THAIPOST|JT|OTHER';

--
-- Name: COLUMN shipping_methods.fee_type; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.fee_type IS 'FLAT|WEIGHT|FREE';

--
-- Name: COLUMN shipping_methods.min_order_amount; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.min_order_amount IS 'เงื่อนไขส่งฟรี';

--
-- Name: COLUMN shipping_methods.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shipping_methods.created_by; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shipping_methods.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shipping_methods.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shipping_methods.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_categories; Type: TABLE
--

CREATE TABLE shop_categories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    parent_id uuid,
    slug character varying(100) NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid
);

--
-- Name: TABLE shop_categories; Type: COMMENT
--

COMMENT ON TABLE shop_categories IS '[Catalog] หมวดสินค้าภายในร้าน';

--
-- Name: COLUMN shop_categories.id; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.id IS 'หมวดสินค้าภายในร้าน';

--
-- Name: COLUMN shop_categories.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.shop_id IS 'tenant; UNIQUE(shop_id,slug)';

--
-- Name: COLUMN shop_categories.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_categories.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_categories.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shop_categories.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_categories.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_customers; Type: TABLE
--

CREATE TABLE shop_customers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    user_id uuid,
    customer_code character varying(40),
    display_name character varying(150) NOT NULL,
    phone character varying(30),
    email character varying(255),
    default_address jsonb,
    internal_note text,
    source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shop_customers_source CHECK ((source IN ('STOREFRONT', 'MANUAL', 'OMS', 'IMPORT'))),
    CONSTRAINT ck_shop_customers_status CHECK ((status IN ('ACTIVE', 'ARCHIVED')))
);

--
-- Name: TABLE shop_customers; Type: COMMENT
--

COMMENT ON TABLE shop_customers IS '[Shop & Team] authz: ลูกค้าระดับร้าน (CUSTOMER_READ / CUSTOMER_PII_READ / CUSTOMER_CREATE / CUSTOMER_UPDATE) — แยกจากบัญชีกลาง users';

--
-- Name: COLUMN shop_customers.id; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.id IS 'authz: ลูกค้าระดับร้าน (CUSTOMER_READ / CUSTOMER_PII_READ / CUSTOMER_CREATE / CUSTOMER_UPDATE) — แยกจากบัญชีกลาง users';

--
-- Name: COLUMN shop_customers.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.shop_id IS 'tenant';

--
-- Name: COLUMN shop_customers.user_id; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.user_id IS 'ผูกบัญชีผู้ซื้อ (NULL = ร้านเพิ่มเอง/OMS) — partial unique uq_shop_customers_shop_id_user_id';

--
-- Name: COLUMN shop_customers.customer_code; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.customer_code IS 'รหัสลูกค้าในร้าน; UNIQUE(shop_id,customer_code)';

--
-- Name: COLUMN shop_customers.display_name; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.display_name IS 'PII — แสดงเต็มเฉพาะ CUSTOMER_PII_READ (Viewer เห็นแบบปกปิด)';

--
-- Name: COLUMN shop_customers.phone; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.phone IS 'PII';

--
-- Name: COLUMN shop_customers.email; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.email IS 'PII';

--
-- Name: COLUMN shop_customers.default_address; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.default_address IS 'PII — ที่อยู่หลักสำหรับการขาย (ออเดอร์ยัง snapshot ใน order_addresses)';

--
-- Name: COLUMN shop_customers.internal_note; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.internal_note IS 'บันทึกภายใน — ห้ามส่งให้ Viewer/Warehouse และห้ามอยู่ใน export';

--
-- Name: COLUMN shop_customers.source; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.source IS 'STOREFRONT|MANUAL|OMS|IMPORT [CHECK enum: shop_customer_source]';

--
-- Name: COLUMN shop_customers.status; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.status IS 'ACTIVE|ARCHIVED [CHECK enum: shop_customer_status]';

--
-- Name: COLUMN shop_customers.deleted_at; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.deleted_at IS 'soft delete';

--
-- Name: COLUMN shop_customers.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_customers.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_customers.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shop_customers.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_customers.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_invitations; Type: TABLE
--

CREATE TABLE shop_invitations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    role character varying(40) NOT NULL,
    token_hash character varying(64) NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    invited_by uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    accepted_user_id uuid,
    revoked_at timestamp with time zone,
    revoked_by uuid,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shop_invitations_role CHECK ((role IN ('STORE_ADMIN', 'STORE_MANAGER', 'STORE_SALES', 'STORE_WAREHOUSE', 'STORE_VIEWER'))),
    CONSTRAINT ck_shop_invitations_status CHECK ((status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')))
);

--
-- Name: TABLE shop_invitations; Type: COMMENT
--

COMMENT ON TABLE shop_invitations IS '[Shop & Team] shop_invitations — ร้าน (tenant), ทีม 6 บทบาท, PG ของร้าน, หมวด hub; authz: Owner เดียว (unique + constraint trigger), ลูกค้าระดับร้าน, โอนเจ้าของ';

--
-- Name: COLUMN shop_invitations.email; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.email IS 'อีเมลผู้รับเชิญ — authz: คำเชิญ PENDING ได้ 1 ใบต่อ (shop_id, lower(email)) uq_shop_invitations_shop_id_email_pending';

--
-- Name: COLUMN shop_invitations.role; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.role IS 'ห้ามเชิญเป็น STORE_OWNER [CHECK enum: store_role + except STORE_OWNER]';

--
-- Name: COLUMN shop_invitations.status; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.status IS 'PENDING|ACCEPTED|EXPIRED|REVOKED [CHECK enum: invitation_status]';

--
-- Name: COLUMN shop_invitations.revoked_at; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.revoked_at IS 'authz: เวลายกเลิกคำเชิญ';

--
-- Name: COLUMN shop_invitations.revoked_by; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.revoked_by IS 'authz: ผู้ยกเลิกคำเชิญ';

--
-- Name: COLUMN shop_invitations.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_invitations.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_invitations.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE — คอลัมน์ใหม่; ตอน insert = created_date_time (ตาราง append-only แทบไม่ถูก UPDATE)';

--
-- Name: COLUMN shop_invitations.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_invitations.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_members; Type: TABLE
--

CREATE TABLE shop_members (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(40) NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    permission_version integer DEFAULT 0 NOT NULL,
    invited_by uuid,
    joined_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_at timestamp with time zone,
    removed_by uuid,
    remove_reason_code character varying(50),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shop_members_role CHECK ((role IN ('STORE_OWNER', 'STORE_ADMIN', 'STORE_MANAGER', 'STORE_SALES', 'STORE_WAREHOUSE', 'STORE_VIEWER'))),
    CONSTRAINT ck_shop_members_status CHECK ((status IN ('ACTIVE', 'INACTIVE')))
);

--
-- Name: TABLE shop_members; Type: COMMENT
--

COMMENT ON TABLE shop_members IS '[Shop & Team] สมาชิกทีมร้าน';

--
-- Name: COLUMN shop_members.id; Type: COMMENT
--

COMMENT ON COLUMN shop_members.id IS 'สมาชิกทีมร้าน';

--
-- Name: COLUMN shop_members.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shop_members.shop_id IS 'tenant; UNIQUE(shop_id,user_id)';

--
-- Name: COLUMN shop_members.role; Type: COMMENT
--

COMMENT ON COLUMN shop_members.role IS 'STORE_OWNER|STORE_ADMIN|STORE_MANAGER|STORE_SALES|STORE_WAREHOUSE|STORE_VIEWER — authz: STORE_OWNER ACTIVE ได้ 1 คนต่อร้าน (uq_shop_members_shop_id_owner) [CHECK enum: store_role]';

--
-- Name: COLUMN shop_members.status; Type: COMMENT
--

COMMENT ON COLUMN shop_members.status IS 'ACTIVE|INACTIVE [CHECK enum: shop_member_status]';

--
-- Name: COLUMN shop_members.permission_version; Type: COMMENT
--

COMMENT ON COLUMN shop_members.permission_version IS 'bump เมื่อเปลี่ยนบทบาท/ถอน';

--
-- Name: COLUMN shop_members.removed_by; Type: COMMENT
--

COMMENT ON COLUMN shop_members.removed_by IS 'authz: ผู้นำสมาชิกออก (USER_REMOVE)';

--
-- Name: COLUMN shop_members.remove_reason_code; Type: COMMENT
--

COMMENT ON COLUMN shop_members.remove_reason_code IS 'authz: รหัสเหตุผลการนำออก';

--
-- Name: COLUMN shop_members.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_members.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_members.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_members.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_members.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_members.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shop_members.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_members.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_ownership_transfers; Type: TABLE
--

CREATE TABLE shop_ownership_transfers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    from_user_id uuid NOT NULL,
    to_user_id uuid NOT NULL,
    to_member_id uuid NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    initiator_reauth_at timestamp with time zone NOT NULL,
    recipient_reauth_at timestamp with time zone,
    accepted_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    expires_at timestamp with time zone NOT NULL,
    reason_code character varying(50),
    request_id character varying(64),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shop_ownership_transfers_parties CHECK ((from_user_id <> to_user_id)),
    CONSTRAINT ck_shop_ownership_transfers_status CHECK ((status IN ('PENDING', 'COMPLETED', 'DECLINED', 'CANCELLED', 'EXPIRED', 'FAILED')))
);

--
-- Name: TABLE shop_ownership_transfers; Type: COMMENT
--

COMMENT ON TABLE shop_ownership_transfers IS '[Shop & Team] authz: คำขอโอน Store Owner (AUTHZ 04) — เสนอ → ผู้รับยอมรับ → สลับบทบาทใน transaction เดียว';

--
-- Name: COLUMN shop_ownership_transfers.id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.id IS 'authz: คำขอโอน Store Owner (AUTHZ 04) — เสนอ → ผู้รับยอมรับ → สลับบทบาทใน transaction เดียว';

--
-- Name: COLUMN shop_ownership_transfers.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.shop_id IS 'ร้าน; คำขอ PENDING ได้ 1 ใบต่อร้าน (uq_shop_ownership_transfers_shop_id_pending)';

--
-- Name: COLUMN shop_ownership_transfers.from_user_id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.from_user_id IS 'Store Owner ปัจจุบัน (ผู้เสนอ) — ≠ to_user_id (ck_shop_ownership_transfers_parties)';

--
-- Name: COLUMN shop_ownership_transfers.to_user_id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.to_user_id IS 'ผู้รับ — ต้องเป็น ACTIVE member ของร้าน';

--
-- Name: COLUMN shop_ownership_transfers.to_member_id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.to_member_id IS 'แถวสมาชิกของผู้รับ';

--
-- Name: COLUMN shop_ownership_transfers.status; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.status IS 'PENDING|COMPLETED|DECLINED|CANCELLED|EXPIRED|FAILED [CHECK enum: ownership_transfer_status]';

--
-- Name: COLUMN shop_ownership_transfers.initiator_reauth_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.initiator_reauth_at IS 'เวลาที่ผู้โอนยืนยันตัวตนซ้ำ (step-up)';

--
-- Name: COLUMN shop_ownership_transfers.recipient_reauth_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.recipient_reauth_at IS 'เวลาที่ผู้รับยืนยันตัวตน';

--
-- Name: COLUMN shop_ownership_transfers.accepted_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.accepted_at IS 'เวลาผู้รับยอมรับ';

--
-- Name: COLUMN shop_ownership_transfers.completed_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.completed_at IS 'เวลาสลับบทบาทสำเร็จ (เจ้าของเดิม → STORE_ADMIN)';

--
-- Name: COLUMN shop_ownership_transfers.cancelled_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.cancelled_at IS 'เวลายกเลิก/ปฏิเสธ';

--
-- Name: COLUMN shop_ownership_transfers.expires_at; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.expires_at IS 'หมดอายุคำขอ';

--
-- Name: COLUMN shop_ownership_transfers.reason_code; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.reason_code IS 'รหัสเหตุผล (ยกเลิก/ล้มเหลว)';

--
-- Name: COLUMN shop_ownership_transfers.request_id; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.request_id IS 'correlation กับ audit_logs';

--
-- Name: COLUMN shop_ownership_transfers.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_ownership_transfers.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_ownership_transfers.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shop_ownership_transfers.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_ownership_transfers.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shop_payment_gateways; Type: TABLE
--

CREATE TABLE shop_payment_gateways (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    provider character varying(50) NOT NULL,
    display_name character varying(100) NOT NULL,
    is_active boolean DEFAULT false NOT NULL,
    config_encrypted text,
    webhook_secret_encrypted text,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shop_payment_gateways_provider CHECK ((provider IN ('OMISE', '2C2P', 'PROMPTPAY', 'BANK_TRANSFER', 'CUSTOM')))
);

--
-- Name: TABLE shop_payment_gateways; Type: COMMENT
--

COMMENT ON TABLE shop_payment_gateways IS '[Shop & Team] PG ของร้านเอง — เราไม่ถือเงิน';

--
-- Name: COLUMN shop_payment_gateways.id; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.id IS 'PG ของร้านเอง — เราไม่ถือเงิน';

--
-- Name: COLUMN shop_payment_gateways.shop_id; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.shop_id IS 'UNIQUE(shop_id,provider)';

--
-- Name: COLUMN shop_payment_gateways.provider; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.provider IS 'OMISE|2C2P|PROMPTPAY|BANK_TRANSFER|CUSTOM [CHECK enum: payment_provider]';

--
-- Name: COLUMN shop_payment_gateways.config_encrypted; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.config_encrypted IS 'API keys เข้ารหัส — ห้าม log';

--
-- Name: COLUMN shop_payment_gateways.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shop_payment_gateways.created_by; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shop_payment_gateways.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shop_payment_gateways.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shop_payment_gateways.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: shops; Type: TABLE
--

CREATE TABLE shops (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug character varying(100) NOT NULL,
    name character varying(150) NOT NULL,
    name_en character varying(150),
    description text,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    hub_category_id uuid,
    logo_media_id uuid,
    cover_media_id uuid,
    application_id uuid,
    owner_user_id uuid NOT NULL,
    timezone character varying(64) DEFAULT 'Asia/Bangkok'::character varying NOT NULL,
    currency character(3) DEFAULT 'THB'::bpchar NOT NULL,
    address_line character varying(500),
    province character varying(100),
    district character varying(100),
    subdistrict character varying(100),
    postal_code character varying(10),
    contact_phone character varying(30),
    contact_email character varying(255),
    published_at timestamp with time zone,
    suspended_at timestamp with time zone,
    suspend_reason text,
    deleted_at timestamp with time zone,
    suspended_by uuid,
    suspend_reason_code character varying(50),
    restored_at timestamp with time zone,
    restored_by uuid,
    closed_at timestamp with time zone,
    closed_by uuid,
    close_reason character varying(500),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_shops_status CHECK ((status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')))
);

--
-- Name: TABLE shops; Type: COMMENT
--

COMMENT ON TABLE shops IS '[Shop & Team] ร้านค้า (tenant root)';

--
-- Name: COLUMN shops.id; Type: COMMENT
--

COMMENT ON COLUMN shops.id IS 'ร้านค้า (tenant root)';

--
-- Name: COLUMN shops.slug; Type: COMMENT
--

COMMENT ON COLUMN shops.slug IS 'URL slug หน้าร้าน /s/{slug}';

--
-- Name: COLUMN shops.name; Type: COMMENT
--

COMMENT ON COLUMN shops.name IS 'ชื่อร้านแสดงผล';

--
-- Name: COLUMN shops.status; Type: COMMENT
--

COMMENT ON COLUMN shops.status IS 'ACTIVE|SUSPENDED|CLOSED [CHECK enum: shop_status]';

--
-- Name: COLUMN shops.hub_category_id; Type: COMMENT
--

COMMENT ON COLUMN shops.hub_category_id IS 'หมวดในหน้า hub/directory';

--
-- Name: COLUMN shops.application_id; Type: COMMENT
--

COMMENT ON COLUMN shops.application_id IS 'ใบสมัครต้นทาง (idempotent create)';

--
-- Name: COLUMN shops.owner_user_id; Type: COMMENT
--

COMMENT ON COLUMN shops.owner_user_id IS 'Store Owner ปัจจุบัน — authz: ต้องตรงกับ shop_members ACTIVE STORE_OWNER (constraint trigger trg_shops_owner_guard ตอน COMMIT)';

--
-- Name: COLUMN shops.currency; Type: COMMENT
--

COMMENT ON COLUMN shops.currency IS 'ISO 4217';

--
-- Name: COLUMN shops.published_at; Type: COMMENT
--

COMMENT ON COLUMN shops.published_at IS 'เปิดเผยใน hub';

--
-- Name: COLUMN shops.suspended_at; Type: COMMENT
--

COMMENT ON COLUMN shops.suspended_at IS 'authz: เวลาระงับ (PLATFORM_SHOP_SUSPEND)';

--
-- Name: COLUMN shops.suspend_reason; Type: COMMENT
--

COMMENT ON COLUMN shops.suspend_reason IS 'เหตุผลการระงับ (ข้อความ)';

--
-- Name: COLUMN shops.deleted_at; Type: COMMENT
--

COMMENT ON COLUMN shops.deleted_at IS 'soft delete / ปิดถาวร';

--
-- Name: COLUMN shops.suspended_by; Type: COMMENT
--

COMMENT ON COLUMN shops.suspended_by IS 'authz: Platform Admin ผู้ระงับ';

--
-- Name: COLUMN shops.suspend_reason_code; Type: COMMENT
--

COMMENT ON COLUMN shops.suspend_reason_code IS 'authz: รหัสเหตุผลการระงับ';

--
-- Name: COLUMN shops.restored_at; Type: COMMENT
--

COMMENT ON COLUMN shops.restored_at IS 'authz: เวลาเปิดใช้งานคืน (PLATFORM_SHOP_RESTORE)';

--
-- Name: COLUMN shops.restored_by; Type: COMMENT
--

COMMENT ON COLUMN shops.restored_by IS 'authz: ผู้คืนสถานะ';

--
-- Name: COLUMN shops.closed_at; Type: COMMENT
--

COMMENT ON COLUMN shops.closed_at IS 'authz: เวลาปิดร้านถาวร (SHOP_CLOSE) หลังตรวจออเดอร์/คืนเงินค้าง';

--
-- Name: COLUMN shops.closed_by; Type: COMMENT
--

COMMENT ON COLUMN shops.closed_by IS 'authz: Store Owner ผู้ปิดร้าน';

--
-- Name: COLUMN shops.close_reason; Type: COMMENT
--

COMMENT ON COLUMN shops.close_reason IS 'authz: เหตุผลการปิดร้าน';

--
-- Name: COLUMN shops.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN shops.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN shops.created_by; Type: COMMENT
--

COMMENT ON COLUMN shops.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN shops.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN shops.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN shops.modified_by; Type: COMMENT
--

COMMENT ON COLUMN shops.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: users; Type: TABLE
--

CREATE TABLE users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    phone character varying(30) NOT NULL,
    password_hash character varying(255) NOT NULL,
    role character varying(30) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    email_verified_at timestamp with time zone,
    phone_verified_at timestamp with time zone,
    last_login_at timestamp with time zone,
    permission_version integer DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    suspended_at timestamp with time zone,
    suspended_by uuid,
    suspend_reason_code character varying(50),
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT ck_users_role CHECK ((role IN ('CUSTOMER', 'MERCHANT', 'ADMIN'))),
    CONSTRAINT ck_users_status CHECK ((status IN ('ACTIVE', 'SUSPENDED')))
);

--
-- Name: TABLE users; Type: COMMENT
--

COMMENT ON TABLE users IS '[Auth & Identity] users — บัญชีผู้ใช้กลาง (buyer เดียวใช้ได้ทุกร้าน), auth token, platform admin role (ประวัติ grant/revoke); v3: ประวัติ OTP (signup/verify/STEP_UP); authz: catalog บทบาท/สิทธิ์ (read-only reference — enforcement อยู่ในโค้ด)';

--
-- Name: COLUMN users.id; Type: COMMENT
--

COMMENT ON COLUMN users.id IS 'Primary key — UUID สาธารณะ';

--
-- Name: COLUMN users.first_name; Type: COMMENT
--

COMMENT ON COLUMN users.first_name IS 'ชื่อ';

--
-- Name: COLUMN users.last_name; Type: COMMENT
--

COMMENT ON COLUMN users.last_name IS 'นามสกุล';

--
-- Name: COLUMN users.email; Type: COMMENT
--

COMMENT ON COLUMN users.email IS 'อีเมล login (unique)';

--
-- Name: COLUMN users.phone; Type: COMMENT
--

COMMENT ON COLUMN users.phone IS 'เบอร์โทร (unique)';

--
-- Name: COLUMN users.password_hash; Type: COMMENT
--

COMMENT ON COLUMN users.password_hash IS 'BCrypt/Argon2 hash — ห้าม log';

--
-- Name: COLUMN users.role; Type: COMMENT
--

COMMENT ON COLUMN users.role IS 'CUSTOMER|MERCHANT|ADMIN — DEPRECATED (legacy V1, authz 2026-10-04): ห้ามใช้ในการ authorize; ใช้ platform_roles / shop_members + permission_version. Flyway V3 คัดลอก ADMIN → platform_roles (step 1); DROP ในขั้นถัดไปหลังแก้ JPA UserAccount [CHECK enum: user_role (legacy)]';

--
-- Name: COLUMN users.status; Type: COMMENT
--

COMMENT ON COLUMN users.status IS 'ACTIVE|SUSPENDED [CHECK enum: user_status]';

--
-- Name: COLUMN users.created_date_time; Type: COMMENT
--

COMMENT ON COLUMN users.created_date_time IS 'audit: เวลาสร้างแถว (มาตรฐาน audit ทุกตาราง)';

--
-- Name: COLUMN users.modified_date_time; Type: COMMENT
--

COMMENT ON COLUMN users.modified_date_time IS 'audit: แก้ไขล่าสุด — trigger set_modified_date_time() ตั้ง now() ทุก UPDATE';

--
-- Name: COLUMN users.email_verified_at; Type: COMMENT
--

COMMENT ON COLUMN users.email_verified_at IS 'เวลายืนยันอีเมล';

--
-- Name: COLUMN users.phone_verified_at; Type: COMMENT
--

COMMENT ON COLUMN users.phone_verified_at IS 'เวลายืนยันเบอร์';

--
-- Name: COLUMN users.last_login_at; Type: COMMENT
--

COMMENT ON COLUMN users.last_login_at IS 'เข้าสู่ระบบล่าสุด';

--
-- Name: COLUMN users.permission_version; Type: COMMENT
--

COMMENT ON COLUMN users.permission_version IS 'bump เมื่อถอนสิทธิ์/ระงับ เพื่อ invalidate cache';

--
-- Name: COLUMN users.deleted_at; Type: COMMENT
--

COMMENT ON COLUMN users.deleted_at IS 'soft delete';

--
-- Name: COLUMN users.suspended_at; Type: COMMENT
--

COMMENT ON COLUMN users.suspended_at IS 'authz: เวลาระงับบัญชี (PLATFORM_ACCOUNT_SUSPEND) — ทำพร้อม revoke refresh_tokens + bump permission_version';

--
-- Name: COLUMN users.suspended_by; Type: COMMENT
--

COMMENT ON COLUMN users.suspended_by IS 'authz: Platform Admin ผู้ระงับ (business actor)';

--
-- Name: COLUMN users.suspend_reason_code; Type: COMMENT
--

COMMENT ON COLUMN users.suspend_reason_code IS 'authz: รหัสเหตุผลการระงับ (ห้ามเก็บ PII; รายละเอียดใน audit_logs)';

--
-- Name: COLUMN users.created_by; Type: COMMENT
--

COMMENT ON COLUMN users.created_by IS 'audit: ผู้สร้าง — logical ref users.id (ไม่มี FK); NULL = system/job/webhook';

--
-- Name: COLUMN users.modified_by; Type: COMMENT
--

COMMENT ON COLUMN users.modified_by IS 'audit: ผู้แก้ไขล่าสุด — logical ref users.id (ไม่มี FK); แอปเป็นผู้ตั้งค่า; NULL = system/job/webhook';

--
-- Name: audit_logs id; Type: DEFAULT
--

ALTER TABLE ONLY audit_logs ALTER COLUMN id SET DEFAULT nextval('audit_logs_id_seq'::regclass);

--
-- Name: integration_events id; Type: DEFAULT
--

ALTER TABLE ONLY integration_events ALTER COLUMN id SET DEFAULT nextval('integration_events_id_seq'::regclass);

--
-- Name: inventory_ledger id; Type: DEFAULT
--

ALTER TABLE ONLY inventory_ledger ALTER COLUMN id SET DEFAULT nextval('inventory_ledger_id_seq'::regclass);

--
-- Name: order_item_reveal_logs id; Type: DEFAULT
--

ALTER TABLE ONLY order_item_reveal_logs ALTER COLUMN id SET DEFAULT nextval('order_item_reveal_logs_id_seq'::regclass);

--
-- Name: order_status_history id; Type: DEFAULT
--

ALTER TABLE ONLY order_status_history ALTER COLUMN id SET DEFAULT nextval('order_status_history_id_seq'::regclass);

--
-- Name: outbox_events id; Type: DEFAULT
--

ALTER TABLE ONLY outbox_events ALTER COLUMN id SET DEFAULT nextval('outbox_events_id_seq'::regclass);

--
-- Name: shipment_tracking_events id; Type: DEFAULT
--

ALTER TABLE ONLY shipment_tracking_events ALTER COLUMN id SET DEFAULT nextval('shipment_tracking_events_id_seq'::regclass);

--
-- Name: audit_logs pk_audit_logs; Type: CONSTRAINT
--

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT pk_audit_logs PRIMARY KEY (id);

--
-- Name: auth_permissions pk_auth_permissions; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_permissions
    ADD CONSTRAINT pk_auth_permissions PRIMARY KEY (id);

--
-- Name: auth_role_permissions pk_auth_role_permissions; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_role_permissions
    ADD CONSTRAINT pk_auth_role_permissions PRIMARY KEY (id);

--
-- Name: auth_roles pk_auth_roles; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_roles
    ADD CONSTRAINT pk_auth_roles PRIMARY KEY (id);

--
-- Name: background_jobs pk_background_jobs; Type: CONSTRAINT
--

ALTER TABLE ONLY background_jobs
    ADD CONSTRAINT pk_background_jobs PRIMARY KEY (id);

--
-- Name: blog_categories pk_blog_categories; Type: CONSTRAINT
--

ALTER TABLE ONLY blog_categories
    ADD CONSTRAINT pk_blog_categories PRIMARY KEY (id);

--
-- Name: blog_posts pk_blog_posts; Type: CONSTRAINT
--

ALTER TABLE ONLY blog_posts
    ADD CONSTRAINT pk_blog_posts PRIMARY KEY (id);

--
-- Name: buyer_addresses pk_buyer_addresses; Type: CONSTRAINT
--

ALTER TABLE ONLY buyer_addresses
    ADD CONSTRAINT pk_buyer_addresses PRIMARY KEY (id);

--
-- Name: cart_items pk_cart_items; Type: CONSTRAINT
--

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT pk_cart_items PRIMARY KEY (id);

--
-- Name: carts pk_carts; Type: CONSTRAINT
--

ALTER TABLE ONLY carts
    ADD CONSTRAINT pk_carts PRIMARY KEY (id);

--
-- Name: checkouts pk_checkouts; Type: CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT pk_checkouts PRIMARY KEY (id);

--
-- Name: coupons pk_coupons; Type: CONSTRAINT
--

ALTER TABLE ONLY coupons
    ADD CONSTRAINT pk_coupons PRIMARY KEY (id);

--
-- Name: hub_categories pk_hub_categories; Type: CONSTRAINT
--

ALTER TABLE ONLY hub_categories
    ADD CONSTRAINT pk_hub_categories PRIMARY KEY (id);

--
-- Name: integration_events pk_integration_events; Type: CONSTRAINT
--

ALTER TABLE ONLY integration_events
    ADD CONSTRAINT pk_integration_events PRIMARY KEY (id);

--
-- Name: inventory_ledger pk_inventory_ledger; Type: CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT pk_inventory_ledger PRIMARY KEY (id);

--
-- Name: inventory_levels pk_inventory_levels; Type: CONSTRAINT
--

ALTER TABLE ONLY inventory_levels
    ADD CONSTRAINT pk_inventory_levels PRIMARY KEY (id);

--
-- Name: inventory_reservations pk_inventory_reservations; Type: CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT pk_inventory_reservations PRIMARY KEY (id);

--
-- Name: media_files pk_media_files; Type: CONSTRAINT
--

ALTER TABLE ONLY media_files
    ADD CONSTRAINT pk_media_files PRIMARY KEY (id);

--
-- Name: media_variants pk_media_variants; Type: CONSTRAINT
--

ALTER TABLE ONLY media_variants
    ADD CONSTRAINT pk_media_variants PRIMARY KEY (id);

--
-- Name: merchant_application_reviews pk_merchant_application_reviews; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_application_reviews
    ADD CONSTRAINT pk_merchant_application_reviews PRIMARY KEY (id);

--
-- Name: merchant_applications pk_merchant_applications; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT pk_merchant_applications PRIMARY KEY (id);

--
-- Name: merchant_documents pk_merchant_documents; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_documents
    ADD CONSTRAINT pk_merchant_documents PRIMARY KEY (id);

--
-- Name: merchant_order_confirmations pk_merchant_order_confirmations; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_order_confirmations
    ADD CONSTRAINT pk_merchant_order_confirmations PRIMARY KEY (id);

--
-- Name: notifications pk_notifications; Type: CONSTRAINT
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT pk_notifications PRIMARY KEY (id);

--
-- Name: oms_connections pk_oms_connections; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_connections
    ADD CONSTRAINT pk_oms_connections PRIMARY KEY (id);

--
-- Name: oms_order_links pk_oms_order_links; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT pk_oms_order_links PRIMARY KEY (id);

--
-- Name: oms_shipment_links pk_oms_shipment_links; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT pk_oms_shipment_links PRIMARY KEY (id);

--
-- Name: oms_sync_checkpoints pk_oms_sync_checkpoints; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_sync_checkpoints
    ADD CONSTRAINT pk_oms_sync_checkpoints PRIMARY KEY (id);

--
-- Name: oms_webhook_inbox pk_oms_webhook_inbox; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_webhook_inbox
    ADD CONSTRAINT pk_oms_webhook_inbox PRIMARY KEY (id);

--
-- Name: order_addresses pk_order_addresses; Type: CONSTRAINT
--

ALTER TABLE ONLY order_addresses
    ADD CONSTRAINT pk_order_addresses PRIMARY KEY (id);

--
-- Name: order_cancellations pk_order_cancellations; Type: CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT pk_order_cancellations PRIMARY KEY (id);

--
-- Name: order_financial_snapshots pk_order_financial_snapshots; Type: CONSTRAINT
--

ALTER TABLE ONLY order_financial_snapshots
    ADD CONSTRAINT pk_order_financial_snapshots PRIMARY KEY (id);

--
-- Name: order_item_reveal_logs pk_order_item_reveal_logs; Type: CONSTRAINT
--

ALTER TABLE ONLY order_item_reveal_logs
    ADD CONSTRAINT pk_order_item_reveal_logs PRIMARY KEY (id);

--
-- Name: order_items pk_order_items; Type: CONSTRAINT
--

ALTER TABLE ONLY order_items
    ADD CONSTRAINT pk_order_items PRIMARY KEY (id);

--
-- Name: order_status_history pk_order_status_history; Type: CONSTRAINT
--

ALTER TABLE ONLY order_status_history
    ADD CONSTRAINT pk_order_status_history PRIMARY KEY (id);

--
-- Name: orders pk_orders; Type: CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT pk_orders PRIMARY KEY (id);

--
-- Name: otp_verifications pk_otp_verifications; Type: CONSTRAINT
--

ALTER TABLE ONLY otp_verifications
    ADD CONSTRAINT pk_otp_verifications PRIMARY KEY (id);

--
-- Name: outbox_events pk_outbox_events; Type: CONSTRAINT
--

ALTER TABLE ONLY outbox_events
    ADD CONSTRAINT pk_outbox_events PRIMARY KEY (id);

--
-- Name: password_reset_tokens pk_password_reset_tokens; Type: CONSTRAINT
--

ALTER TABLE ONLY password_reset_tokens
    ADD CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id);

--
-- Name: payment_proofs pk_payment_proofs; Type: CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT pk_payment_proofs PRIMARY KEY (id);

--
-- Name: payment_webhook_events pk_payment_webhook_events; Type: CONSTRAINT
--

ALTER TABLE ONLY payment_webhook_events
    ADD CONSTRAINT pk_payment_webhook_events PRIMARY KEY (id);

--
-- Name: payments pk_payments; Type: CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT pk_payments PRIMARY KEY (id);

--
-- Name: platform_roles pk_platform_roles; Type: CONSTRAINT
--

ALTER TABLE ONLY platform_roles
    ADD CONSTRAINT pk_platform_roles PRIMARY KEY (id);

--
-- Name: platform_settings pk_platform_settings; Type: CONSTRAINT
--

ALTER TABLE ONLY platform_settings
    ADD CONSTRAINT pk_platform_settings PRIMARY KEY (id);

--
-- Name: product_category_map pk_product_category_map; Type: CONSTRAINT
--

ALTER TABLE ONLY product_category_map
    ADD CONSTRAINT pk_product_category_map PRIMARY KEY (product_id, shop_category_id);

--
-- Name: product_images pk_product_images; Type: CONSTRAINT
--

ALTER TABLE ONLY product_images
    ADD CONSTRAINT pk_product_images PRIMARY KEY (id);

--
-- Name: product_imports pk_product_imports; Type: CONSTRAINT
--

ALTER TABLE ONLY product_imports
    ADD CONSTRAINT pk_product_imports PRIMARY KEY (id);

--
-- Name: product_reviews pk_product_reviews; Type: CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT pk_product_reviews PRIMARY KEY (id);

--
-- Name: product_skus pk_product_skus; Type: CONSTRAINT
--

ALTER TABLE ONLY product_skus
    ADD CONSTRAINT pk_product_skus PRIMARY KEY (id);

--
-- Name: products pk_products; Type: CONSTRAINT
--

ALTER TABLE ONLY products
    ADD CONSTRAINT pk_products PRIMARY KEY (id);

--
-- Name: promotion_usages pk_promotion_usages; Type: CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT pk_promotion_usages PRIMARY KEY (id);

--
-- Name: promotions pk_promotions; Type: CONSTRAINT
--

ALTER TABLE ONLY promotions
    ADD CONSTRAINT pk_promotions PRIMARY KEY (id);

--
-- Name: refresh_tokens pk_refresh_tokens; Type: CONSTRAINT
--

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT pk_refresh_tokens PRIMARY KEY (id);

--
-- Name: refunds pk_refunds; Type: CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT pk_refunds PRIMARY KEY (id);

--
-- Name: return_items pk_return_items; Type: CONSTRAINT
--

ALTER TABLE ONLY return_items
    ADD CONSTRAINT pk_return_items PRIMARY KEY (id);

--
-- Name: return_requests pk_return_requests; Type: CONSTRAINT
--

ALTER TABLE ONLY return_requests
    ADD CONSTRAINT pk_return_requests PRIMARY KEY (id);

--
-- Name: shipment_delivery_proofs pk_shipment_delivery_proofs; Type: CONSTRAINT
--

ALTER TABLE ONLY shipment_delivery_proofs
    ADD CONSTRAINT pk_shipment_delivery_proofs PRIMARY KEY (id);

--
-- Name: shipment_items pk_shipment_items; Type: CONSTRAINT
--

ALTER TABLE ONLY shipment_items
    ADD CONSTRAINT pk_shipment_items PRIMARY KEY (id);

--
-- Name: shipment_media pk_shipment_media; Type: CONSTRAINT
--

ALTER TABLE ONLY shipment_media
    ADD CONSTRAINT pk_shipment_media PRIMARY KEY (id);

--
-- Name: shipment_tracking_events pk_shipment_tracking_events; Type: CONSTRAINT
--

ALTER TABLE ONLY shipment_tracking_events
    ADD CONSTRAINT pk_shipment_tracking_events PRIMARY KEY (id);

--
-- Name: shipments pk_shipments; Type: CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT pk_shipments PRIMARY KEY (id);

--
-- Name: shipping_methods pk_shipping_methods; Type: CONSTRAINT
--

ALTER TABLE ONLY shipping_methods
    ADD CONSTRAINT pk_shipping_methods PRIMARY KEY (id);

--
-- Name: shop_categories pk_shop_categories; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_categories
    ADD CONSTRAINT pk_shop_categories PRIMARY KEY (id);

--
-- Name: shop_customers pk_shop_customers; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_customers
    ADD CONSTRAINT pk_shop_customers PRIMARY KEY (id);

--
-- Name: shop_invitations pk_shop_invitations; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT pk_shop_invitations PRIMARY KEY (id);

--
-- Name: shop_members pk_shop_members; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT pk_shop_members PRIMARY KEY (id);

--
-- Name: shop_ownership_transfers pk_shop_ownership_transfers; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_ownership_transfers
    ADD CONSTRAINT pk_shop_ownership_transfers PRIMARY KEY (id);

--
-- Name: shop_payment_gateways pk_shop_payment_gateways; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_payment_gateways
    ADD CONSTRAINT pk_shop_payment_gateways PRIMARY KEY (id);

--
-- Name: shops pk_shops; Type: CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT pk_shops PRIMARY KEY (id);

--
-- Name: users pk_users; Type: CONSTRAINT
--

ALTER TABLE ONLY users
    ADD CONSTRAINT pk_users PRIMARY KEY (id);

--
-- Name: auth_permissions uq_auth_permissions_code; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_permissions
    ADD CONSTRAINT uq_auth_permissions_code UNIQUE (code);

--
-- Name: auth_role_permissions uq_auth_role_permissions_role_id_permission_id; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_role_permissions
    ADD CONSTRAINT uq_auth_role_permissions_role_id_permission_id UNIQUE (role_id, permission_id);

--
-- Name: auth_roles uq_auth_roles_code; Type: CONSTRAINT
--

ALTER TABLE ONLY auth_roles
    ADD CONSTRAINT uq_auth_roles_code UNIQUE (code);

--
-- Name: background_jobs uq_background_jobs_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY background_jobs
    ADD CONSTRAINT uq_background_jobs_idempotency_key UNIQUE (idempotency_key);

--
-- Name: blog_categories uq_blog_categories_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY blog_categories
    ADD CONSTRAINT uq_blog_categories_slug UNIQUE (slug);

--
-- Name: blog_posts uq_blog_posts_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY blog_posts
    ADD CONSTRAINT uq_blog_posts_slug UNIQUE (slug);

--
-- Name: cart_items uq_cart_items_cart_id_sku_id; Type: CONSTRAINT
--

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT uq_cart_items_cart_id_sku_id UNIQUE (cart_id, sku_id);

--
-- Name: checkouts uq_checkouts_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT uq_checkouts_idempotency_key UNIQUE (idempotency_key);

--
-- Name: checkouts uq_checkouts_order_id; Type: CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT uq_checkouts_order_id UNIQUE (order_id);

--
-- Name: coupons uq_coupons_shop_id_code; Type: CONSTRAINT
--

ALTER TABLE ONLY coupons
    ADD CONSTRAINT uq_coupons_shop_id_code UNIQUE (shop_id, code);

--
-- Name: hub_categories uq_hub_categories_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY hub_categories
    ADD CONSTRAINT uq_hub_categories_slug UNIQUE (slug);

--
-- Name: integration_events uq_integration_events_event_id; Type: CONSTRAINT
--

ALTER TABLE ONLY integration_events
    ADD CONSTRAINT uq_integration_events_event_id UNIQUE (event_id);

--
-- Name: inventory_ledger uq_inventory_ledger_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT uq_inventory_ledger_idempotency_key UNIQUE (idempotency_key);

--
-- Name: inventory_levels uq_inventory_levels_sku_id; Type: CONSTRAINT
--

ALTER TABLE ONLY inventory_levels
    ADD CONSTRAINT uq_inventory_levels_sku_id UNIQUE (sku_id);

--
-- Name: media_variants uq_media_variants_media_file_id_variant_version; Type: CONSTRAINT
--

ALTER TABLE ONLY media_variants
    ADD CONSTRAINT uq_media_variants_media_file_id_variant_version UNIQUE (media_file_id, variant_version);

--
-- Name: merchant_applications uq_merchant_applications_reference_no; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT uq_merchant_applications_reference_no UNIQUE (reference_no);

--
-- Name: merchant_applications uq_merchant_applications_shop_id; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT uq_merchant_applications_shop_id UNIQUE (shop_id);

--
-- Name: merchant_documents uq_merchant_documents_application_id_document_type; Type: CONSTRAINT
--

ALTER TABLE ONLY merchant_documents
    ADD CONSTRAINT uq_merchant_documents_application_id_document_type UNIQUE (application_id, document_type);

--
-- Name: notifications uq_notifications_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT uq_notifications_idempotency_key UNIQUE (idempotency_key);

--
-- Name: oms_connections uq_oms_connections_shop_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_connections
    ADD CONSTRAINT uq_oms_connections_shop_id UNIQUE (shop_id);

--
-- Name: oms_order_links uq_oms_order_links_oms_connection_id_external_order_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT uq_oms_order_links_oms_connection_id_external_order_id UNIQUE (oms_connection_id, external_order_id);

--
-- Name: oms_order_links uq_oms_order_links_order_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT uq_oms_order_links_order_id UNIQUE (order_id);

--
-- Name: oms_shipment_links uq_oms_shipment_links_oms_connection_id_external_shipment_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT uq_oms_shipment_links_oms_connection_id_external_shipment_id UNIQUE (oms_connection_id, external_shipment_id);

--
-- Name: oms_shipment_links uq_oms_shipment_links_shipment_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT uq_oms_shipment_links_shipment_id UNIQUE (shipment_id);

--
-- Name: oms_sync_checkpoints uq_oms_sync_checkpoints_oms_connection_id_resource_type; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_sync_checkpoints
    ADD CONSTRAINT uq_oms_sync_checkpoints_oms_connection_id_resource_type UNIQUE (oms_connection_id, resource_type);

--
-- Name: oms_webhook_inbox uq_oms_webhook_inbox_oms_connection_id_external_event_id; Type: CONSTRAINT
--

ALTER TABLE ONLY oms_webhook_inbox
    ADD CONSTRAINT uq_oms_webhook_inbox_oms_connection_id_external_event_id UNIQUE (oms_connection_id, external_event_id);

--
-- Name: order_addresses uq_order_addresses_order_id_address_type; Type: CONSTRAINT
--

ALTER TABLE ONLY order_addresses
    ADD CONSTRAINT uq_order_addresses_order_id_address_type UNIQUE (order_id, address_type);

--
-- Name: order_cancellations uq_order_cancellations_order_id; Type: CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT uq_order_cancellations_order_id UNIQUE (order_id);

--
-- Name: order_financial_snapshots uq_order_financial_snapshots_order_id; Type: CONSTRAINT
--

ALTER TABLE ONLY order_financial_snapshots
    ADD CONSTRAINT uq_order_financial_snapshots_order_id UNIQUE (order_id);

--
-- Name: orders uq_orders_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key);

--
-- Name: orders uq_orders_shop_id_order_number; Type: CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT uq_orders_shop_id_order_number UNIQUE (shop_id, order_number);

--
-- Name: outbox_events uq_outbox_events_event_id; Type: CONSTRAINT
--

ALTER TABLE ONLY outbox_events
    ADD CONSTRAINT uq_outbox_events_event_id UNIQUE (event_id);

--
-- Name: password_reset_tokens uq_password_reset_tokens_token_hash; Type: CONSTRAINT
--

ALTER TABLE ONLY password_reset_tokens
    ADD CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash);

--
-- Name: payment_webhook_events uq_payment_webhook_events_provider_event_id; Type: CONSTRAINT
--

ALTER TABLE ONLY payment_webhook_events
    ADD CONSTRAINT uq_payment_webhook_events_provider_event_id UNIQUE (provider, event_id);

--
-- Name: payments uq_payments_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key);

--
-- Name: payments uq_payments_provider_provider_payment_id; Type: CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT uq_payments_provider_provider_payment_id UNIQUE (provider, provider_payment_id);

--
-- Name: platform_settings uq_platform_settings_setting_key; Type: CONSTRAINT
--

ALTER TABLE ONLY platform_settings
    ADD CONSTRAINT uq_platform_settings_setting_key UNIQUE (setting_key);

--
-- Name: product_reviews uq_product_reviews_order_item_id; Type: CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT uq_product_reviews_order_item_id UNIQUE (order_item_id);

--
-- Name: product_skus uq_product_skus_shop_id_sku_code; Type: CONSTRAINT
--

ALTER TABLE ONLY product_skus
    ADD CONSTRAINT uq_product_skus_shop_id_sku_code UNIQUE (shop_id, sku_code);

--
-- Name: products uq_products_shop_id_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY products
    ADD CONSTRAINT uq_products_shop_id_slug UNIQUE (shop_id, slug);

--
-- Name: promotion_usages uq_promotion_usages_order_id_promotion_id; Type: CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT uq_promotion_usages_order_id_promotion_id UNIQUE (order_id, promotion_id);

--
-- Name: refresh_tokens uq_refresh_tokens_token_hash; Type: CONSTRAINT
--

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);

--
-- Name: refunds uq_refunds_idempotency_key; Type: CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT uq_refunds_idempotency_key UNIQUE (idempotency_key);

--
-- Name: shipment_tracking_events uq_shipment_tracking_events_shipment_id_external_event_id; Type: CONSTRAINT
--

ALTER TABLE ONLY shipment_tracking_events
    ADD CONSTRAINT uq_shipment_tracking_events_shipment_id_external_event_id UNIQUE (shipment_id, external_event_id);

--
-- Name: shop_categories uq_shop_categories_shop_id_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_categories
    ADD CONSTRAINT uq_shop_categories_shop_id_slug UNIQUE (shop_id, slug);

--
-- Name: shop_customers uq_shop_customers_shop_id_customer_code; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_customers
    ADD CONSTRAINT uq_shop_customers_shop_id_customer_code UNIQUE (shop_id, customer_code);

--
-- Name: shop_invitations uq_shop_invitations_token_hash; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT uq_shop_invitations_token_hash UNIQUE (token_hash);

--
-- Name: shop_members uq_shop_members_shop_id_user_id; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT uq_shop_members_shop_id_user_id UNIQUE (shop_id, user_id);

--
-- Name: shop_payment_gateways uq_shop_payment_gateways_shop_id_provider; Type: CONSTRAINT
--

ALTER TABLE ONLY shop_payment_gateways
    ADD CONSTRAINT uq_shop_payment_gateways_shop_id_provider UNIQUE (shop_id, provider);

--
-- Name: shops uq_shops_application_id; Type: CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT uq_shops_application_id UNIQUE (application_id);

--
-- Name: shops uq_shops_slug; Type: CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT uq_shops_slug UNIQUE (slug);

--
-- Name: users uq_users_email; Type: CONSTRAINT
--

ALTER TABLE ONLY users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

--
-- Name: users uq_users_phone; Type: CONSTRAINT
--

ALTER TABLE ONLY users
    ADD CONSTRAINT uq_users_phone UNIQUE (phone);

--
-- Name: ix_audit_logs_actor_id_occurred_at; Type: INDEX
--

CREATE INDEX ix_audit_logs_actor_id_occurred_at ON audit_logs USING btree (actor_id, occurred_at);

--
-- Name: ix_audit_logs_resource_type_resource_id; Type: INDEX
--

CREATE INDEX ix_audit_logs_resource_type_resource_id ON audit_logs USING btree (resource_type, resource_id);

--
-- Name: ix_audit_logs_shop_id_occurred_at; Type: INDEX
--

CREATE INDEX ix_audit_logs_shop_id_occurred_at ON audit_logs USING btree (shop_id, occurred_at);

--
-- Name: ix_auth_role_permissions_permission_id; Type: INDEX
--

CREATE INDEX ix_auth_role_permissions_permission_id ON auth_role_permissions USING btree (permission_id);

--
-- Name: ix_background_jobs_requested_by_user_id; Type: INDEX
--

CREATE INDEX ix_background_jobs_requested_by_user_id ON background_jobs USING btree (requested_by_user_id);

--
-- Name: ix_background_jobs_result_file_id; Type: INDEX
--

CREATE INDEX ix_background_jobs_result_file_id ON background_jobs USING btree (result_file_id);

--
-- Name: ix_background_jobs_shop_id; Type: INDEX
--

CREATE INDEX ix_background_jobs_shop_id ON background_jobs USING btree (shop_id);

--
-- Name: ix_blog_posts_author_id; Type: INDEX
--

CREATE INDEX ix_blog_posts_author_id ON blog_posts USING btree (author_id);

--
-- Name: ix_blog_posts_category_id; Type: INDEX
--

CREATE INDEX ix_blog_posts_category_id ON blog_posts USING btree (category_id);

--
-- Name: ix_blog_posts_cover_media_id; Type: INDEX
--

CREATE INDEX ix_blog_posts_cover_media_id ON blog_posts USING btree (cover_media_id);

--
-- Name: ix_buyer_addresses_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_buyer_addresses_buyer_user_id ON buyer_addresses USING btree (buyer_user_id);

--
-- Name: ix_cart_items_product_id; Type: INDEX
--

CREATE INDEX ix_cart_items_product_id ON cart_items USING btree (product_id);

--
-- Name: ix_cart_items_sku_id; Type: INDEX
--

CREATE INDEX ix_cart_items_sku_id ON cart_items USING btree (sku_id);

--
-- Name: ix_carts_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_carts_buyer_user_id ON carts USING btree (buyer_user_id);

--
-- Name: ix_carts_shop_id; Type: INDEX
--

CREATE INDEX ix_carts_shop_id ON carts USING btree (shop_id);

--
-- Name: ix_checkouts_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_checkouts_buyer_user_id ON checkouts USING btree (buyer_user_id);

--
-- Name: ix_checkouts_cart_id; Type: INDEX
--

CREATE INDEX ix_checkouts_cart_id ON checkouts USING btree (cart_id);

--
-- Name: ix_checkouts_shipping_address_id; Type: INDEX
--

CREATE INDEX ix_checkouts_shipping_address_id ON checkouts USING btree (shipping_address_id);

--
-- Name: ix_checkouts_shipping_method_id; Type: INDEX
--

CREATE INDEX ix_checkouts_shipping_method_id ON checkouts USING btree (shipping_method_id);

--
-- Name: ix_checkouts_shop_id; Type: INDEX
--

CREATE INDEX ix_checkouts_shop_id ON checkouts USING btree (shop_id);

--
-- Name: ix_coupons_promotion_id; Type: INDEX
--

CREATE INDEX ix_coupons_promotion_id ON coupons USING btree (promotion_id);

--
-- Name: ix_hub_categories_parent_id; Type: INDEX
--

CREATE INDEX ix_hub_categories_parent_id ON hub_categories USING btree (parent_id);

--
-- Name: ix_integration_events_shop_id; Type: INDEX
--

CREATE INDEX ix_integration_events_shop_id ON integration_events USING btree (shop_id);

--
-- Name: ix_inventory_ledger_actor_id; Type: INDEX
--

CREATE INDEX ix_inventory_ledger_actor_id ON inventory_ledger USING btree (actor_id);

--
-- Name: ix_inventory_ledger_order_item_id; Type: INDEX
--

CREATE INDEX ix_inventory_ledger_order_item_id ON inventory_ledger USING btree (order_item_id);

--
-- Name: ix_inventory_ledger_reservation_id; Type: INDEX
--

CREATE INDEX ix_inventory_ledger_reservation_id ON inventory_ledger USING btree (reservation_id);

--
-- Name: ix_inventory_ledger_shop_id; Type: INDEX
--

CREATE INDEX ix_inventory_ledger_shop_id ON inventory_ledger USING btree (shop_id);

--
-- Name: ix_inventory_ledger_sku_id; Type: INDEX
--

CREATE INDEX ix_inventory_ledger_sku_id ON inventory_ledger USING btree (sku_id);

--
-- Name: ix_inventory_levels_shop_id; Type: INDEX
--

CREATE INDEX ix_inventory_levels_shop_id ON inventory_levels USING btree (shop_id);

--
-- Name: ix_inventory_reservations_cart_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_cart_id ON inventory_reservations USING btree (cart_id);

--
-- Name: ix_inventory_reservations_checkout_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_checkout_id ON inventory_reservations USING btree (checkout_id);

--
-- Name: ix_inventory_reservations_order_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_order_id ON inventory_reservations USING btree (order_id);

--
-- Name: ix_inventory_reservations_order_item_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_order_item_id ON inventory_reservations USING btree (order_item_id);

--
-- Name: ix_inventory_reservations_shop_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_shop_id ON inventory_reservations USING btree (shop_id);

--
-- Name: ix_inventory_reservations_sku_id; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_sku_id ON inventory_reservations USING btree (sku_id);

--
-- Name: ix_inventory_reservations_status_expires_at; Type: INDEX
--

CREATE INDEX ix_inventory_reservations_status_expires_at ON inventory_reservations USING btree (status, expires_at);

--
-- Name: ix_media_files_shop_id; Type: INDEX
--

CREATE INDEX ix_media_files_shop_id ON media_files USING btree (shop_id);

--
-- Name: ix_media_files_source_job_id; Type: INDEX
--

CREATE INDEX ix_media_files_source_job_id ON media_files USING btree (source_job_id);

--
-- Name: ix_media_files_uploader_id; Type: INDEX
--

CREATE INDEX ix_media_files_uploader_id ON media_files USING btree (uploader_id);

--
-- Name: ix_merchant_application_reviews_application_id_decided_at; Type: INDEX
--

CREATE INDEX ix_merchant_application_reviews_application_id_decided_at ON merchant_application_reviews USING btree (application_id, decided_at);

--
-- Name: ix_merchant_application_reviews_reviewer_id; Type: INDEX
--

CREATE INDEX ix_merchant_application_reviews_reviewer_id ON merchant_application_reviews USING btree (reviewer_id);

--
-- Name: ix_merchant_applications_reviewer_id; Type: INDEX
--

CREATE INDEX ix_merchant_applications_reviewer_id ON merchant_applications USING btree (reviewer_id);

--
-- Name: ix_merchant_applications_status; Type: INDEX
--

CREATE INDEX ix_merchant_applications_status ON merchant_applications USING btree (status);

--
-- Name: ix_merchant_applications_user_id; Type: INDEX
--

CREATE INDEX ix_merchant_applications_user_id ON merchant_applications USING btree (user_id);

--
-- Name: ix_merchant_order_confirmations_confirmed_by_user_id; Type: INDEX
--

CREATE INDEX ix_merchant_order_confirmations_confirmed_by_user_id ON merchant_order_confirmations USING btree (confirmed_by_user_id);

--
-- Name: ix_merchant_order_confirmations_order_id; Type: INDEX
--

CREATE INDEX ix_merchant_order_confirmations_order_id ON merchant_order_confirmations USING btree (order_id);

--
-- Name: ix_merchant_order_confirmations_shop_id; Type: INDEX
--

CREATE INDEX ix_merchant_order_confirmations_shop_id ON merchant_order_confirmations USING btree (shop_id);

--
-- Name: ix_notifications_shop_id; Type: INDEX
--

CREATE INDEX ix_notifications_shop_id ON notifications USING btree (shop_id);

--
-- Name: ix_notifications_user_id; Type: INDEX
--

CREATE INDEX ix_notifications_user_id ON notifications USING btree (user_id);

--
-- Name: ix_oms_order_links_shop_id; Type: INDEX
--

CREATE INDEX ix_oms_order_links_shop_id ON oms_order_links USING btree (shop_id);

--
-- Name: ix_oms_shipment_links_shop_id; Type: INDEX
--

CREATE INDEX ix_oms_shipment_links_shop_id ON oms_shipment_links USING btree (shop_id);

--
-- Name: ix_oms_webhook_inbox_process_status; Type: INDEX
--

CREATE INDEX ix_oms_webhook_inbox_process_status ON oms_webhook_inbox USING btree (process_status);

--
-- Name: ix_oms_webhook_inbox_shop_id; Type: INDEX
--

CREATE INDEX ix_oms_webhook_inbox_shop_id ON oms_webhook_inbox USING btree (shop_id);

--
-- Name: ix_order_cancellations_processed_by; Type: INDEX
--

CREATE INDEX ix_order_cancellations_processed_by ON order_cancellations USING btree (processed_by);

--
-- Name: ix_order_cancellations_requested_by; Type: INDEX
--

CREATE INDEX ix_order_cancellations_requested_by ON order_cancellations USING btree (requested_by);

--
-- Name: ix_order_cancellations_shop_id; Type: INDEX
--

CREATE INDEX ix_order_cancellations_shop_id ON order_cancellations USING btree (shop_id);

--
-- Name: ix_order_financial_snapshots_shop_id_paid_at; Type: INDEX
--

CREATE INDEX ix_order_financial_snapshots_shop_id_paid_at ON order_financial_snapshots USING btree (shop_id, paid_at);

--
-- Name: ix_order_item_reveal_logs_order_id; Type: INDEX
--

CREATE INDEX ix_order_item_reveal_logs_order_id ON order_item_reveal_logs USING btree (order_id);

--
-- Name: ix_order_item_reveal_logs_revealed_by_user_id; Type: INDEX
--

CREATE INDEX ix_order_item_reveal_logs_revealed_by_user_id ON order_item_reveal_logs USING btree (revealed_by_user_id);

--
-- Name: ix_order_item_reveal_logs_shop_id; Type: INDEX
--

CREATE INDEX ix_order_item_reveal_logs_shop_id ON order_item_reveal_logs USING btree (shop_id);

--
-- Name: ix_order_items_order_id; Type: INDEX
--

CREATE INDEX ix_order_items_order_id ON order_items USING btree (order_id);

--
-- Name: ix_order_items_product_id; Type: INDEX
--

CREATE INDEX ix_order_items_product_id ON order_items USING btree (product_id);

--
-- Name: ix_order_items_shop_id; Type: INDEX
--

CREATE INDEX ix_order_items_shop_id ON order_items USING btree (shop_id);

--
-- Name: ix_order_items_sku_id; Type: INDEX
--

CREATE INDEX ix_order_items_sku_id ON order_items USING btree (sku_id);

--
-- Name: ix_order_status_history_changed_by; Type: INDEX
--

CREATE INDEX ix_order_status_history_changed_by ON order_status_history USING btree (changed_by);

--
-- Name: ix_order_status_history_order_id; Type: INDEX
--

CREATE INDEX ix_order_status_history_order_id ON order_status_history USING btree (order_id);

--
-- Name: ix_orders_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_orders_buyer_user_id ON orders USING btree (buyer_user_id);

--
-- Name: ix_orders_oms_sync_status; Type: INDEX
--

CREATE INDEX ix_orders_oms_sync_status ON orders USING btree (oms_sync_status);

--
-- Name: ix_orders_shop_customer_id; Type: INDEX
--

CREATE INDEX ix_orders_shop_customer_id ON orders USING btree (shop_customer_id);

--
-- Name: ix_orders_shop_id_created_date_time; Type: INDEX
--

CREATE INDEX ix_orders_shop_id_created_date_time ON orders USING btree (shop_id, created_date_time);

--
-- Name: ix_orders_shop_id_merchant_confirm_status_paid_at; Type: INDEX
--

CREATE INDEX ix_orders_shop_id_merchant_confirm_status_paid_at ON orders USING btree (shop_id, merchant_confirm_status, paid_at);

--
-- Name: ix_orders_status_auto_complete_at; Type: INDEX
--

CREATE INDEX ix_orders_status_auto_complete_at ON orders USING btree (status, auto_complete_at) WHERE ((status)::text = 'DELIVERED'::text);

--
-- Name: ix_otp_verifications_destination_hash_purpose_created_date_time; Type: INDEX
--

CREATE INDEX ix_otp_verifications_destination_hash_purpose_created_date_time ON otp_verifications USING btree (destination_hash, purpose, created_date_time);

--
-- Name: ix_otp_verifications_user_id; Type: INDEX
--

CREATE INDEX ix_otp_verifications_user_id ON otp_verifications USING btree (user_id);

--
-- Name: ix_outbox_events_shop_id; Type: INDEX
--

CREATE INDEX ix_outbox_events_shop_id ON outbox_events USING btree (shop_id);

--
-- Name: ix_outbox_events_status_next_attempt_at; Type: INDEX
--

CREATE INDEX ix_outbox_events_status_next_attempt_at ON outbox_events USING btree (status, next_attempt_at);

--
-- Name: ix_password_reset_tokens_user_id; Type: INDEX
--

CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens USING btree (user_id);

--
-- Name: ix_payment_proofs_media_file_id; Type: INDEX
--

CREATE INDEX ix_payment_proofs_media_file_id ON payment_proofs USING btree (media_file_id);

--
-- Name: ix_payment_proofs_order_id; Type: INDEX
--

CREATE INDEX ix_payment_proofs_order_id ON payment_proofs USING btree (order_id);

--
-- Name: ix_payment_proofs_payment_id; Type: INDEX
--

CREATE INDEX ix_payment_proofs_payment_id ON payment_proofs USING btree (payment_id);

--
-- Name: ix_payment_proofs_shop_id_status_created_date_time; Type: INDEX
--

CREATE INDEX ix_payment_proofs_shop_id_status_created_date_time ON payment_proofs USING btree (shop_id, status, created_date_time);

--
-- Name: ix_payment_proofs_uploaded_by_user_id; Type: INDEX
--

CREATE INDEX ix_payment_proofs_uploaded_by_user_id ON payment_proofs USING btree (uploaded_by_user_id);

--
-- Name: ix_payment_proofs_verified_by; Type: INDEX
--

CREATE INDEX ix_payment_proofs_verified_by ON payment_proofs USING btree (verified_by);

--
-- Name: ix_payment_webhook_events_order_id; Type: INDEX
--

CREATE INDEX ix_payment_webhook_events_order_id ON payment_webhook_events USING btree (order_id);

--
-- Name: ix_payment_webhook_events_payment_id; Type: INDEX
--

CREATE INDEX ix_payment_webhook_events_payment_id ON payment_webhook_events USING btree (payment_id);

--
-- Name: ix_payment_webhook_events_shop_id; Type: INDEX
--

CREATE INDEX ix_payment_webhook_events_shop_id ON payment_webhook_events USING btree (shop_id);

--
-- Name: ix_payments_gateway_id; Type: INDEX
--

CREATE INDEX ix_payments_gateway_id ON payments USING btree (gateway_id);

--
-- Name: ix_payments_order_id; Type: INDEX
--

CREATE INDEX ix_payments_order_id ON payments USING btree (order_id);

--
-- Name: ix_payments_shop_id; Type: INDEX
--

CREATE INDEX ix_payments_shop_id ON payments USING btree (shop_id);

--
-- Name: ix_platform_roles_granted_by; Type: INDEX
--

CREATE INDEX ix_platform_roles_granted_by ON platform_roles USING btree (granted_by);

--
-- Name: ix_platform_roles_revoked_by; Type: INDEX
--

CREATE INDEX ix_platform_roles_revoked_by ON platform_roles USING btree (revoked_by);

--
-- Name: ix_platform_roles_user_id; Type: INDEX
--

CREATE INDEX ix_platform_roles_user_id ON platform_roles USING btree (user_id);

--
-- Name: ix_product_category_map_shop_category_id; Type: INDEX
--

CREATE INDEX ix_product_category_map_shop_category_id ON product_category_map USING btree (shop_category_id);

--
-- Name: ix_product_images_media_file_id; Type: INDEX
--

CREATE INDEX ix_product_images_media_file_id ON product_images USING btree (media_file_id);

--
-- Name: ix_product_images_product_id; Type: INDEX
--

CREATE INDEX ix_product_images_product_id ON product_images USING btree (product_id);

--
-- Name: ix_product_imports_error_report_file_id; Type: INDEX
--

CREATE INDEX ix_product_imports_error_report_file_id ON product_imports USING btree (error_report_file_id);

--
-- Name: ix_product_imports_file_id; Type: INDEX
--

CREATE INDEX ix_product_imports_file_id ON product_imports USING btree (file_id);

--
-- Name: ix_product_imports_job_id; Type: INDEX
--

CREATE INDEX ix_product_imports_job_id ON product_imports USING btree (job_id);

--
-- Name: ix_product_imports_shop_id; Type: INDEX
--

CREATE INDEX ix_product_imports_shop_id ON product_imports USING btree (shop_id);

--
-- Name: ix_product_reviews_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_product_reviews_buyer_user_id ON product_reviews USING btree (buyer_user_id);

--
-- Name: ix_product_reviews_order_id; Type: INDEX
--

CREATE INDEX ix_product_reviews_order_id ON product_reviews USING btree (order_id);

--
-- Name: ix_product_reviews_product_id; Type: INDEX
--

CREATE INDEX ix_product_reviews_product_id ON product_reviews USING btree (product_id);

--
-- Name: ix_product_reviews_shop_id; Type: INDEX
--

CREATE INDEX ix_product_reviews_shop_id ON product_reviews USING btree (shop_id);

--
-- Name: ix_product_skus_product_id; Type: INDEX
--

CREATE INDEX ix_product_skus_product_id ON product_skus USING btree (product_id);

--
-- Name: ix_products_shop_id_status; Type: INDEX
--

CREATE INDEX ix_products_shop_id_status ON products USING btree (shop_id, status);

--
-- Name: ix_promotion_usages_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_promotion_usages_buyer_user_id ON promotion_usages USING btree (buyer_user_id);

--
-- Name: ix_promotion_usages_coupon_id; Type: INDEX
--

CREATE INDEX ix_promotion_usages_coupon_id ON promotion_usages USING btree (coupon_id);

--
-- Name: ix_promotion_usages_promotion_id; Type: INDEX
--

CREATE INDEX ix_promotion_usages_promotion_id ON promotion_usages USING btree (promotion_id);

--
-- Name: ix_promotions_shop_id; Type: INDEX
--

CREATE INDEX ix_promotions_shop_id ON promotions USING btree (shop_id);

--
-- Name: ix_refresh_tokens_expires_at; Type: INDEX
--

CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens USING btree (expires_at);

--
-- Name: ix_refresh_tokens_user_id; Type: INDEX
--

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens USING btree (user_id);

--
-- Name: ix_refunds_initiated_by; Type: INDEX
--

CREATE INDEX ix_refunds_initiated_by ON refunds USING btree (initiated_by);

--
-- Name: ix_refunds_order_id; Type: INDEX
--

CREATE INDEX ix_refunds_order_id ON refunds USING btree (order_id);

--
-- Name: ix_refunds_payment_id; Type: INDEX
--

CREATE INDEX ix_refunds_payment_id ON refunds USING btree (payment_id);

--
-- Name: ix_refunds_return_request_id; Type: INDEX
--

CREATE INDEX ix_refunds_return_request_id ON refunds USING btree (return_request_id);

--
-- Name: ix_refunds_shop_id; Type: INDEX
--

CREATE INDEX ix_refunds_shop_id ON refunds USING btree (shop_id);

--
-- Name: ix_return_items_order_item_id; Type: INDEX
--

CREATE INDEX ix_return_items_order_item_id ON return_items USING btree (order_item_id);

--
-- Name: ix_return_items_return_request_id; Type: INDEX
--

CREATE INDEX ix_return_items_return_request_id ON return_items USING btree (return_request_id);

--
-- Name: ix_return_requests_buyer_user_id; Type: INDEX
--

CREATE INDEX ix_return_requests_buyer_user_id ON return_requests USING btree (buyer_user_id);

--
-- Name: ix_return_requests_order_id; Type: INDEX
--

CREATE INDEX ix_return_requests_order_id ON return_requests USING btree (order_id);

--
-- Name: ix_return_requests_shop_id; Type: INDEX
--

CREATE INDEX ix_return_requests_shop_id ON return_requests USING btree (shop_id);

--
-- Name: ix_shipment_delivery_proofs_media_file_id; Type: INDEX
--

CREATE INDEX ix_shipment_delivery_proofs_media_file_id ON shipment_delivery_proofs USING btree (media_file_id);

--
-- Name: ix_shipment_delivery_proofs_shipment_id; Type: INDEX
--

CREATE INDEX ix_shipment_delivery_proofs_shipment_id ON shipment_delivery_proofs USING btree (shipment_id);

--
-- Name: ix_shipment_items_order_item_id; Type: INDEX
--

CREATE INDEX ix_shipment_items_order_item_id ON shipment_items USING btree (order_item_id);

--
-- Name: ix_shipment_items_shipment_id; Type: INDEX
--

CREATE INDEX ix_shipment_items_shipment_id ON shipment_items USING btree (shipment_id);

--
-- Name: ix_shipment_media_media_file_id; Type: INDEX
--

CREATE INDEX ix_shipment_media_media_file_id ON shipment_media USING btree (media_file_id);

--
-- Name: ix_shipment_media_shipment_id; Type: INDEX
--

CREATE INDEX ix_shipment_media_shipment_id ON shipment_media USING btree (shipment_id);

--
-- Name: ix_shipment_media_uploaded_by; Type: INDEX
--

CREATE INDEX ix_shipment_media_uploaded_by ON shipment_media USING btree (uploaded_by);

--
-- Name: ix_shipment_tracking_events_shipment_id_event_at; Type: INDEX
--

CREATE INDEX ix_shipment_tracking_events_shipment_id_event_at ON shipment_tracking_events USING btree (shipment_id, event_at);

--
-- Name: ix_shipment_tracking_events_shop_id; Type: INDEX
--

CREATE INDEX ix_shipment_tracking_events_shop_id ON shipment_tracking_events USING btree (shop_id);

--
-- Name: ix_shipments_dispatched_by; Type: INDEX
--

CREATE INDEX ix_shipments_dispatched_by ON shipments USING btree (dispatched_by);

--
-- Name: ix_shipments_order_id; Type: INDEX
--

CREATE INDEX ix_shipments_order_id ON shipments USING btree (order_id);

--
-- Name: ix_shipments_packed_by; Type: INDEX
--

CREATE INDEX ix_shipments_packed_by ON shipments USING btree (packed_by);

--
-- Name: ix_shipments_shipping_method_id; Type: INDEX
--

CREATE INDEX ix_shipments_shipping_method_id ON shipments USING btree (shipping_method_id);

--
-- Name: ix_shipments_shop_id; Type: INDEX
--

CREATE INDEX ix_shipments_shop_id ON shipments USING btree (shop_id);

--
-- Name: ix_shipments_tracking_number; Type: INDEX
--

CREATE INDEX ix_shipments_tracking_number ON shipments USING btree (tracking_number);

--
-- Name: ix_shipping_methods_shop_id; Type: INDEX
--

CREATE INDEX ix_shipping_methods_shop_id ON shipping_methods USING btree (shop_id);

--
-- Name: ix_shop_categories_parent_id; Type: INDEX
--

CREATE INDEX ix_shop_categories_parent_id ON shop_categories USING btree (parent_id);

--
-- Name: ix_shop_customers_shop_id_phone; Type: INDEX
--

CREATE INDEX ix_shop_customers_shop_id_phone ON shop_customers USING btree (shop_id, phone);

--
-- Name: ix_shop_customers_user_id; Type: INDEX
--

CREATE INDEX ix_shop_customers_user_id ON shop_customers USING btree (user_id);

--
-- Name: ix_shop_invitations_accepted_user_id; Type: INDEX
--

CREATE INDEX ix_shop_invitations_accepted_user_id ON shop_invitations USING btree (accepted_user_id);

--
-- Name: ix_shop_invitations_invited_by; Type: INDEX
--

CREATE INDEX ix_shop_invitations_invited_by ON shop_invitations USING btree (invited_by);

--
-- Name: ix_shop_invitations_revoked_by; Type: INDEX
--

CREATE INDEX ix_shop_invitations_revoked_by ON shop_invitations USING btree (revoked_by);

--
-- Name: ix_shop_invitations_shop_id; Type: INDEX
--

CREATE INDEX ix_shop_invitations_shop_id ON shop_invitations USING btree (shop_id);

--
-- Name: ix_shop_members_invited_by; Type: INDEX
--

CREATE INDEX ix_shop_members_invited_by ON shop_members USING btree (invited_by);

--
-- Name: ix_shop_members_removed_by; Type: INDEX
--

CREATE INDEX ix_shop_members_removed_by ON shop_members USING btree (removed_by);

--
-- Name: ix_shop_members_user_id; Type: INDEX
--

CREATE INDEX ix_shop_members_user_id ON shop_members USING btree (user_id);

--
-- Name: ix_shop_ownership_transfers_from_user_id; Type: INDEX
--

CREATE INDEX ix_shop_ownership_transfers_from_user_id ON shop_ownership_transfers USING btree (from_user_id);

--
-- Name: ix_shop_ownership_transfers_shop_id; Type: INDEX
--

CREATE INDEX ix_shop_ownership_transfers_shop_id ON shop_ownership_transfers USING btree (shop_id);

--
-- Name: ix_shop_ownership_transfers_to_member_id; Type: INDEX
--

CREATE INDEX ix_shop_ownership_transfers_to_member_id ON shop_ownership_transfers USING btree (to_member_id);

--
-- Name: ix_shop_ownership_transfers_to_user_id_status; Type: INDEX
--

CREATE INDEX ix_shop_ownership_transfers_to_user_id_status ON shop_ownership_transfers USING btree (to_user_id, status);

--
-- Name: ix_shops_closed_by; Type: INDEX
--

CREATE INDEX ix_shops_closed_by ON shops USING btree (closed_by);

--
-- Name: ix_shops_cover_media_id; Type: INDEX
--

CREATE INDEX ix_shops_cover_media_id ON shops USING btree (cover_media_id);

--
-- Name: ix_shops_hub_category_id; Type: INDEX
--

CREATE INDEX ix_shops_hub_category_id ON shops USING btree (hub_category_id);

--
-- Name: ix_shops_logo_media_id; Type: INDEX
--

CREATE INDEX ix_shops_logo_media_id ON shops USING btree (logo_media_id);

--
-- Name: ix_shops_owner_user_id; Type: INDEX
--

CREATE INDEX ix_shops_owner_user_id ON shops USING btree (owner_user_id);

--
-- Name: ix_shops_restored_by; Type: INDEX
--

CREATE INDEX ix_shops_restored_by ON shops USING btree (restored_by);

--
-- Name: ix_shops_suspended_by; Type: INDEX
--

CREATE INDEX ix_shops_suspended_by ON shops USING btree (suspended_by);

--
-- Name: ix_users_suspended_by; Type: INDEX
--

CREATE INDEX ix_users_suspended_by ON users USING btree (suspended_by);

--
-- Name: uq_carts_buyer_user_id_shop_id; Type: INDEX
--

CREATE UNIQUE INDEX uq_carts_buyer_user_id_shop_id ON carts USING btree (buyer_user_id, shop_id) WHERE ((status)::text = 'ACTIVE'::text);

--
-- Name: uq_merchant_applications_user_id_open; Type: INDEX
--

CREATE UNIQUE INDEX uq_merchant_applications_user_id_open ON merchant_applications USING btree (user_id) WHERE (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'NEEDS_INFORMATION'));

--
-- Name: uq_platform_roles_user_id_active; Type: INDEX
--

CREATE UNIQUE INDEX uq_platform_roles_user_id_active ON platform_roles USING btree (user_id) WHERE (revoked_at IS NULL);

--
-- Name: uq_shop_customers_shop_id_user_id; Type: INDEX
--

CREATE UNIQUE INDEX uq_shop_customers_shop_id_user_id ON shop_customers USING btree (shop_id, user_id) WHERE ((user_id IS NOT NULL) AND (deleted_at IS NULL));

--
-- Name: uq_shop_invitations_shop_id_email_pending; Type: INDEX
--

CREATE UNIQUE INDEX uq_shop_invitations_shop_id_email_pending ON shop_invitations USING btree (shop_id, lower((email)::text)) WHERE ((status)::text = 'PENDING'::text);

--
-- Name: uq_shop_members_shop_id_owner; Type: INDEX
--

CREATE UNIQUE INDEX uq_shop_members_shop_id_owner ON shop_members USING btree (shop_id) WHERE (((role)::text = 'STORE_OWNER'::text) AND ((status)::text = 'ACTIVE'::text));

--
-- Name: uq_shop_ownership_transfers_shop_id_pending; Type: INDEX
--

CREATE UNIQUE INDEX uq_shop_ownership_transfers_shop_id_pending ON shop_ownership_transfers USING btree (shop_id) WHERE ((status)::text = 'PENDING'::text);

--
-- Name: audit_logs trg_audit_logs_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_audit_logs_modified_date_time BEFORE UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: auth_permissions trg_auth_permissions_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_auth_permissions_modified_date_time BEFORE UPDATE ON auth_permissions FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: auth_role_permissions trg_auth_role_permissions_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_auth_role_permissions_modified_date_time BEFORE UPDATE ON auth_role_permissions FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: auth_roles trg_auth_roles_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_auth_roles_modified_date_time BEFORE UPDATE ON auth_roles FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: background_jobs trg_background_jobs_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_background_jobs_modified_date_time BEFORE UPDATE ON background_jobs FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: blog_categories trg_blog_categories_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_blog_categories_modified_date_time BEFORE UPDATE ON blog_categories FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: blog_posts trg_blog_posts_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_blog_posts_modified_date_time BEFORE UPDATE ON blog_posts FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: buyer_addresses trg_buyer_addresses_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_buyer_addresses_modified_date_time BEFORE UPDATE ON buyer_addresses FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: cart_items trg_cart_items_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_cart_items_modified_date_time BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: carts trg_carts_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_carts_modified_date_time BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: checkouts trg_checkouts_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_checkouts_modified_date_time BEFORE UPDATE ON checkouts FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: coupons trg_coupons_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_coupons_modified_date_time BEFORE UPDATE ON coupons FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: hub_categories trg_hub_categories_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_hub_categories_modified_date_time BEFORE UPDATE ON hub_categories FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: integration_events trg_integration_events_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_integration_events_modified_date_time BEFORE UPDATE ON integration_events FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: inventory_ledger trg_inventory_ledger_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_inventory_ledger_modified_date_time BEFORE UPDATE ON inventory_ledger FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: inventory_levels trg_inventory_levels_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_inventory_levels_modified_date_time BEFORE UPDATE ON inventory_levels FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: inventory_reservations trg_inventory_reservations_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_inventory_reservations_modified_date_time BEFORE UPDATE ON inventory_reservations FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: media_files trg_media_files_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_media_files_modified_date_time BEFORE UPDATE ON media_files FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: media_variants trg_media_variants_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_media_variants_modified_date_time BEFORE UPDATE ON media_variants FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: merchant_application_reviews trg_merchant_application_reviews_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_merchant_application_reviews_modified_date_time BEFORE UPDATE ON merchant_application_reviews FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: merchant_applications trg_merchant_applications_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_merchant_applications_modified_date_time BEFORE UPDATE ON merchant_applications FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: merchant_documents trg_merchant_documents_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_merchant_documents_modified_date_time BEFORE UPDATE ON merchant_documents FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: merchant_order_confirmations trg_merchant_order_confirmations_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_merchant_order_confirmations_modified_date_time BEFORE UPDATE ON merchant_order_confirmations FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: notifications trg_notifications_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_notifications_modified_date_time BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: oms_connections trg_oms_connections_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_oms_connections_modified_date_time BEFORE UPDATE ON oms_connections FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: oms_order_links trg_oms_order_links_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_oms_order_links_modified_date_time BEFORE UPDATE ON oms_order_links FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: oms_shipment_links trg_oms_shipment_links_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_oms_shipment_links_modified_date_time BEFORE UPDATE ON oms_shipment_links FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: oms_sync_checkpoints trg_oms_sync_checkpoints_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_oms_sync_checkpoints_modified_date_time BEFORE UPDATE ON oms_sync_checkpoints FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: oms_webhook_inbox trg_oms_webhook_inbox_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_oms_webhook_inbox_modified_date_time BEFORE UPDATE ON oms_webhook_inbox FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_addresses trg_order_addresses_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_addresses_modified_date_time BEFORE UPDATE ON order_addresses FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_cancellations trg_order_cancellations_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_cancellations_modified_date_time BEFORE UPDATE ON order_cancellations FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_financial_snapshots trg_order_financial_snapshots_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_financial_snapshots_modified_date_time BEFORE UPDATE ON order_financial_snapshots FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_item_reveal_logs trg_order_item_reveal_logs_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_item_reveal_logs_modified_date_time BEFORE UPDATE ON order_item_reveal_logs FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_items trg_order_items_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_items_modified_date_time BEFORE UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: order_status_history trg_order_status_history_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_order_status_history_modified_date_time BEFORE UPDATE ON order_status_history FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: orders trg_orders_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_orders_modified_date_time BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: otp_verifications trg_otp_verifications_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_otp_verifications_modified_date_time BEFORE UPDATE ON otp_verifications FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: outbox_events trg_outbox_events_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_outbox_events_modified_date_time BEFORE UPDATE ON outbox_events FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: password_reset_tokens trg_password_reset_tokens_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_password_reset_tokens_modified_date_time BEFORE UPDATE ON password_reset_tokens FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: payment_proofs trg_payment_proofs_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_payment_proofs_modified_date_time BEFORE UPDATE ON payment_proofs FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: payment_webhook_events trg_payment_webhook_events_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_payment_webhook_events_modified_date_time BEFORE UPDATE ON payment_webhook_events FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: payments trg_payments_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_payments_modified_date_time BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: platform_roles trg_platform_roles_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_platform_roles_modified_date_time BEFORE UPDATE ON platform_roles FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: platform_settings trg_platform_settings_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_platform_settings_modified_date_time BEFORE UPDATE ON platform_settings FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: product_category_map trg_product_category_map_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_product_category_map_modified_date_time BEFORE UPDATE ON product_category_map FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: product_images trg_product_images_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_product_images_modified_date_time BEFORE UPDATE ON product_images FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: product_imports trg_product_imports_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_product_imports_modified_date_time BEFORE UPDATE ON product_imports FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: product_reviews trg_product_reviews_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_product_reviews_modified_date_time BEFORE UPDATE ON product_reviews FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: product_skus trg_product_skus_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_product_skus_modified_date_time BEFORE UPDATE ON product_skus FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: products trg_products_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_products_modified_date_time BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: promotion_usages trg_promotion_usages_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_promotion_usages_modified_date_time BEFORE UPDATE ON promotion_usages FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: promotions trg_promotions_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_promotions_modified_date_time BEFORE UPDATE ON promotions FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: refresh_tokens trg_refresh_tokens_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_refresh_tokens_modified_date_time BEFORE UPDATE ON refresh_tokens FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: refunds trg_refunds_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_refunds_modified_date_time BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: return_items trg_return_items_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_return_items_modified_date_time BEFORE UPDATE ON return_items FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: return_requests trg_return_requests_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_return_requests_modified_date_time BEFORE UPDATE ON return_requests FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipment_delivery_proofs trg_shipment_delivery_proofs_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipment_delivery_proofs_modified_date_time BEFORE UPDATE ON shipment_delivery_proofs FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipment_items trg_shipment_items_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipment_items_modified_date_time BEFORE UPDATE ON shipment_items FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipment_media trg_shipment_media_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipment_media_modified_date_time BEFORE UPDATE ON shipment_media FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipment_tracking_events trg_shipment_tracking_events_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipment_tracking_events_modified_date_time BEFORE UPDATE ON shipment_tracking_events FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipments trg_shipments_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipments_modified_date_time BEFORE UPDATE ON shipments FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shipping_methods trg_shipping_methods_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shipping_methods_modified_date_time BEFORE UPDATE ON shipping_methods FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_categories trg_shop_categories_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_categories_modified_date_time BEFORE UPDATE ON shop_categories FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_customers trg_shop_customers_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_customers_modified_date_time BEFORE UPDATE ON shop_customers FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_invitations trg_shop_invitations_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_invitations_modified_date_time BEFORE UPDATE ON shop_invitations FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_members trg_shop_members_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_members_modified_date_time BEFORE UPDATE ON shop_members FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_members trg_shop_members_owner_guard; Type: TRIGGER
--

CREATE CONSTRAINT TRIGGER trg_shop_members_owner_guard AFTER INSERT OR DELETE OR UPDATE ON shop_members DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_shop_single_owner();

--
-- Name: TRIGGER trg_shop_members_owner_guard ON shop_members; Type: COMMENT
--

COMMENT ON TRIGGER trg_shop_members_owner_guard ON shop_members IS 'authz: Owner ACTIVE 1 คน = shops.owner_user_id (ตรวจตอน COMMIT)';

--
-- Name: shop_ownership_transfers trg_shop_ownership_transfers_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_ownership_transfers_modified_date_time BEFORE UPDATE ON shop_ownership_transfers FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shop_payment_gateways trg_shop_payment_gateways_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shop_payment_gateways_modified_date_time BEFORE UPDATE ON shop_payment_gateways FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shops trg_shops_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_shops_modified_date_time BEFORE UPDATE ON shops FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: shops trg_shops_owner_guard; Type: TRIGGER
--

CREATE CONSTRAINT TRIGGER trg_shops_owner_guard AFTER INSERT OR UPDATE OF owner_user_id, status, deleted_at ON shops DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_shop_single_owner();

--
-- Name: TRIGGER trg_shops_owner_guard ON shops; Type: COMMENT
--

COMMENT ON TRIGGER trg_shops_owner_guard ON shops IS 'authz: Owner ACTIVE 1 คน = shops.owner_user_id (ตรวจตอน COMMIT)';

--
-- Name: users trg_users_modified_date_time; Type: TRIGGER
--

CREATE TRIGGER trg_users_modified_date_time BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

--
-- Name: audit_logs fk_audit_logs_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY audit_logs
    ADD CONSTRAINT fk_audit_logs_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: auth_role_permissions fk_auth_role_permissions_permission_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY auth_role_permissions
    ADD CONSTRAINT fk_auth_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES auth_permissions(id) ON DELETE CASCADE;

--
-- Name: auth_role_permissions fk_auth_role_permissions_role_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY auth_role_permissions
    ADD CONSTRAINT fk_auth_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES auth_roles(id) ON DELETE CASCADE;

--
-- Name: background_jobs fk_background_jobs_requested_by_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY background_jobs
    ADD CONSTRAINT fk_background_jobs_requested_by_user_id FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: background_jobs fk_background_jobs_result_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY background_jobs
    ADD CONSTRAINT fk_background_jobs_result_file_id FOREIGN KEY (result_file_id) REFERENCES media_files(id) ON DELETE SET NULL;

--
-- Name: background_jobs fk_background_jobs_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY background_jobs
    ADD CONSTRAINT fk_background_jobs_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: blog_posts fk_blog_posts_author_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY blog_posts
    ADD CONSTRAINT fk_blog_posts_author_id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: blog_posts fk_blog_posts_category_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY blog_posts
    ADD CONSTRAINT fk_blog_posts_category_id FOREIGN KEY (category_id) REFERENCES blog_categories(id) ON DELETE SET NULL;

--
-- Name: blog_posts fk_blog_posts_cover_media_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY blog_posts
    ADD CONSTRAINT fk_blog_posts_cover_media_id FOREIGN KEY (cover_media_id) REFERENCES media_files(id) ON DELETE SET NULL;

--
-- Name: buyer_addresses fk_buyer_addresses_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY buyer_addresses
    ADD CONSTRAINT fk_buyer_addresses_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: cart_items fk_cart_items_cart_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT fk_cart_items_cart_id FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE;

--
-- Name: cart_items fk_cart_items_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT fk_cart_items_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

--
-- Name: cart_items fk_cart_items_sku_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT fk_cart_items_sku_id FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE RESTRICT;

--
-- Name: carts fk_carts_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY carts
    ADD CONSTRAINT fk_carts_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: carts fk_carts_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY carts
    ADD CONSTRAINT fk_carts_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: checkouts fk_checkouts_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: checkouts fk_checkouts_cart_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_cart_id FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE RESTRICT;

--
-- Name: checkouts fk_checkouts_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

--
-- Name: checkouts fk_checkouts_shipping_address_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_shipping_address_id FOREIGN KEY (shipping_address_id) REFERENCES buyer_addresses(id) ON DELETE SET NULL;

--
-- Name: checkouts fk_checkouts_shipping_method_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_shipping_method_id FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id) ON DELETE SET NULL;

--
-- Name: checkouts fk_checkouts_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY checkouts
    ADD CONSTRAINT fk_checkouts_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: coupons fk_coupons_promotion_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY coupons
    ADD CONSTRAINT fk_coupons_promotion_id FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE;

--
-- Name: coupons fk_coupons_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY coupons
    ADD CONSTRAINT fk_coupons_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: hub_categories fk_hub_categories_parent_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY hub_categories
    ADD CONSTRAINT fk_hub_categories_parent_id FOREIGN KEY (parent_id) REFERENCES hub_categories(id) ON DELETE SET NULL;

--
-- Name: integration_events fk_integration_events_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY integration_events
    ADD CONSTRAINT fk_integration_events_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: inventory_ledger fk_inventory_ledger_actor_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_actor_id FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: inventory_ledger fk_inventory_ledger_order_item_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_order_item_id FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE SET NULL;

--
-- Name: inventory_ledger fk_inventory_ledger_reservation_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_reservation_id FOREIGN KEY (reservation_id) REFERENCES inventory_reservations(id) ON DELETE SET NULL;

--
-- Name: inventory_ledger fk_inventory_ledger_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: inventory_ledger fk_inventory_ledger_sku_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_sku_id FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE RESTRICT;

--
-- Name: inventory_levels fk_inventory_levels_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_levels
    ADD CONSTRAINT fk_inventory_levels_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: inventory_levels fk_inventory_levels_sku_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_levels
    ADD CONSTRAINT fk_inventory_levels_sku_id FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE CASCADE;

--
-- Name: inventory_reservations fk_inventory_reservations_cart_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_cart_id FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE SET NULL;

--
-- Name: inventory_reservations fk_inventory_reservations_checkout_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_checkout_id FOREIGN KEY (checkout_id) REFERENCES checkouts(id) ON DELETE SET NULL;

--
-- Name: inventory_reservations fk_inventory_reservations_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

--
-- Name: inventory_reservations fk_inventory_reservations_order_item_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_order_item_id FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE SET NULL;

--
-- Name: inventory_reservations fk_inventory_reservations_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: inventory_reservations fk_inventory_reservations_sku_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_sku_id FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE RESTRICT;

--
-- Name: media_files fk_media_files_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY media_files
    ADD CONSTRAINT fk_media_files_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: media_files fk_media_files_source_job_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY media_files
    ADD CONSTRAINT fk_media_files_source_job_id FOREIGN KEY (source_job_id) REFERENCES background_jobs(id) ON DELETE SET NULL;

--
-- Name: media_files fk_media_files_uploader_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY media_files
    ADD CONSTRAINT fk_media_files_uploader_id FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: media_variants fk_media_variants_media_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY media_variants
    ADD CONSTRAINT fk_media_variants_media_file_id FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE;

--
-- Name: merchant_application_reviews fk_merchant_application_reviews_application_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_application_reviews
    ADD CONSTRAINT fk_merchant_application_reviews_application_id FOREIGN KEY (application_id) REFERENCES merchant_applications(id) ON DELETE CASCADE;

--
-- Name: merchant_application_reviews fk_merchant_application_reviews_reviewer_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_application_reviews
    ADD CONSTRAINT fk_merchant_application_reviews_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: merchant_applications fk_merchant_applications_reviewer_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT fk_merchant_applications_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: merchant_applications fk_merchant_applications_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT fk_merchant_applications_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: merchant_applications fk_merchant_applications_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_applications
    ADD CONSTRAINT fk_merchant_applications_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: merchant_documents fk_merchant_documents_application_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_documents
    ADD CONSTRAINT fk_merchant_documents_application_id FOREIGN KEY (application_id) REFERENCES merchant_applications(id) ON DELETE CASCADE;

--
-- Name: merchant_order_confirmations fk_merchant_order_confirmations_confirmed_by_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_order_confirmations
    ADD CONSTRAINT fk_merchant_order_confirmations_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: merchant_order_confirmations fk_merchant_order_confirmations_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_order_confirmations
    ADD CONSTRAINT fk_merchant_order_confirmations_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: merchant_order_confirmations fk_merchant_order_confirmations_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY merchant_order_confirmations
    ADD CONSTRAINT fk_merchant_order_confirmations_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: notifications fk_notifications_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT fk_notifications_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: notifications fk_notifications_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY notifications
    ADD CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: oms_connections fk_oms_connections_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_connections
    ADD CONSTRAINT fk_oms_connections_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: oms_order_links fk_oms_order_links_oms_connection_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT fk_oms_order_links_oms_connection_id FOREIGN KEY (oms_connection_id) REFERENCES oms_connections(id) ON DELETE CASCADE;

--
-- Name: oms_order_links fk_oms_order_links_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT fk_oms_order_links_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: oms_order_links fk_oms_order_links_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_order_links
    ADD CONSTRAINT fk_oms_order_links_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: oms_shipment_links fk_oms_shipment_links_oms_connection_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT fk_oms_shipment_links_oms_connection_id FOREIGN KEY (oms_connection_id) REFERENCES oms_connections(id) ON DELETE CASCADE;

--
-- Name: oms_shipment_links fk_oms_shipment_links_shipment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT fk_oms_shipment_links_shipment_id FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE;

--
-- Name: oms_shipment_links fk_oms_shipment_links_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_shipment_links
    ADD CONSTRAINT fk_oms_shipment_links_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: oms_sync_checkpoints fk_oms_sync_checkpoints_oms_connection_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_sync_checkpoints
    ADD CONSTRAINT fk_oms_sync_checkpoints_oms_connection_id FOREIGN KEY (oms_connection_id) REFERENCES oms_connections(id) ON DELETE CASCADE;

--
-- Name: oms_webhook_inbox fk_oms_webhook_inbox_oms_connection_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_webhook_inbox
    ADD CONSTRAINT fk_oms_webhook_inbox_oms_connection_id FOREIGN KEY (oms_connection_id) REFERENCES oms_connections(id) ON DELETE SET NULL;

--
-- Name: oms_webhook_inbox fk_oms_webhook_inbox_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY oms_webhook_inbox
    ADD CONSTRAINT fk_oms_webhook_inbox_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: order_addresses fk_order_addresses_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_addresses
    ADD CONSTRAINT fk_order_addresses_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: order_cancellations fk_order_cancellations_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT fk_order_cancellations_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: order_cancellations fk_order_cancellations_processed_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT fk_order_cancellations_processed_by FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: order_cancellations fk_order_cancellations_requested_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT fk_order_cancellations_requested_by FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: order_cancellations fk_order_cancellations_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_cancellations
    ADD CONSTRAINT fk_order_cancellations_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: order_financial_snapshots fk_order_financial_snapshots_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_financial_snapshots
    ADD CONSTRAINT fk_order_financial_snapshots_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: order_financial_snapshots fk_order_financial_snapshots_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_financial_snapshots
    ADD CONSTRAINT fk_order_financial_snapshots_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: order_item_reveal_logs fk_order_item_reveal_logs_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_item_reveal_logs
    ADD CONSTRAINT fk_order_item_reveal_logs_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: order_item_reveal_logs fk_order_item_reveal_logs_revealed_by_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_item_reveal_logs
    ADD CONSTRAINT fk_order_item_reveal_logs_revealed_by_user_id FOREIGN KEY (revealed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: order_item_reveal_logs fk_order_item_reveal_logs_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_item_reveal_logs
    ADD CONSTRAINT fk_order_item_reveal_logs_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: order_items fk_order_items_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_items
    ADD CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: order_items fk_order_items_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_items
    ADD CONSTRAINT fk_order_items_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL;

--
-- Name: order_items fk_order_items_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_items
    ADD CONSTRAINT fk_order_items_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: order_items fk_order_items_sku_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_items
    ADD CONSTRAINT fk_order_items_sku_id FOREIGN KEY (sku_id) REFERENCES product_skus(id) ON DELETE SET NULL;

--
-- Name: order_status_history fk_order_status_history_changed_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_status_history
    ADD CONSTRAINT fk_order_status_history_changed_by FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: order_status_history fk_order_status_history_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY order_status_history
    ADD CONSTRAINT fk_order_status_history_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: orders fk_orders_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT fk_orders_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: orders fk_orders_shop_customer_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT fk_orders_shop_customer_id FOREIGN KEY (shop_customer_id) REFERENCES shop_customers(id) ON DELETE SET NULL;

--
-- Name: orders fk_orders_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY orders
    ADD CONSTRAINT fk_orders_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: otp_verifications fk_otp_verifications_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY otp_verifications
    ADD CONSTRAINT fk_otp_verifications_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: outbox_events fk_outbox_events_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY outbox_events
    ADD CONSTRAINT fk_outbox_events_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: password_reset_tokens fk_password_reset_tokens_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY password_reset_tokens
    ADD CONSTRAINT fk_password_reset_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: payment_proofs fk_payment_proofs_media_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_media_file_id FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE RESTRICT;

--
-- Name: payment_proofs fk_payment_proofs_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: payment_proofs fk_payment_proofs_payment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT;

--
-- Name: payment_proofs fk_payment_proofs_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: payment_proofs fk_payment_proofs_uploaded_by_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_uploaded_by_user_id FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: payment_proofs fk_payment_proofs_verified_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_proofs
    ADD CONSTRAINT fk_payment_proofs_verified_by FOREIGN KEY (verified_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: payment_webhook_events fk_payment_webhook_events_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_webhook_events
    ADD CONSTRAINT fk_payment_webhook_events_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

--
-- Name: payment_webhook_events fk_payment_webhook_events_payment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_webhook_events
    ADD CONSTRAINT fk_payment_webhook_events_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE SET NULL;

--
-- Name: payment_webhook_events fk_payment_webhook_events_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payment_webhook_events
    ADD CONSTRAINT fk_payment_webhook_events_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE SET NULL;

--
-- Name: payments fk_payments_gateway_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT fk_payments_gateway_id FOREIGN KEY (gateway_id) REFERENCES shop_payment_gateways(id) ON DELETE SET NULL;

--
-- Name: payments fk_payments_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT fk_payments_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: payments fk_payments_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY payments
    ADD CONSTRAINT fk_payments_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: platform_roles fk_platform_roles_granted_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY platform_roles
    ADD CONSTRAINT fk_platform_roles_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: platform_roles fk_platform_roles_revoked_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY platform_roles
    ADD CONSTRAINT fk_platform_roles_revoked_by FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: platform_roles fk_platform_roles_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY platform_roles
    ADD CONSTRAINT fk_platform_roles_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: product_category_map fk_product_category_map_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_category_map
    ADD CONSTRAINT fk_product_category_map_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

--
-- Name: product_category_map fk_product_category_map_shop_category_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_category_map
    ADD CONSTRAINT fk_product_category_map_shop_category_id FOREIGN KEY (shop_category_id) REFERENCES shop_categories(id) ON DELETE CASCADE;

--
-- Name: product_images fk_product_images_media_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_images
    ADD CONSTRAINT fk_product_images_media_file_id FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE RESTRICT;

--
-- Name: product_images fk_product_images_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_images
    ADD CONSTRAINT fk_product_images_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

--
-- Name: product_imports fk_product_imports_error_report_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_imports
    ADD CONSTRAINT fk_product_imports_error_report_file_id FOREIGN KEY (error_report_file_id) REFERENCES media_files(id) ON DELETE SET NULL;

--
-- Name: product_imports fk_product_imports_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_imports
    ADD CONSTRAINT fk_product_imports_file_id FOREIGN KEY (file_id) REFERENCES media_files(id) ON DELETE RESTRICT;

--
-- Name: product_imports fk_product_imports_job_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_imports
    ADD CONSTRAINT fk_product_imports_job_id FOREIGN KEY (job_id) REFERENCES background_jobs(id) ON DELETE SET NULL;

--
-- Name: product_imports fk_product_imports_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_imports
    ADD CONSTRAINT fk_product_imports_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: product_reviews fk_product_reviews_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT fk_product_reviews_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: product_reviews fk_product_reviews_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT fk_product_reviews_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

--
-- Name: product_reviews fk_product_reviews_order_item_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT fk_product_reviews_order_item_id FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE SET NULL;

--
-- Name: product_reviews fk_product_reviews_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT fk_product_reviews_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

--
-- Name: product_reviews fk_product_reviews_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_reviews
    ADD CONSTRAINT fk_product_reviews_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: product_skus fk_product_skus_product_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_skus
    ADD CONSTRAINT fk_product_skus_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

--
-- Name: product_skus fk_product_skus_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY product_skus
    ADD CONSTRAINT fk_product_skus_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: products fk_products_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY products
    ADD CONSTRAINT fk_products_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: promotion_usages fk_promotion_usages_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT fk_promotion_usages_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: promotion_usages fk_promotion_usages_coupon_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT fk_promotion_usages_coupon_id FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE SET NULL;

--
-- Name: promotion_usages fk_promotion_usages_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT fk_promotion_usages_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

--
-- Name: promotion_usages fk_promotion_usages_promotion_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY promotion_usages
    ADD CONSTRAINT fk_promotion_usages_promotion_id FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE RESTRICT;

--
-- Name: promotions fk_promotions_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY promotions
    ADD CONSTRAINT fk_promotions_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: refresh_tokens fk_refresh_tokens_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: refunds fk_refunds_initiated_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT fk_refunds_initiated_by FOREIGN KEY (initiated_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: refunds fk_refunds_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT fk_refunds_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: refunds fk_refunds_payment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT fk_refunds_payment_id FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE SET NULL;

--
-- Name: refunds fk_refunds_return_request_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT fk_refunds_return_request_id FOREIGN KEY (return_request_id) REFERENCES return_requests(id) ON DELETE SET NULL;

--
-- Name: refunds fk_refunds_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY refunds
    ADD CONSTRAINT fk_refunds_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: return_items fk_return_items_order_item_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY return_items
    ADD CONSTRAINT fk_return_items_order_item_id FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT;

--
-- Name: return_items fk_return_items_return_request_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY return_items
    ADD CONSTRAINT fk_return_items_return_request_id FOREIGN KEY (return_request_id) REFERENCES return_requests(id) ON DELETE CASCADE;

--
-- Name: return_requests fk_return_requests_buyer_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY return_requests
    ADD CONSTRAINT fk_return_requests_buyer_user_id FOREIGN KEY (buyer_user_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: return_requests fk_return_requests_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY return_requests
    ADD CONSTRAINT fk_return_requests_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: return_requests fk_return_requests_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY return_requests
    ADD CONSTRAINT fk_return_requests_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: shipment_delivery_proofs fk_shipment_delivery_proofs_media_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_delivery_proofs
    ADD CONSTRAINT fk_shipment_delivery_proofs_media_file_id FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE RESTRICT;

--
-- Name: shipment_delivery_proofs fk_shipment_delivery_proofs_shipment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_delivery_proofs
    ADD CONSTRAINT fk_shipment_delivery_proofs_shipment_id FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE RESTRICT;

--
-- Name: shipment_items fk_shipment_items_order_item_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_items
    ADD CONSTRAINT fk_shipment_items_order_item_id FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT;

--
-- Name: shipment_items fk_shipment_items_shipment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_items
    ADD CONSTRAINT fk_shipment_items_shipment_id FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE;

--
-- Name: shipment_media fk_shipment_media_media_file_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_media
    ADD CONSTRAINT fk_shipment_media_media_file_id FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE RESTRICT;

--
-- Name: shipment_media fk_shipment_media_shipment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_media
    ADD CONSTRAINT fk_shipment_media_shipment_id FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE RESTRICT;

--
-- Name: shipment_media fk_shipment_media_uploaded_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_media
    ADD CONSTRAINT fk_shipment_media_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shipment_tracking_events fk_shipment_tracking_events_shipment_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_tracking_events
    ADD CONSTRAINT fk_shipment_tracking_events_shipment_id FOREIGN KEY (shipment_id) REFERENCES shipments(id) ON DELETE CASCADE;

--
-- Name: shipment_tracking_events fk_shipment_tracking_events_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipment_tracking_events
    ADD CONSTRAINT fk_shipment_tracking_events_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: shipments fk_shipments_dispatched_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT fk_shipments_dispatched_by FOREIGN KEY (dispatched_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shipments fk_shipments_order_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT fk_shipments_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

--
-- Name: shipments fk_shipments_packed_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT fk_shipments_packed_by FOREIGN KEY (packed_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shipments fk_shipments_shipping_method_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT fk_shipments_shipping_method_id FOREIGN KEY (shipping_method_id) REFERENCES shipping_methods(id) ON DELETE SET NULL;

--
-- Name: shipments fk_shipments_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipments
    ADD CONSTRAINT fk_shipments_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE RESTRICT;

--
-- Name: shipping_methods fk_shipping_methods_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shipping_methods
    ADD CONSTRAINT fk_shipping_methods_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_categories fk_shop_categories_parent_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_categories
    ADD CONSTRAINT fk_shop_categories_parent_id FOREIGN KEY (parent_id) REFERENCES shop_categories(id) ON DELETE SET NULL;

--
-- Name: shop_categories fk_shop_categories_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_categories
    ADD CONSTRAINT fk_shop_categories_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_customers fk_shop_customers_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_customers
    ADD CONSTRAINT fk_shop_customers_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_customers fk_shop_customers_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_customers
    ADD CONSTRAINT fk_shop_customers_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shop_invitations fk_shop_invitations_accepted_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT fk_shop_invitations_accepted_user_id FOREIGN KEY (accepted_user_id) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shop_invitations fk_shop_invitations_invited_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT fk_shop_invitations_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: shop_invitations fk_shop_invitations_revoked_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT fk_shop_invitations_revoked_by FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shop_invitations fk_shop_invitations_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_invitations
    ADD CONSTRAINT fk_shop_invitations_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_members fk_shop_members_invited_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT fk_shop_members_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shop_members fk_shop_members_removed_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT fk_shop_members_removed_by FOREIGN KEY (removed_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shop_members fk_shop_members_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT fk_shop_members_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_members fk_shop_members_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_members
    ADD CONSTRAINT fk_shop_members_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

--
-- Name: shop_ownership_transfers fk_shop_ownership_transfers_from_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_ownership_transfers
    ADD CONSTRAINT fk_shop_ownership_transfers_from_user_id FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: shop_ownership_transfers fk_shop_ownership_transfers_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_ownership_transfers
    ADD CONSTRAINT fk_shop_ownership_transfers_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shop_ownership_transfers fk_shop_ownership_transfers_to_member_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_ownership_transfers
    ADD CONSTRAINT fk_shop_ownership_transfers_to_member_id FOREIGN KEY (to_member_id) REFERENCES shop_members(id);

--
-- Name: shop_ownership_transfers fk_shop_ownership_transfers_to_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_ownership_transfers
    ADD CONSTRAINT fk_shop_ownership_transfers_to_user_id FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: shop_payment_gateways fk_shop_payment_gateways_shop_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shop_payment_gateways
    ADD CONSTRAINT fk_shop_payment_gateways_shop_id FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE;

--
-- Name: shops fk_shops_application_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_application_id FOREIGN KEY (application_id) REFERENCES merchant_applications(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_closed_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_closed_by FOREIGN KEY (closed_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_cover_media_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_cover_media_id FOREIGN KEY (cover_media_id) REFERENCES media_files(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_hub_category_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_hub_category_id FOREIGN KEY (hub_category_id) REFERENCES hub_categories(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_logo_media_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_logo_media_id FOREIGN KEY (logo_media_id) REFERENCES media_files(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_owner_user_id; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT;

--
-- Name: shops fk_shops_restored_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_restored_by FOREIGN KEY (restored_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: shops fk_shops_suspended_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY shops
    ADD CONSTRAINT fk_shops_suspended_by FOREIGN KEY (suspended_by) REFERENCES users(id) ON DELETE SET NULL;

--
-- Name: users fk_users_suspended_by; Type: FK CONSTRAINT
--

ALTER TABLE ONLY users
    ADD CONSTRAINT fk_users_suspended_by FOREIGN KEY (suspended_by) REFERENCES users(id) ON DELETE SET NULL;

