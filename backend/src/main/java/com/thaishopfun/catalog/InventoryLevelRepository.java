package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    Optional<InventoryLevel> findBySku_Id(UUID skuId);
    List<InventoryLevel> findBySku_IdIn(Collection<UUID> skuIds);
}
