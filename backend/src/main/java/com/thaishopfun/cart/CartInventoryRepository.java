package com.thaishopfun.cart;

import com.thaishopfun.catalog.InventoryLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stock reads for the cart. {@link #lockForCheckout} takes row locks (SELECT ... FOR UPDATE) in a fixed order
 * (sku id) so a future reservation / order-creation step can re-check and decrement {@code available} without
 * overselling or deadlocking; inventory_levels also carries an optimistic {@code version}.
 */
public interface CartInventoryRepository extends JpaRepository<InventoryLevel, UUID> {
    @Query("select l from InventoryLevel l where l.sku.id in :skuIds")
    List<InventoryLevel> findBySkuIds(@Param("skuIds") Collection<UUID> skuIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryLevel l where l.sku.id in :skuIds order by l.sku.id")
    List<InventoryLevel> lockForCheckout(@Param("skuIds") Collection<UUID> skuIds);
}
