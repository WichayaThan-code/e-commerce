-- Flyway fix: align description/script for V28–V31 after rename to F1–F4
-- Run against TSF_DB while backend is STOPPED.
-- Review SELECT results before COMMIT.

BEGIN;

CREATE TABLE flyway_schema_history_bak_20261021 AS TABLE flyway_schema_history;

UPDATE flyway_schema_history
SET description = 'F1 shop themes',
    script = 'V28__F1_shop_themes.sql'
WHERE version = '28'
  AND script = 'V28__shop_themes.sql'
  AND checksum = -21770100;

UPDATE flyway_schema_history
SET description = 'F2 shop public contact and policies',
    script = 'V29__F2_shop_public_contact_and_policies.sql'
WHERE version = '29'
  AND script = 'V29__shop_public_contact_and_policies.sql'
  AND checksum = -2028064789;

UPDATE flyway_schema_history
SET description = 'F3 promotions management',
    script = 'V30__F3_promotions_management.sql'
WHERE version = '30'
  AND script = 'V30__promotions_management.sql'
  AND checksum = -1094402574;

UPDATE flyway_schema_history
SET description = 'F4 coupon collections',
    script = 'V31__F4_coupon_collections.sql'
WHERE version = '31'
  AND script = 'V31__coupon_collections.sql'
  AND checksum = -653190501;

-- Expect 4 rows; descriptions/scripts should be F1–F4 names
SELECT installed_rank, version, description, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('28', '29', '30', '31')
ORDER BY installed_rank;

-- Expect 0 rows (no duplicate versions)
SELECT version, count(*)
FROM flyway_schema_history
WHERE version IS NOT NULL
GROUP BY version
HAVING count(*) > 1;

-- If the two queries look correct:
--   COMMIT;
-- If anything is wrong:
--   ROLLBACK;
