-- PLATFORM_SYSTEM_READ: read-only platform system status (GET /api/admin/system/status).
INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PLATFORM_SYSTEM_READ', 'PLATFORM', 'PLATFORM',
     'ดูสถานะระบบแพลตฟอร์ม (ฐานข้อมูล, migration, อีเมล, พื้นที่จัดเก็บ) โดยไม่รวมข้อมูลลับ',
     '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'PLATFORM_SYSTEM_READ'
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
