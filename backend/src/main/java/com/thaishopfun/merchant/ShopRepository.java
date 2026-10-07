package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {
    Optional<Shop> findByShopId(Long shopId);
    Optional<Shop> findByApplication_Id(UUID applicationId);
    Optional<Shop> findBySlug(String slug);
    List<Shop> findByUser_IdOrderByShopIdDesc(UUID userId);
    List<Shop> findByOwner_IdOrderByShopIdAsc(UUID ownerId);
    boolean existsBySlug(String slug);

    /**
     * Shops in which the user has an ACTIVE membership (shop_members), newest shop number first. The first element
     * is the "default shop" used when shopId is omitted (same rule as AuthorizationService#defaultShop).
     */
    @Query("""
        SELECT s FROM Shop s
        WHERE s.id IN (SELECT m.shopId FROM ShopMember m WHERE m.userId = :userId AND m.status = 'ACTIVE' AND m.removedAt IS NULL)
        ORDER BY s.shopId DESC
        """)
    List<Shop> findMemberShops(@Param("userId") UUID userId);
}
