package com.thaishopfun.cart;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response shapes of the cart, checkout validation, coupon and shipping-method APIs. Money: THB, 2 dp. */
public final class CartDtos {
    private CartDtos() {}

    // ---- buyer requests ----
    /** skuId = the chosen variant; quantity default 1; capToAvailable: add what fits instead of 409. */
    public record AddItemRequest(UUID skuId, Integer quantity, Boolean capToAvailable) {}
    /** Any subset: quantity (absolute), selected, skuId (switch to another variant of the same product). */
    public record UpdateItemRequest(Integer quantity, Boolean selected, UUID skuId) {}
    public record ItemIdsRequest(List<UUID> itemIds) {}
    /** itemIds null or empty = every line. */
    public record SelectionRequest(List<UUID> itemIds, Boolean selected) {}
    public record ApplyCouponRequest(String code) {}
    /** shippingMethodId null = automatic (cheapest). */
    public record ChooseShippingRequest(UUID shippingMethodId) {}

    // ---- buyer responses ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CartResponse(
        String cartToken,
        String currency,
        List<ShopGroup> groups,
        Totals summary,
        Counts counts,
        Instant expiresAt,
        ActionResult lastAction,
        List<MergeNote> mergeNotes
    ) {}

    public record Counts(int lineCount, int itemQuantity, int selectedLineCount, int selectedQuantity) {}

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee, BigDecimal shippingDiscount, BigDecimal grandTotal) {}

    public record ShopGroup(
        Long shopId,
        String shopName,
        String shopSlug,
        List<Line> items,
        CouponView coupon,
        ShippingView shipping,
        Totals totals,
        int selectedQuantity
    ) {}

    public record Line(
        UUID id,
        UUID skuId,
        UUID productId,
        String productName,
        String productSlug,
        String skuCode,
        String optionLabel,
        Map<String, String> options,
        String imageUrl,
        int quantity,
        boolean selected,
        BigDecimal unitPrice,
        BigDecimal priceSnapshot,
        BigDecimal compareAtPrice,
        boolean priceChanged,
        BigDecimal lineTotal,
        int availableQuantity,
        int maxQuantity,
        /** OK | PRICE_CHANGED | INSUFFICIENT_STOCK | OUT_OF_STOCK | PRODUCT_UNAVAILABLE */
        String status
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CouponView(
        String code,
        /** PERCENT | FIXED | FREE_SHIPPING */
        String type,
        BigDecimal value,
        BigDecimal maxDiscount,
        BigDecimal minSubtotal,
        boolean applicable,
        /** null when applicable, else COUPON_NOT_FOUND / COUPON_INACTIVE / COUPON_NOT_STARTED / COUPON_EXPIRED / COUPON_USAGE_LIMIT_REACHED / COUPON_PER_CUSTOMER_LIMIT_REACHED / COUPON_MIN_SUBTOTAL_NOT_MET */
        String reason,
        String message,
        BigDecimal discountAmount,
        BigDecimal shippingDiscountAmount
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShippingView(
        UUID methodId,
        String methodName,
        String feeType,
        boolean autoSelected,
        BigDecimal fee,
        BigDecimal freeShippingThreshold,
        BigDecimal amountToFreeShipping,
        int totalWeightGrams,
        List<ShippingOption> options
    ) {}

    public record ShippingOption(UUID id, String name, String carrierCode, String feeType, BigDecimal fee, Integer estimatedDaysMin, Integer estimatedDaysMax) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActionResult(UUID itemId, UUID skuId, int requestedQuantity, int appliedQuantity, boolean adjusted, String code, String message) {}

    public record MergeNote(UUID skuId, String code, String message, int requestedQuantity, int appliedQuantity) {}

    public record CountResponse(int lineCount, int itemQuantity, int selectedLineCount, int selectedQuantity) {}

    // ---- checkout validation ----
    public record ValidationResponse(boolean valid, Instant checkedAt, List<Issue> issues, CartResponse cart) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issue(
        /** PRICE_CHANGED | OUT_OF_STOCK | INSUFFICIENT_STOCK | PRODUCT_UNAVAILABLE | COUPON_INVALID | CART_EMPTY | LOGIN_REQUIRED */
        String code,
        /** ERROR blocks checkout; WARNING / INFO do not */
        String severity,
        Long shopId,
        UUID itemId,
        UUID skuId,
        String field,
        String message,
        Map<String, Object> details
    ) {}

    // ---- merchant coupon management ----
    public record CouponRequest(
        String code,
        String name,
        /** PERCENT | FIXED | FREE_SHIPPING */
        String type,
        BigDecimal value,
        BigDecimal maxDiscount,
        BigDecimal minSubtotal,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimitTotal,
        Integer usageLimitPerCustomer,
        Boolean active,
        /** null = keep (update) / true (create). Storefront listing + wallet collect. */
        Boolean showOnStorefront
    ) {}

    public record CouponResponse(
        UUID id,
        Long shopId,
        String code,
        String name,
        String type,
        BigDecimal value,
        BigDecimal maxDiscount,
        BigDecimal minSubtotal,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimitTotal,
        Integer usageLimitPerCustomer,
        long usedCount,
        boolean active,
        /** ACTIVE | SCHEDULED | EXPIRED | DISABLED | USED_UP */
        String state,
        Instant createdAt,
        Instant updatedAt,
        boolean showOnStorefront,
        /** Sum of promotion_usages.discount_amount (orders that used it). */
        BigDecimal discountTotal,
        /** Merchant-page status, precedence EXPIRED > PAUSED > UPCOMING > ACTIVE (same as /merchant/promotions). */
        String status
    ) {}

    /** Whole-shop counters (independent of the list filters) for KPI cards / tab badges. */
    public record CouponSummary(long total, long active, long upcoming, long paused, long expired,
                                long percent, long fixedAmount, long freeShipping, long usageCount, BigDecimal discountTotal) {}

    /** Coupon list = PageResponse fields + summary. */
    public record CouponListResponse(List<CouponResponse> items, int page, int size, long totalItems, int totalPages, CouponSummary summary) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    // ---- merchant shipping methods ----
    public record ShippingMethodRequest(
        String name,
        String carrierCode,
        /** FLAT | WEIGHT | FREE */
        String feeType,
        BigDecimal feeAmount,
        BigDecimal extraFeePerKg,
        BigDecimal freeShippingMinAmount,
        Integer estimatedDaysMin,
        Integer estimatedDaysMax,
        Boolean active
    ) {}

    public record ShippingMethodResponse(
        UUID id,
        Long shopId,
        String name,
        String carrierCode,
        String feeType,
        BigDecimal feeAmount,
        BigDecimal extraFeePerKg,
        BigDecimal freeShippingMinAmount,
        Integer estimatedDaysMin,
        Integer estimatedDaysMax,
        boolean active
    ) {}
}
