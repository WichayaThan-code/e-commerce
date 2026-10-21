package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopCarrierRepository extends JpaRepository<ShopCarrier, UUID> {
    List<ShopCarrier> findByShopId(UUID shopId);

    Optional<ShopCarrier> findByShopIdAndCarrierCode(UUID shopId, String carrierCode);

    boolean existsByShopIdAndActiveTrue(UUID shopId);
}
