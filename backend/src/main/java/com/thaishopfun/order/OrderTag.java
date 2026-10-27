package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "order_tags")
public class OrderTag extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(nullable = false, length = 40) private String name;
    @Column(length = 7) private String color;

    protected OrderTag() {}

    public OrderTag(UUID shopId, String name, String color) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.name = name;
        this.color = color;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
