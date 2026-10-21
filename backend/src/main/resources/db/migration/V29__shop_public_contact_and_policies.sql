-- ช่องทางติดต่อสาธารณะของร้าน (แยกจากเบอร์/อีเมลส่วนตัวของเจ้าของใน users/merchant application)
-- ไม่ใช้ shops.contact_phone/contact_email (คอลัมน์ legacy จาก V1 ไม่มีโค้ดใช้ อาจมีข้อมูลเก่า)
ALTER TABLE shops ADD COLUMN IF NOT EXISTS public_phone VARCHAR(30);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS public_email VARCHAR(255);
COMMENT ON COLUMN shops.public_phone IS 'เบอร์ติดต่อหน้าร้าน (สาธารณะ) — ร้านกรอกเอง ว่าง = ไม่แสดง; ไม่ใช่เบอร์ส่วนตัวเจ้าของ';
COMMENT ON COLUMN shops.public_email IS 'อีเมลติดต่อหน้าร้าน (สาธารณะ) — ร้านกรอกเอง ว่าง = ไม่แสดง; ไม่ใช่อีเมลส่วนตัวเจ้าของ';

CREATE TABLE shop_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    policy_type VARCHAR(20) NOT NULL
        CHECK (policy_type IN ('SHIPPING', 'RETURNS', 'PRIVACY', 'TERMS')),
    title VARCHAR(150),
    body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 20000),
    created_date_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    modified_date_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by UUID,
    CONSTRAINT uq_shop_policies_shop_type UNIQUE (shop_id, policy_type)
);
COMMENT ON TABLE shop_policies IS '[Merchant] นโยบายร้าน (จัดส่ง/คืนสินค้า/ความเป็นส่วนตัว/เงื่อนไข) 1 แถวต่อประเภทต่อร้าน';

CREATE TRIGGER trg_shop_policies_modified_date_time
    BEFORE UPDATE ON shop_policies
    FOR EACH ROW EXECUTE FUNCTION set_modified_date_time();
