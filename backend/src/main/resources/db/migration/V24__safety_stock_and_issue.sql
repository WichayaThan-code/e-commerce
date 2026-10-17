ALTER TABLE inventory_levels ADD COLUMN safety_stock INT NOT NULL DEFAULT 0;
ALTER TABLE inventory_levels ADD CONSTRAINT ck_inventory_levels_safety_stock CHECK (safety_stock >= 0);

UPDATE inventory_levels
SET available = GREATEST(0, on_hand - reserved_qty - safety_stock);

ALTER TABLE inventory_ledger DROP CONSTRAINT ck_inventory_ledger_entry_type;
ALTER TABLE inventory_ledger ADD CONSTRAINT ck_inventory_ledger_entry_type CHECK (
    entry_type IN ('RECEIVE', 'ADJUST', 'RESERVE', 'RELEASE', 'SALE_CONFIRM', 'COMMIT_SALE', 'RETURN', 'CANCEL', 'RESTOCK_REFUND', 'ISSUE')
);
