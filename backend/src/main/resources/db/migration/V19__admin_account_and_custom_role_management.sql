ALTER TABLE platform_roles
    DROP CONSTRAINT IF EXISTS ck_platform_roles_role;

INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PLATFORM_ACCOUNT_CREATE', 'PLATFORM', 'PLATFORM', 'สร้างบัญชีผู้ใช้และส่งลิงก์ตั้งรหัสผ่าน', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ACCOUNT_UPDATE', 'PLATFORM', 'PLATFORM', 'แก้ไขชื่อและเบอร์โทรของบัญชีผู้ใช้', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ACCOUNT_DELETE', 'PLATFORM', 'PLATFORM', 'ลบบัญชีผู้ใช้แบบเก็บประวัติและเพิกถอน Session', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ACCOUNT_ROLE_ASSIGN', 'PLATFORM', 'PLATFORM', 'กำหนดหรือเพิกถอนบทบาทแพลตฟอร์ม', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ROLE_CREATE', 'PLATFORM', 'PLATFORM', 'สร้างบทบาทแพลตฟอร์มแบบกำหนดเอง', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ROLE_UPDATE', 'PLATFORM', 'PLATFORM', 'แก้ไขรายละเอียดและสิทธิ์ของบทบาทกำหนดเอง', '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_ROLE_DELETE', 'PLATFORM', 'PLATFORM', 'ปิดใช้งานบทบาทกำหนดเอง', '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code IN (
      'PLATFORM_ACCOUNT_CREATE', 'PLATFORM_ACCOUNT_UPDATE', 'PLATFORM_ACCOUNT_DELETE',
      'PLATFORM_ACCOUNT_ROLE_ASSIGN', 'PLATFORM_ROLE_CREATE', 'PLATFORM_ROLE_UPDATE', 'PLATFORM_ROLE_DELETE')
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
