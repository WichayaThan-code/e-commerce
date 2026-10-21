-- V30: shipping carriers, per-shop carrier enablement, shipment tracking — additive, idempotent.
-- V1 already has shipments (status CHECK = 10 values), shipment_tracking_events, shipping_methods (carrier_code free text
-- "KERRY|FLASH|THAIPOST|JT|OTHER"). V1 has no carrier master table and no per-shop carrier table; this adds both.

-- 1. Carrier master list (platform-managed). tracking_pattern = regex the normalized tracking number must match
--    (NULL = generic rule in the app: 4-40 chars A-Z 0-9 '-'). Only well-known formats are strict.
CREATE TABLE IF NOT EXISTS carriers (
    code character varying(50) NOT NULL,
    name_th character varying(100) NOT NULL,
    name_en character varying(100) NOT NULL,
    tracking_pattern character varying(200),
    tracking_url_template character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_carriers PRIMARY KEY (code),
    CONSTRAINT ck_carriers_code CHECK (code ~ '^[A-Z0-9_]{2,50}$')
);
COMMENT ON TABLE carriers IS '[Shipping] V30: master list ขนส่ง (KERRY, FLASH, THAIPOST, JT, …); shipments.carrier_code / shipping_methods.carrier_code อ้างอิงรหัสนี้ (ตรวจในแอป)';
COMMENT ON COLUMN carriers.tracking_pattern IS 'regex ของเลขพัสดุหลัง normalize (ตัวใหญ่ ไม่มีช่องว่าง); NULL = กฎทั่วไปในแอป';
COMMENT ON COLUMN carriers.tracking_url_template IS 'ลิงก์ติดตามพัสดุ; {tracking} ถูกแทนด้วยเลขพัสดุ';

INSERT INTO carriers (code, name_th, name_en, tracking_pattern, tracking_url_template, is_active, sort_order) VALUES
    ('KERRY', 'เคอรี่ เอ็กซ์เพรส (KEX)', 'Kerry Express (KEX)', NULL, 'https://th.kex-express.com/th/track/?track={tracking}', true, 10),
    ('FLASH', 'แฟลช เอ็กซ์เพรส', 'Flash Express', NULL, 'https://www.flashexpress.co.th/fle/tracking?se={tracking}', true, 20),
    ('THAIPOST', 'ไปรษณีย์ไทย', 'Thailand Post', '^[A-Z]{2}[0-9]{9}TH$', 'https://track.thailandpost.co.th/?trackNumber={tracking}', true, 30),
    ('JT', 'เจแอนด์ที เอ็กซ์เพรส', 'J&T Express', NULL, 'https://www.jtexpress.co.th/service/track?billcode={tracking}', true, 40),
    ('NINJAVAN', 'นินจาแวน', 'Ninja Van', NULL, 'https://www.ninjavan.co/th-th/tracking?id={tracking}', true, 50),
    ('BEST', 'เบสท์ เอ็กซ์เพรส', 'BEST Express', NULL, NULL, true, 60),
    ('SPX', 'ช้อปปี้ เอ็กซ์เพรส (SPX)', 'SPX Express', NULL, NULL, true, 70),
    ('DHL', 'ดีเอชแอล อีคอมเมิร์ซ', 'DHL eCommerce', NULL, NULL, true, 80),
    ('SCG', 'เอสซีจี เอ็กซ์เพรส', 'SCG Express', NULL, NULL, true, 90),
    ('OTHER', 'ขนส่งอื่น ๆ', 'Other carrier', NULL, NULL, true, 999)
ON CONFLICT (code) DO NOTHING;

DROP TRIGGER IF EXISTS trg_carriers_modified_date_time ON carriers;
CREATE TRIGGER trg_carriers_modified_date_time BEFORE UPDATE ON carriers FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

-- 2. Carriers a shop uses. A shop with no row at all may use every active carrier (backward compatible); once it has
--    rows, only active rows count.
CREATE TABLE IF NOT EXISTS shop_carriers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_id uuid NOT NULL,
    carrier_code character varying(50) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_date_time timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    modified_date_time timestamp with time zone DEFAULT now() NOT NULL,
    modified_by uuid,
    CONSTRAINT pk_shop_carriers PRIMARY KEY (id),
    CONSTRAINT uq_shop_carriers_shop_id_carrier_code UNIQUE (shop_id, carrier_code),
    CONSTRAINT fk_shop_carriers_shop_id_shops FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_carriers_carrier_code_carriers FOREIGN KEY (carrier_code) REFERENCES carriers(code)
);
COMMENT ON TABLE shop_carriers IS '[Shipping] V30: ขนส่งที่ร้านเปิดใช้; ร้านที่ยังไม่มีแถวเลย = ใช้ได้ทุกขนส่งที่ active; มีแถวแล้ว = เฉพาะแถว is_active';
CREATE INDEX IF NOT EXISTS ix_shop_carriers_carrier_code ON shop_carriers (carrier_code);
DROP TRIGGER IF EXISTS trg_shop_carriers_modified_date_time ON shop_carriers;
CREATE TRIGGER trg_shop_carriers_modified_date_time BEFORE UPDATE ON shop_carriers FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();

