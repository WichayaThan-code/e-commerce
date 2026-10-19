-- V27: merchant promotions management (/merchant/promotions) on the V1 tables promotions / coupons / promotion_usages.
-- 1) promotions.is_public: whether an active promotion is listed on the public storefront (GET /api/shops/{slug}/promotions).
--    Existing rows default to TRUE (V1 had no visibility flag).
ALTER TABLE promotions
    ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE;

-- 2) STORE permissions (same pattern as V19; also listed in db/seed/seed_reference_data.sql, which tests parse).
INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PROMOTION_READ', 'PROMOTION', 'STORE', 'ดูโปรโมชันและคูปองของร้าน', '§5', TRUE, '2.0'),
    (gen_random_uuid(), 'PROMOTION_MANAGE', 'PROMOTION', 'STORE', 'สร้าง แก้ไข หยุดชั่วคราว และลบโปรโมชันและคูปองของร้าน', '§5', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

-- 3) Role grants: read like PRODUCT_READ minus warehouse (owner/admin/manager/sales/viewer); manage like PRODUCT_UPDATE.
INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'PROMOTION_READ'
 WHERE role.code IN ('STORE_OWNER', 'STORE_ADMIN', 'STORE_MANAGER', 'STORE_SALES', 'STORE_VIEWER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'PROMOTION_MANAGE'
 WHERE role.code IN ('STORE_OWNER', 'STORE_ADMIN', 'STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
