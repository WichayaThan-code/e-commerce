-- V18: drop the deprecated users.role column (authz step 2, announced in the V1 column comment and in V3).
--
-- Who is an admin / merchant now comes only from the role tables:
--   platform_roles (revoked_at IS NULL)                      -> platform admin / staff
--   shop_members   (status = 'ACTIVE' AND removed_at IS NULL) -> shop team (STORE_OWNER, ...)
--   auth_roles / auth_role_permissions                       -> role codes, labels and permissions
-- The application no longer maps the column (UserAccount), and spring.jpa.hibernate.ddl-auto=validate needs the
-- table and the entity to match, so this migration and that code change go together.
--
-- Data safety: nothing is granted here. Before dropping, the legacy values are copied into users_role_legacy_v18
-- (so they can still be inspected or restored) and every account whose users.role claims more than the role tables
-- grant is reported with RAISE NOTICE (shown in the Flyway log); fix such accounts through platform_roles /
-- shop_members if they really should have that access.
--
-- Idempotent: every step checks the current state, so re-running it (or running it on a database where the column
-- or the V1 CHECK constraint ck_users_role is already gone) is a no-op. No index, view, function or policy depends
-- on users.role (checked in pg_catalog); the column comment and NOT NULL / DEFAULT go away with the column.

DO $$
DECLARE
    r RECORD;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'role'
    ) THEN
        RAISE NOTICE 'V18: users.role is already gone, nothing to archive';
        RETURN;
    END IF;

    CREATE TABLE IF NOT EXISTS users_role_legacy_v18 (
        user_id     UUID PRIMARY KEY,
        legacy_role VARCHAR(50) NOT NULL,
        archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    COMMENT ON TABLE users_role_legacy_v18 IS
        'Archive of the dropped users.role column (Flyway V18). Read-only history, not used by the application; safe to drop once no longer needed.';

    EXECUTE 'INSERT INTO users_role_legacy_v18 (user_id, legacy_role)
             SELECT u.id, u.role FROM users u WHERE u.role IS NOT NULL
             ON CONFLICT (user_id) DO NOTHING';

    FOR r IN EXECUTE $q$
        SELECT u.id, u.email, u.role
          FROM users u
         WHERE u.deleted_at IS NULL
           AND (
                (u.role NOT IN ('CUSTOMER', 'MERCHANT')
                 AND NOT EXISTS (SELECT 1 FROM platform_roles pr WHERE pr.user_id = u.id AND pr.revoked_at IS NULL))
             OR (u.role = 'MERCHANT'
                 AND NOT EXISTS (SELECT 1 FROM shop_members sm
                                  WHERE sm.user_id = u.id AND sm.status = 'ACTIVE' AND sm.removed_at IS NULL))
           )
         ORDER BY u.email
    $q$ LOOP
        RAISE NOTICE 'V18: user % (%) had users.role=% but the role tables grant no matching access; NOT granted automatically',
            r.id, r.email, r.role;
    END LOOP;
END
$$;

ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;
ALTER TABLE users DROP COLUMN IF EXISTS role;
