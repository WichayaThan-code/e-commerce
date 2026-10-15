package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductSkuOptionValueRepository extends JpaRepository<ProductSkuOptionValue, UUID> {
    List<ProductSkuOptionValue> findBySku_IdIn(Collection<UUID> skuIds);
    List<ProductSkuOptionValue> findBySku_Id(UUID skuId);
    List<ProductSkuOptionValue> findByValue_IdIn(Collection<UUID> valueIds);
    List<ProductSkuOptionValue> findByGroup_IdIn(Collection<UUID> groupIds);
}
