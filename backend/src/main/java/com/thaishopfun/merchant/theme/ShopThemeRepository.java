package com.thaishopfun.merchant.theme;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopThemeRepository extends JpaRepository<ShopTheme, UUID> {
    Optional<ShopTheme> findByShopId(UUID shopId);
}
