package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, UUID> {
    List<ShippingMethod> findByShopIdOrderByCreatedDateTimeAsc(UUID shopId);
    List<ShippingMethod> findByShopIdInAndActiveTrue(Collection<UUID> shopIds);
    Optional<ShippingMethod> findByIdAndShopId(UUID id, UUID shopId);
}
