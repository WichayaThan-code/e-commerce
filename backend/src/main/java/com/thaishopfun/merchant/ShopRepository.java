package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByApplication_Id(UUID applicationId);
    Optional<Shop> findBySlug(String slug);
    List<Shop> findByUser_IdOrderByShopIdDesc(UUID userId);
    List<Shop> findByOwner_IdOrderByShopIdAsc(UUID ownerId);
    boolean existsBySlug(String slug);
}
