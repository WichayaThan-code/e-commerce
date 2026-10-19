package com.thaishopfun.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository("merchantPromotionRepository")
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    Optional<Promotion> findByIdAndShopId(UUID id, UUID shopId);
    List<Promotion> findByShopId(UUID shopId);
}
