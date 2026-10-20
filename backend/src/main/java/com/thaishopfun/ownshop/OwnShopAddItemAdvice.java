package com.thaishopfun.ownshop;

import com.thaishopfun.cart.CartController;
import com.thaishopfun.cart.CartDtos;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Blocks POST /api/cart/items and PATCH that switches SKU when the SKU belongs to a shop the buyer staffs.
 */
@RestControllerAdvice(assignableTypes = CartController.class)
public class OwnShopAddItemAdvice extends RequestBodyAdviceAdapter {
    private final OwnShopPurchaseGuard guard;

    public OwnShopAddItemAdvice(OwnShopPurchaseGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == CartDtos.AddItemRequest.class || targetType == CartDtos.UpdateItemRequest.class;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        UUID skuId = null;
        if (body instanceof CartDtos.AddItemRequest add) skuId = add.skuId();
        else if (body instanceof CartDtos.UpdateItemRequest update) skuId = update.skuId();
        if (skuId != null) {
            var userId = OwnShopPurchaseGuard.currentUserId();
            if (userId != null && guard.isMemberOfSkuShop(userId, skuId)) {
                throw OwnShopPurchaseGuard.forbidden();
            }
        }
        return body;
    }
}
