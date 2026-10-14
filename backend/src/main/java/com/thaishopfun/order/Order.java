package com.thaishopfun.order;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uq_orders_idempotency_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "uq_orders_shop_id_order_number", columnNames = {"shop_id", "order_number"})})
/**
 * V1 {@code orders} aggregate. Own audit columns (not AuditedEntity) so the H2 test schema carries the same column
 * defaults as Postgres (@ColumnDefault mirrors V1 DEFAULTs; raw-SQL test fixtures rely on them).
 */
public class Order {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "order_number", nullable = false, length = 40) private String orderNumber;
    @Column(name = "buyer_user_id") private UUID buyerUserId;
    @Column(name = "shop_customer_id") private UUID shopCustomerId;
    @ColumnDefault("'STOREFRONT'") @Column(nullable = false, length = 40) private String channel = "STOREFRONT";
    @ColumnDefault("'PENDING_PAYMENT'") @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private OrderStatus status = OrderStatus.PENDING_PAYMENT;
    @ColumnDefault("'UNPAID'") @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30) private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    @ColumnDefault("'UNFULFILLED'") @Enumerated(EnumType.STRING) @Column(name = "fulfillment_status", nullable = false, length = 30) private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.UNFULFILLED;
    @ColumnDefault("'THB'") @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency = "THB";
    @ColumnDefault("0") @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "discount_total", nullable = false, precision = 18, scale = 2) private BigDecimal discountTotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "tax_total", nullable = false, precision = 18, scale = 2) private BigDecimal taxTotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "grand_total", nullable = false, precision = 18, scale = 2) private BigDecimal grandTotal = BigDecimal.ZERO;
    @Column(name = "coupon_code", length = 64) private String couponCode;
    @Column(name = "buyer_note", columnDefinition = "text") private String buyerNote;
    @Column(name = "internal_note", columnDefinition = "text") private String internalNote;
    @Column(name = "idempotency_key", length = 120) private String idempotencyKey;
    @Column(name = "placed_at") private Instant placedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @ColumnDefault("'NOT_REQUIRED'") @Column(name = "merchant_confirm_status", nullable = false, length = 30) private String merchantConfirmStatus = "AWAITING";
    @Column(name = "merchant_confirmed_at") private Instant merchantConfirmedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completion_source", length = 20) private String completionSource;
    @ColumnDefault("'NOT_SYNCED'") @Column(name = "oms_sync_status", nullable = false, length = 30) private String omsSyncStatus = "NOT_SYNCED";
    @Column(name = "shipping_method_id") private UUID shippingMethodId;
    @Column(name = "shipping_method_name_snapshot", length = 150) private String shippingMethodNameSnapshot;
    @Column(name = "shipping_carrier_code_snapshot", length = 50) private String shippingCarrierCodeSnapshot;
    @Column(name = "status_before_refund", length = 40) private String statusBeforeRefund;
    @ColumnDefault("false") @Column(name = "stock_committed", nullable = false) private boolean stockCommitted;
    @ColumnDefault("0") @Version @Column(name = "row_version", nullable = false) private int rowVersion;

    @ColumnDefault("CURRENT_TIMESTAMP") @Column(name = "created_date_time", nullable = false, updatable = false) private Instant createdDateTime;
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @ColumnDefault("CURRENT_TIMESTAMP") @Column(name = "modified_date_time", nullable = false) private Instant modifiedDateTime;
    @Column(name = "modified_by") private UUID modifiedBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDateTime ASC, skuCodeSnapshot ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdDateTime ASC")
    private List<OrderAddress> addresses = new ArrayList<>();

    protected Order() {}

    public Order(UUID shopId, String orderNumber, UUID buyerUserId) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.orderNumber = orderNumber;
        this.buyerUserId = buyerUserId;
        this.placedAt = Instant.now();
        this.merchantConfirmStatus = "AWAITING";
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdDateTime == null) createdDateTime = now;
        modifiedDateTime = now;
    }

    @PreUpdate
    void onUpdate() {
        modifiedDateTime = Instant.now();
    }

    public void stamp(UUID userId) {
        if (createdBy == null) createdBy = userId;
        modifiedBy = userId;
    }

    public Instant getCreatedDateTime() { return createdDateTime; }
    public Instant getModifiedDateTime() { return modifiedDateTime; }
    public UUID getModifiedBy() { return modifiedBy; }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public UUID getShopCustomerId() { return shopCustomerId; }
    public void setShopCustomerId(UUID shopCustomerId) { this.shopCustomerId = shopCustomerId; }
    public String getChannel() { return channel; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(FulfillmentStatus fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxTotal = taxTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getBuyerNote() { return buyerNote; }
    public void setBuyerNote(String buyerNote) { this.buyerNote = buyerNote; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getMerchantConfirmStatus() { return merchantConfirmStatus; }
    public void setMerchantConfirmStatus(String merchantConfirmStatus) { this.merchantConfirmStatus = merchantConfirmStatus; }
    public Instant getMerchantConfirmedAt() { return merchantConfirmedAt; }
    public void setMerchantConfirmedAt(Instant merchantConfirmedAt) { this.merchantConfirmedAt = merchantConfirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getCompletionSource() { return completionSource; }
    public void setCompletionSource(String completionSource) { this.completionSource = completionSource; }
    public UUID getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(UUID shippingMethodId) { this.shippingMethodId = shippingMethodId; }
    public String getShippingMethodNameSnapshot() { return shippingMethodNameSnapshot; }
    public void setShippingMethodNameSnapshot(String v) { this.shippingMethodNameSnapshot = v; }
    public String getShippingCarrierCodeSnapshot() { return shippingCarrierCodeSnapshot; }
    public void setShippingCarrierCodeSnapshot(String v) { this.shippingCarrierCodeSnapshot = v; }
    public String getStatusBeforeRefund() { return statusBeforeRefund; }
    public void setStatusBeforeRefund(String statusBeforeRefund) { this.statusBeforeRefund = statusBeforeRefund; }
    public boolean isStockCommitted() { return stockCommitted; }
    public void setStockCommitted(boolean stockCommitted) { this.stockCommitted = stockCommitted; }
    public int getRowVersion() { return rowVersion; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderAddress> getAddresses() { return addresses; }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addAddress(OrderAddress address) {
        addresses.add(address);
        address.setOrder(this);
    }
}
