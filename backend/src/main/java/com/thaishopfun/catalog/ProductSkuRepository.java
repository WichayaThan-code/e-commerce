package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {
    List<ProductSku> findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(UUID productId);
    Optional<ProductSku> findByIdAndShop_ShopIdAndDeletedAtIsNull(UUID id, Long shopId);
    boolean existsByShop_ShopIdAndSkuCode(Long shopId, String skuCode);
    boolean existsByShop_ShopIdAndSkuCodeAndIdNot(Long shopId, String skuCode, UUID id);
    boolean existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNull(Long shopId, String skuCode);
    boolean existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNullAndIdNot(Long shopId, String skuCode, UUID id);
    List<ProductSku> findByShop_ShopIdAndSkuCodeInAndDeletedAtIsNull(Long shopId, Collection<String> skuCodes);
}
