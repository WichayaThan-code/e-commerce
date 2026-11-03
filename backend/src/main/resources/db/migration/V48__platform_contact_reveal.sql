-- PLATFORM_CONTACT_REVEAL: reveal full owner phone/email on store-application review (audited).
INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PLATFORM_CONTACT_REVEAL', 'PLATFORM', 'PLATFORM',
     'เปิดดูเบอร์โทรและอีเมลเต็มของเจ้าของร้านตอนตรวจคำขอเปิดร้าน พร้อมบันทึก Audit',
     '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code = 'PLATFORM_CONTACT_REVEAL'
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
