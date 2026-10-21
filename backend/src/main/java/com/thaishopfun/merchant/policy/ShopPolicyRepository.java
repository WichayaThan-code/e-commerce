package com.thaishopfun.merchant.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopPolicyRepository extends JpaRepository<ShopPolicy, UUID> {
    @Query("select p from ShopPolicy p where p.shop.id = :shopUuid")
    List<ShopPolicy> findAllByShopUuid(@Param("shopUuid") UUID shopUuid);

    @Query("select p from ShopPolicy p where p.shop.id = :shopUuid and p.type = :type")
    Optional<ShopPolicy> findByShopUuidAndType(@Param("shopUuid") UUID shopUuid, @Param("type") ShopPolicyType type);
}
