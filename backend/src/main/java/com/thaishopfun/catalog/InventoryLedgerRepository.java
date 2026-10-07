package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, Long> {
    Optional<InventoryLedgerEntry> findByIdempotencyKey(String idempotencyKey);
    List<InventoryLedgerEntry> findBySku_Product_IdOrderByCreatedDateTimeDesc(UUID productId);
}
