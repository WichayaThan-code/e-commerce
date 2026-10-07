package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_reinstatement_requests")
public class ShopReinstatementRequest {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id")
    private Shop shop;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;
    @Column(nullable = false, length = 1000)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReinstatementStatus status;
    @Column(name = "created_date_time", nullable = false)
    private Instant createdAt;
    @Column(name = "modified_date_time", nullable = false)
    private Instant updatedAt;

    protected ShopReinstatementRequest() {}

    public ShopReinstatementRequest(Shop shop, UserAccount user, String message) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.shop = shop;
        this.user = user;
        this.message = message;
        this.status = ReinstatementStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void revise(String message) {
        this.message = message;
        this.updatedAt = Instant.now();
    }

    public void approve() {
        this.status = ReinstatementStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public String getMessage() { return message; }
    public ReinstatementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
