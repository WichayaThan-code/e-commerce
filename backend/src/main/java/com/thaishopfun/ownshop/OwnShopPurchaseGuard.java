package com.thaishopfun.ownshop;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.cart.Cart;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Blocks a signed-in user from buying products of a shop they own or staff (ACTIVE shop_members row).
 * Used by OwnShopAddItemAdvice (cart add / SKU switch) and OwnShopCheckoutInterceptor (checkout).
 */
@Component
public class OwnShopPurchaseGuard {
    private final JdbcTemplate jdbc;

    public OwnShopPurchaseGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when the user is an ACTIVE member of the shop that owns the SKU. */
    public boolean isMemberOfSkuShop(UUID userId, UUID skuId) {
        if (userId == null || skuId == null) return false;
        Boolean hit = jdbc.queryForObject("""
            SELECT EXISTS (
              SELECT 1 FROM product_skus s
              JOIN shop_members m ON m.shop_id = s.shop_id
              WHERE s.id = ? AND m.user_id = ? AND m.removed_at IS NULL AND m.status = 'ACTIVE'
            )
            """, Boolean.class, skuId, userId);
        return Boolean.TRUE.equals(hit);
    }

    /** True when the buyer's ACTIVE cart has a selected line for a shop they staff. */
    public boolean hasOwnShopSelectedLines(UUID userId) {
        if (userId == null) return false;
        Boolean hit = jdbc.queryForObject("""
            SELECT EXISTS (
              SELECT 1 FROM carts c
              JOIN cart_items i ON i.cart_id = c.id
              JOIN shop_members m ON m.shop_id = c.shop_id
              WHERE c.buyer_user_id = ? AND c.status = ? AND i.is_selected
                AND m.user_id = ? AND m.removed_at IS NULL AND m.status = 'ACTIVE'
            )
            """, Boolean.class, userId, Cart.ACTIVE, userId);
        return Boolean.TRUE.equals(hit);
    }

    public static AuthException forbidden() {
        return new AuthException(HttpStatus.FORBIDDEN, "OWN_SHOP_PURCHASE_FORBIDDEN", "ไม่สามารถซื้อสินค้าจากร้านของคุณเองได้");
    }

    /** Same pattern as CartAccess.currentUserId(); null → skip the guard (existing auth returns 401). */
    public static UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return RequestUser.id(jwt);
        }
        return null;
    }
}
