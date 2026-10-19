package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthDtos;
import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/** 409 INSUFFICIENT_STOCK with the numbers the UI needs (available / already in cart / can still add). */
public class CartStockException extends AuthException {
    private final UUID skuId;
    private final int requestedQuantity;
    private final int availableQuantity;
    private final int inCartQuantity;

    public CartStockException(UUID skuId, int requestedQuantity, int availableQuantity, int inCartQuantity) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
            "คงเหลือเพียง " + availableQuantity + " ชิ้น" + (inCartQuantity > 0 ? " (ในตะกร้ามีอยู่ " + inCartQuantity + ")" : ""),
            List.of(new AuthDtos.ApiFieldError("quantity", "INSUFFICIENT_STOCK",
                "ต้องการ " + requestedQuantity + " แต่เพิ่มได้อีก " + Math.max(0, availableQuantity - inCartQuantity) + " ชิ้น")));
        this.skuId = skuId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
        this.inCartQuantity = inCartQuantity;
    }

    public UUID getSkuId() { return skuId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getInCartQuantity() { return inCartQuantity; }
}
