-- Keep only Kerry, Flash, Thailand Post, J&T, and SPX as connectable carriers for merchants.
UPDATE carriers
SET is_active = false,
    modified_date_time = CURRENT_TIMESTAMP
WHERE code IN ('NINJAVAN', 'BEST', 'DHL', 'SCG', 'OTHER');

UPDATE carriers
SET is_active = true,
    modified_date_time = CURRENT_TIMESTAMP
WHERE code IN ('KERRY', 'FLASH', 'THAIPOST', 'JT', 'SPX');
