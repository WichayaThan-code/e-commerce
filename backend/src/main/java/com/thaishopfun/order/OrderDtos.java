package com.thaishopfun.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response shapes of checkout and order APIs. Money: THB, 2 dp. shopId = public shop number. */
public final class OrderDtos {
    private OrderDtos() {}

    // ---- checkout ----
    public record AddressRequest(String recipientName, String phone, String addressLine1, String addressLine2,
                                 String subdistrict, String district, String province, String postalCode) {}

    /** paymentMethod: BANK_TRANSFER (default, pay first + slip) | COD (cash on delivery; the shop must have COD enabled). */
    public record CheckoutRequest(AddressRequest shippingAddress, String buyerNote, String paymentMethod) {
        public CheckoutRequest(AddressRequest shippingAddress, String buyerNote) {
            this(shippingAddress, buyerNote, null);
        }
    }

    public record CheckoutResponse(boolean replayed, List<OrderDetail> orders, BigDecimal grandTotal, String currency) {}

    // ---- transitions ----
    /**
     * One body for every transition endpoint; each endpoint reads what it needs.
     * reason: cancel / refund request / refund reject / any admin action. carrierCode + trackingNumber: ship.
     * status: process (READY_TO_PACK | PACKING | PACKED) and admin transition target.
     * provider / amount / paymentReference: mark-paid. restock: approve refund after dispatch (default true).
     * expectedVersion: optional optimistic check against orders.row_version (409 ORDER_VERSION_CONFLICT).
     */
    /* amount: mark-paid (must equal the order total) or refund request (partial allowed, default = all refundable). */
    public record TransitionRequest(String reason, String carrierCode, String trackingNumber, String status,
                                    String provider, BigDecimal amount, String paymentReference, Boolean restock,
                                    Integer expectedVersion, String shippedAt) {
        /** Pre-shipping-API shape (no shippedAt). shippedAt: ship only — ISO-8601 or local Bangkok time; default now. */
        public TransitionRequest(String reason, String carrierCode, String trackingNumber, String status, String provider, BigDecimal amount,
                                 String paymentReference, Boolean restock, Integer expectedVersion) {
            this(reason, carrierCode, trackingNumber, status, provider, amount, paymentReference, restock, expectedVersion, null);
        }

        public static TransitionRequest empty() { return new TransitionRequest(null, null, null, null, null, null, null, null, null, null); }
    }

    public record TransitionResponse(boolean changed, String fromStatus, String toStatus, OrderDetail order) {}

    public record AllowedTransitions(String status, String paymentStatus, String fulfillmentStatus, int version,
                                     List<AllowedTransition> transitions) {}

    /** toStatus null = action without a status change (ACCEPT; COLLECT_COD → POST …/cod/collect). */
    public record AllowedTransition(String toStatus, String action, boolean reasonRequired, boolean trackingRequired) {}

    // ---- responses ----
    /**
     * awaitingAcceptance = true (merchant view, order not accepted yet): only id, orderNumber, shop, placedAt and version
     * are filled; status / money / counts / paidAt are null (MerchantVisibility).
     */
    public record OrderSummary(UUID id, String orderNumber, Long shopId, String shopName, String status, String paymentStatus,
                               String fulfillmentStatus, BigDecimal grandTotal, String currency, Integer itemCount, Integer totalQuantity,
                               Instant placedAt, Instant paidAt, int version, boolean awaitingAcceptance,
                               /* BANK_TRANSFER | COD (null while awaitingAcceptance) */
                               String paymentMethod) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderDetail(
        UUID id, String orderNumber, Long shopId, String shopName, String channel,
        String status, String paymentStatus, String fulfillmentStatus, String merchantConfirmStatus,
        String currency, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee, BigDecimal taxTotal, BigDecimal grandTotal,
        String couponCode, ShippingSnapshot shipping, ShipmentView shipment, AddressView shippingAddress,
        String buyerNote, UUID buyerUserId, UUID shopCustomerId, String cancelReason, String statusBeforeRefund,
        Instant placedAt, Instant paidAt, Instant merchantConfirmedAt, Instant cancelledAt, Instant completedAt,
        int version, List<Item> items, List<String> allowedTransitions,
        /* true = merchant view of an order the shop has not accepted: items (and everything else) withheld */
        boolean itemsHidden, Integer itemCount, Integer totalQuantity,
        /* money summary: captured payment, refunded with proof, still refundable */
        BigDecimal paidAmount, BigDecimal refundedAmount, BigDecimal refundableAmount,
        /* merchant views only: true = not accepted yet (only id / orderNumber / placedAt / version shown; POST …/accept) */
        Boolean awaitingAcceptance,
        /* BANK_TRANSFER | COD; codFee = COD surcharge already included in grandTotal (withheld while awaitingAcceptance) */
        String paymentMethod, BigDecimal codFee
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(UUID id, UUID productId, UUID skuId, String productName, String optionLabel, String skuCode, String barcode,
                       String imageUrl, BigDecimal unitPrice, BigDecimal compareAtPrice, BigDecimal costPrice, int quantity,
                       BigDecimal discountAmount, BigDecimal lineTotal) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShippingSnapshot(UUID methodId, String methodName, String carrierCode) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShipmentView(String status, String carrierCode, String trackingNumber, Instant packedAt, Instant dispatchedAt, Instant deliveredAt) {}

    /** masked = true: only district / province / postal code (merchant before confirming or without SHIPMENT_ADDRESS_READ). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AddressView(String recipientName, String phone, String addressLine1, String addressLine2, String subdistrict,
                              String district, String province, String postalCode, String countryCode, boolean masked) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(Long id, String event, String fromStatus, String toStatus, String actorType, UUID actorUserId,
                                String reason, Map<String, Object> metadata, Instant at) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
}
