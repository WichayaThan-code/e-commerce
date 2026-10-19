package com.thaishopfun.customer;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of V1 {@code orders} for customer order history and merge re-pointing. Only the columns needed here are
 * mapped. Orders are created / changed through {@link com.thaishopfun.order.Order} (checkout links shop_customer_id
 * to the buyer's CRM record); this view only writes shop_customer_id when customers are merged.
 */
@Entity(name = "CustomerOrderRecord")
@Table(name = "orders")
public class CustomerOrderRecord {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "order_number", nullable = false, length = 40) private String orderNumber;
    @Column(name = "shop_customer_id") private UUID shopCustomerId;
    @Column(nullable = false, length = 40) private String channel;
    @Column(nullable = false, length = 40) private String status;
    @Column(name = "payment_status", nullable = false, length = 30) private String paymentStatus;
    @Column(name = "fulfillment_status", nullable = false, length = 30) private String fulfillmentStatus;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "grand_total", nullable = false, precision = 18, scale = 2) private BigDecimal grandTotal;
    @Column(name = "placed_at") private Instant placedAt;
    @Column(name = "created_date_time", nullable = false) private Instant createdDateTime;

    protected CustomerOrderRecord() {}

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getShopCustomerId() { return shopCustomerId; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getFulfillmentStatus() { return fulfillmentStatus; }
    public String getCurrency() { return currency; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getCreatedDateTime() { return createdDateTime; }
}
