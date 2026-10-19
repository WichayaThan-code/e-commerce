package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/** V1 {@code merchant_order_confirmations}: who in the shop accepted (CONFIRM) or declined (REJECT) the order, and when. */
@Entity
@Table(name = "merchant_order_confirmations")
public class MerchantOrderConfirmation extends AuditedEntity {
    public static final String CONFIRM = "CONFIRM";
    public static final String REJECT = "REJECT";

    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "confirmed_by_user_id", nullable = false) private UUID confirmedByUserId;
    @ColumnDefault("'CONFIRM'") @Column(nullable = false, length = 30) private String action;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    @Column(length = 500) private String note;

    protected MerchantOrderConfirmation() {}

    public MerchantOrderConfirmation(UUID orderId, UUID shopId, UUID userId, String action, Instant at, String note) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.shopId = shopId;
        this.confirmedByUserId = userId;
        this.action = action;
        this.confirmedAt = at;
        this.note = note;
        stamp(userId);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getConfirmedByUserId() { return confirmedByUserId; }
    public String getAction() { return action; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
