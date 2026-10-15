package com.thaishopfun.customer;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Customer of one merchant shop (CRM/OMS record), V1 table shop_customers + V22 columns. Not a platform account. */
@Entity
@Table(name = "shop_customers")
public class ShopCustomer extends AuditedEntity {
    @Id private UUID id;
    /** shops.id (uuid). Plain column (no association) so shop tests that wipe shops are unaffected. */
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "customer_code", length = 40) private String customerCode;
    @Column(name = "display_name", nullable = false, length = 150) private String displayName;
    @Column(length = 30) private String phone;
    @Column(name = "phone_normalized", length = 20) private String phoneNormalized;
    @Column(length = 255) private String email;
    @Column(name = "email_normalized", length = 255) private String emailNormalized;
    @Column(name = "internal_note", columnDefinition = "text") private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CustomerSource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CustomerStatus status;
    @Column(name = "merged_into_id") private UUID mergedIntoId;
    @Column(name = "merged_at") private Instant mergedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shop_customer_tags", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "tag", nullable = false, length = 50)
    @OrderBy
    private Set<String> tags = new LinkedHashSet<>();

    protected ShopCustomer() {}

    public ShopCustomer(UUID shopId) {
        this.id = UUID.randomUUID();
        this.shopId = shopId;
        this.source = CustomerSource.MANUAL;
        this.status = CustomerStatus.ACTIVE;
    }

    public void markMerged(UUID primaryId, Instant at) {
        this.status = CustomerStatus.MERGED;
        this.mergedIntoId = primaryId;
        this.mergedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getShopId() { return shopId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPhone() { return phone; }
    public String getPhoneNormalized() { return phoneNormalized; }
    public void setPhone(String phone, String normalized) { this.phone = phone; this.phoneNormalized = normalized; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    public void setEmail(String email, String normalized) { this.email = email; this.emailNormalized = normalized; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public CustomerSource getSource() { return source; }
    public void setSource(CustomerSource source) { this.source = source; }
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public UUID getMergedIntoId() { return mergedIntoId; }
    public Instant getMergedAt() { return mergedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Set<String> getTags() { return tags; }
}
