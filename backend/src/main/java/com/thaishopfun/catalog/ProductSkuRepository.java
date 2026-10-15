package com.thaishopfun.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {
    List<ProductSku> findByProduct_IdAndDeletedAtIsNullOrderBySkuCodeAsc(UUID productId);
    Optional<ProductSku> findByIdAndShop_ShopIdAndDeletedAtIsNull(UUID id, Long shopId);
    boolean existsByShop_ShopIdAndSkuCode(Long shopId, String skuCode);
    boolean existsByShop_ShopIdAndSkuCodeAndIdNot(Long shopId, String skuCode, UUID id);
    boolean existsByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNull(Long shopId, String barcode);
    List<ProductSku> findByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNull(Long shopId, String barcode);
    boolean existsByShop_ShopIdAndBarcodeIgnoreCaseAndDeletedAtIsNullAndIdNot(Long shopId, String barcode, UUID id);
    boolean existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNull(Long shopId, String skuCode);
    boolean existsByShop_ShopIdAndSkuCodeAndDeletedAtIsNullAndIdNot(Long shopId, String skuCode, UUID id);
    List<ProductSku> findByShop_ShopIdAndSkuCodeInAndDeletedAtIsNull(Long shopId, Collection<String> skuCodes);

    @Query(value = """
        select s.product_id,
               count(s.id),
               coalesce(sum(l.available), 0),
               min(s.price),
               max(s.price),
               coalesce(sum(case when l.available > 0 and l.low_stock_threshold is not null and l.available <= l.low_stock_threshold then 1 else 0 end), 0)
        from product_skus s
        left join inventory_levels l on l.sku_id = s.id
        where s.product_id in (:ids) and s.is_active = true and s.deleted_at is null
        group by s.product_id
        """, nativeQuery = true)
    List<Object[]> summarizeActiveSkus(@Param("ids") Collection<UUID> ids);
}
