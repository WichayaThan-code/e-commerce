package com.thaishopfun.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface CustomerOrderRecordRepository extends JpaRepository<CustomerOrderRecord, UUID> {
    Page<CustomerOrderRecord> findByShopIdAndShopCustomerId(UUID shopId, UUID customerId, Pageable pageable);

    long countByShopIdAndShopCustomerId(UUID shopId, UUID customerId);

    @Query("""
        select coalesce(sum(o.grandTotal), 0) from CustomerOrderRecord o
        where o.shopId = :shopId and o.shopCustomerId = :customerId and o.status not in ('CANCELLED', 'REFUNDED')
        """)
    BigDecimal totalSpent(@Param("shopId") UUID shopId, @Param("customerId") UUID customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update CustomerOrderRecord o set o.shopCustomerId = :primary where o.shopId = :shopId and o.shopCustomerId = :secondary")
    int repoint(@Param("shopId") UUID shopId, @Param("secondary") UUID secondary, @Param("primary") UUID primary);
}
