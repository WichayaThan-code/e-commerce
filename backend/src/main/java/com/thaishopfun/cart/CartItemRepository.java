package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartIdInOrderByCreatedDateTimeAsc(Collection<UUID> cartIds);
    List<CartItem> findByCartId(UUID cartId);
    Optional<CartItem> findByCartIdAndSkuId(UUID cartId, UUID skuId);
    long countByCartIdIn(Collection<UUID> cartIds);
}
