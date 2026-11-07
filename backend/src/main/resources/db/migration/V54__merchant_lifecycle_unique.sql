-- V54: Align DB with current V53 intent that was edited after first apply.
-- Adds the lifecycle unique index that the applied V53 checksum (992313639) did not create.
-- Collapses duplicate shop-lifecycle rows that share the same business event
-- (shop_id, type, created_at) — confirmed same transition with different dedupe_key millis.

-- Keep one row per (shop_id, type, created_at): prefer canonical TYPE:shop:… key, then lowest id.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY shop_id, type, created_at
               ORDER BY
                   CASE WHEN dedupe_key LIKE (type || ':shop:%') THEN 0 ELSE 1 END,
                   dedupe_key,
                   id
           ) AS rn
      FROM merchant_notifications
     WHERE type IN ('SHOP_APPROVED', 'SHOP_REJECTED', 'SHOP_SUSPENDED', 'SHOP_RESTORED')
)
DELETE FROM merchant_notifications n
 WHERE n.id IN (SELECT id FROM ranked WHERE rn > 1);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_notifications_lifecycle_event
    ON merchant_notifications (shop_id, type, created_at)
    WHERE type IN ('SHOP_APPROVED', 'SHOP_REJECTED', 'SHOP_SUSPENDED', 'SHOP_RESTORED');
