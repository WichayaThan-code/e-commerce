package com.thaishopfun.catalog.collection;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import com.thaishopfun.merchant.theme.ShopThemeRepository;
import com.thaishopfun.merchant.theme.ShopThemeTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shop resolution shared by collections (V41) and the configurator (V42). Mirrors {@code ProductService}:
 * {@code @RequireShopPermission} already ran; here the caller must be an active member of THIS shop (or super admin),
 * otherwise the same 404 as a missing shop. Public reads only see APPROVED shops.
 */
@Component
public class CatalogShopAccess {
    private final ShopRepository shops;
    private final ShopThemeRepository themes;
    private final AuthorizationService authorization;

    public CatalogShopAccess(ShopRepository shops, ShopThemeRepository themes, AuthorizationService authorization) {
        this.shops = shops;
        this.themes = themes;
        this.authorization = authorization;
    }

    public Shop merchantShop(UUID userId, Long shopId) {
        var shop = shops.findByShopId(shopId).orElseThrow(CatalogShopAccess::missingShop);
        if (userId == null) throw missingShop();
        var grants = authorization.grants(userId);
        if (!grants.superAdmin() && !grants.shopRoles().containsKey(shop.getId())) throw missingShop();
        return shop;
    }

    public Shop publicShop(String slug) {
        var shop = shops.findBySlug(slug).orElseThrow(CatalogShopAccess::missingShop);
        if (shop.getStatus() != ShopStatus.APPROVED) throw missingShop();
        return shop;
    }

    public boolean isCustom(Shop shop) {
        return themes.findByShopId(shop.getUuid())
            .map(theme -> ShopThemeTemplate.CUSTOM.name().equals(theme.getTemplateKey()))
            .orElse(false);
    }

    public static AuthException missingShop() {
        return new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
    }
}
