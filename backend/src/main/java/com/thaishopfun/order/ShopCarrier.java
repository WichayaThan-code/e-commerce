package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** V30 {@code shop_carriers}: carriers a shop has enabled (no row at all = every active carrier allowed). */
@Entity
@Table(name = "shop_carriers", uniqueConstraints = @UniqueConstraint(name = "uq_shop_carriers_shop_id_carrier_code", columnNames = {"shop_id", "carrier_code"}))
public class ShopCarrier extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "carrier_code", nullable = false, length = 50) private String carrierCode;
    @Column(name = "is_active", nullable = false) private boolean active = true;

    protected ShopCarrier() {}

    public ShopCarrier(UUID shopId, String carrierCode) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.carrierCode = carrierCode;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getCarrierCode() { return carrierCode; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
