package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V1 {@code order_financial_snapshots}: the money summary a seller may see before confirming the order (1:1 with
 * orders). Written at checkout, refreshed on payment / refund changes. Address hints only, never name/phone/house no.
 */
@Entity
@Table(name = "order_financial_snapshots", uniqueConstraints = @UniqueConstraint(name = "uq_order_financial_snapshots_order_id", columnNames = "order_id"))
public class OrderFinancialSnapshot extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @JdbcTypeCode(SqlTypes.CHAR) @ColumnDefault("'THB'") @Column(nullable = false, length = 3) private String currency = "THB";
    @ColumnDefault("0") @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "discount_total", nullable = false, precision = 18, scale = 2) private BigDecimal discountTotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "tax_total", nullable = false, precision = 18, scale = 2) private BigDecimal taxTotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "grand_total", nullable = false, precision = 18, scale = 2) private BigDecimal grandTotal = BigDecimal.ZERO;
    @ColumnDefault("0") @Column(name = "item_count", nullable = false) private int itemCount;
    @ColumnDefault("0") @Column(name = "total_qty", nullable = false) private int totalQty;
    @ColumnDefault("'UNPAID'") @Column(name = "payment_status", nullable = false, length = 30) private String paymentStatus = "UNPAID";
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "buyer_province_hint", length = 100) private String buyerProvinceHint;
    @Column(name = "buyer_district_hint", length = 100) private String buyerDistrictHint;
    @Column(name = "buyer_postal_hint", length = 10) private String buyerPostalHint;
    @Column(name = "snapshot_at", nullable = false) private Instant snapshotAt;

    protected OrderFinancialSnapshot() {}

    public OrderFinancialSnapshot(UUID orderId, UUID shopId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.shopId = shopId;
    }

    public void refresh(Order order) {
        this.currency = order.getCurrency();
        this.subtotal = order.getSubtotal();
        this.discountTotal = order.getDiscountTotal();
        this.shippingFee = order.getShippingFee();
        this.taxTotal = order.getTaxTotal();
        this.grandTotal = order.getGrandTotal();
        this.itemCount = order.getItems().size();
        this.totalQty = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        this.paymentStatus = order.getPaymentStatus().name();
        this.paidAt = order.getPaidAt();
        order.getAddresses().stream().filter(a -> "SHIPPING".equals(a.getAddressType())).findFirst().ifPresent(a -> {
            this.buyerProvinceHint = a.getProvince();
            this.buyerDistrictHint = a.getDistrict();
            this.buyerPostalHint = a.getPostalCode();
        });
        this.snapshotAt = Instant.now();
    }

    public UUID getOrderId() { return orderId; }
    public String getPaymentStatus() { return paymentStatus; }
    public BigDecimal getGrandTotal() { return grandTotal; }
}
