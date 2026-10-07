package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopReinstatementRequestRepository extends JpaRepository<ShopReinstatementRequest, UUID> {
    Optional<ShopReinstatementRequest> findFirstByShop_ShopIdAndStatusOrderByCreatedAtDesc(Long shopId, ReinstatementStatus status);
    Optional<ShopReinstatementRequest> findFirstByShop_ShopIdOrderByCreatedAtDesc(Long shopId);
    List<ShopReinstatementRequest> findByShop_ShopIdAndStatus(Long shopId, ReinstatementStatus status);
}
