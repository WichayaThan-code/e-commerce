package com.thaishopfun.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    List<Cart> findByBuyerUserIdAndStatusOrderByCreatedDateTimeAsc(UUID buyerUserId, String status);
    List<Cart> findByGuestTokenHashAndStatusOrderByCreatedDateTimeAsc(String guestTokenHash, String status);
    Optional<Cart> findByBuyerUserIdAndShopIdAndStatus(UUID buyerUserId, UUID shopId, String status);
    Optional<Cart> findByGuestTokenHashAndShopIdAndStatus(String guestTokenHash, UUID shopId, String status);
}
