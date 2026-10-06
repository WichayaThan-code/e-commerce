package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopReinstatementRequestRepository extends JpaRepository<ShopReinstatementRequest, Long> {
    Optional<ShopReinstatementRequest> findFirstByShop_IdAndStatusOrderByCreatedAtDesc(Long shopId, ReinstatementStatus status);
    Optional<ShopReinstatementRequest> findFirstByShop_IdOrderByCreatedAtDesc(Long shopId);
    List<ShopReinstatementRequest> findByShop_IdAndStatus(Long shopId, ReinstatementStatus status);
}
