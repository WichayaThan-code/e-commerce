package com.thaishopfun.catalog;

import com.thaishopfun.merchant.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "inventory_ledger", uniqueConstraints = @UniqueConstraint(name = "uq_inventory_ledger_idempotency", columnNames = "idempotency_key"))
public class InventoryLedgerEntry extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Shop shop;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductSku sku;
    @Column(name = "entry_type", nullable = false, length = 40)
    private String entryType;
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;
    @Column(name = "on_hand_before", nullable = false)
    private int onHandBefore;
    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;
    @Column(length = 500)
    private String reason;
    @Column(name = "reference_type", length = 40)
    private String referenceType;
    @Column(name = "reference_id", length = 64)
    private String referenceId;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "actor_id")
    private UUID actorId;

    protected InventoryLedgerEntry() {}

    public InventoryLedgerEntry(ProductSku sku, String entryType, int quantityDelta, int onHandBefore, int onHandAfter, String reason, String idempotencyKey, UUID actorId) {
        this.shop = sku.getShop();
        this.sku = sku;
        this.entryType = entryType;
        this.quantityDelta = quantityDelta;
        this.onHandBefore = onHandBefore;
        this.onHandAfter = onHandAfter;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.actorId = actorId;
    }

    public Long getId() { return id; }
    public ProductSku getSku() { return sku; }
    public String getReason() { return reason; }
    public String getEntryType() { return entryType; }
    public int getQuantityDelta() { return quantityDelta; }
    public int getOnHandBefore() { return onHandBefore; }
    public int getOnHandAfter() { return onHandAfter; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
