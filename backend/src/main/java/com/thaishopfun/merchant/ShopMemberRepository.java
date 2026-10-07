package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ShopMemberRepository extends JpaRepository<ShopMember, UUID> {
    boolean existsByShopIdAndUserIdAndRole(UUID shopId, UUID userId, String role);
    boolean existsByShopIdAndUserIdAndStatusAndRemovedAtIsNull(UUID shopId, UUID userId, String status);
}