-- 3. shipments: optimistic lock + failed / returned facts.
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS row_version integer DEFAULT 0 NOT NULL;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS failed_at timestamp with time zone;
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS failure_reason character varying(500);
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS returned_at timestamp with time zone;
COMMENT ON COLUMN shipments.row_version IS 'optimistic lock (JPA @Version) — V30';
COMMENT ON COLUMN shipments.failure_reason IS 'เหตุผลส่งไม่สำเร็จ/ตีกลับล่าสุด — V30';
CREATE INDEX IF NOT EXISTS ix_shipments_shop_id_status ON shipments (shop_id, status);

-- Tracking number unique per carrier (case-insensitive) among live shipments. Only created when existing data has no
-- duplicates; the application enforces the same rule either way.
DO $$
BEGIN
    IF to_regclass('public.uq_shipments_carrier_code_tracking_number') IS NULL THEN
        IF EXISTS (SELECT 1 FROM shipments WHERE tracking_number IS NOT NULL AND status <> 'CANCELLED'
                   GROUP BY carrier_code, upper(tracking_number) HAVING count(*) > 1) THEN
            RAISE NOTICE 'V30: duplicate (carrier_code, tracking_number) rows exist; uq_shipments_carrier_code_tracking_number not created';
        ELSE
            CREATE UNIQUE INDEX uq_shipments_carrier_code_tracking_number ON shipments (carrier_code, upper(tracking_number))
                WHERE tracking_number IS NOT NULL AND status <> 'CANCELLED';
        END IF;
    END IF;
END $$;

-- 4. shipment_tracking_events: who did it (actor), what kind of entry, previous status; admin as a source.
ALTER TABLE shipment_tracking_events ADD COLUMN IF NOT EXISTS event_type character varying(40);
ALTER TABLE shipment_tracking_events ADD COLUMN IF NOT EXISTS from_status character varying(40);
ALTER TABLE shipment_tracking_events ADD COLUMN IF NOT EXISTS actor_type character varying(20);
ALTER TABLE shipment_tracking_events ADD COLUMN IF NOT EXISTS actor_user_id uuid;
COMMENT ON COLUMN shipment_tracking_events.event_type IS 'STATUS_CHANGED | TRACKING_UPDATED | NOTE | CARRIER_UPDATE — V30';
COMMENT ON COLUMN shipment_tracking_events.actor_type IS 'BUYER | MERCHANT | ADMIN | SYSTEM (ใคร) — V30; source = ช่องทาง';
ALTER TABLE shipment_tracking_events DROP CONSTRAINT IF EXISTS ck_shipment_tracking_events_source;
ALTER TABLE shipment_tracking_events ADD CONSTRAINT ck_shipment_tracking_events_source
    CHECK (source IN ('CARRIER_WEBHOOK', 'CARRIER_POLL', 'MERCHANT', 'SYSTEM', 'OMS', 'ADMIN', 'BUYER'));
ALTER TABLE shipment_tracking_events DROP CONSTRAINT IF EXISTS ck_shipment_tracking_events_actor_type;
ALTER TABLE shipment_tracking_events ADD CONSTRAINT ck_shipment_tracking_events_actor_type
    CHECK (actor_type IS NULL OR actor_type IN ('BUYER', 'MERCHANT', 'ADMIN', 'SYSTEM'));
ALTER TABLE shipment_tracking_events DROP CONSTRAINT IF EXISTS ck_shipment_tracking_events_event_type;
ALTER TABLE shipment_tracking_events ADD CONSTRAINT ck_shipment_tracking_events_event_type
    CHECK (event_type IS NULL OR event_type IN ('STATUS_CHANGED', 'TRACKING_UPDATED', 'NOTE', 'CARRIER_UPDATE'));
ALTER TABLE shipment_tracking_events DROP CONSTRAINT IF EXISTS ck_shipment_tracking_events_from_status;
ALTER TABLE shipment_tracking_events ADD CONSTRAINT ck_shipment_tracking_events_from_status
    CHECK (from_status IS NULL OR from_status IN ('READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED', 'RETURNED'));
