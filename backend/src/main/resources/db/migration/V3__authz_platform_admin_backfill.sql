-- =============================================================================
-- ThaiShopFun Ecommerce — Flyway V3__authz_platform_admin_backfill.sql
--
-- Permission Matrix enforcement (feature/permission-matrix): platform permissions are now granted from
-- platform_roles (+ auth_role_permissions), no longer from the deprecated users.role column.
-- Step 1 announced in the V1 comment on users.role: copy every legacy ADMIN account into platform_roles
-- as PLATFORM_ADMIN so existing administrators keep their access.
--
-- Idempotent and data-preserving: inserts only users that have no active platform role yet
-- (respects the partial unique index uq_platform_roles_user_id_active); never updates or deletes rows.
-- users.role itself is left untouched (still used for display / frontend routing).
-- No BEGIN/COMMIT: Flyway wraps the migration in one transaction.
-- =============================================================================

INSERT INTO platform_roles (user_id, role, grant_reason)
SELECT u.id, 'PLATFORM_ADMIN', 'V3 backfill from legacy users.role = ADMIN'
  FROM users u
 WHERE u.role = 'ADMIN'
   AND u.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM platform_roles pr WHERE pr.user_id = u.id AND pr.revoked_at IS NULL);
