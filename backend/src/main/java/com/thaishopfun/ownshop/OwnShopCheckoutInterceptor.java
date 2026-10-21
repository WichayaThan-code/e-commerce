package com.thaishopfun.ownshop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks POST /api/checkout when any selected cart line belongs to a shop the buyer staffs
 * (covers items that entered before the user joined the shop, or other non-add paths).
 */
@Component
public class OwnShopCheckoutInterceptor implements HandlerInterceptor {
    private final OwnShopPurchaseGuard guard;

    public OwnShopCheckoutInterceptor(OwnShopPurchaseGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        var userId = OwnShopPurchaseGuard.currentUserId();
        if (userId != null && guard.hasOwnShopSelectedLines(userId)) {
            throw OwnShopPurchaseGuard.forbidden();
        }
        return true;
    }
}
