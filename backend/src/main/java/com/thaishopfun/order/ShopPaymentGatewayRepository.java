package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopPaymentGatewayRepository extends JpaRepository<ShopPaymentGateway, UUID> {
    List<ShopPaymentGateway> findByShopIdOrderByProviderAsc(UUID shopId);
    Optional<ShopPaymentGateway> findByShopIdAndProvider(UUID shopId, String provider);
    List<ShopPaymentGateway> findByShopIdAndProviderInAndActiveTrue(UUID shopId, Collection<String> providers);
}
