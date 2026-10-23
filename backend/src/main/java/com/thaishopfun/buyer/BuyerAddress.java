package com.thaishopfun.buyer;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Signed-in buyer's saved shipping address (V1 buyer_addresses). Soft-deleted via deleted_at. */
@Entity
@Table(name = "buyer_addresses")
public class BuyerAddress extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "buyer_user_id", nullable = false) private UUID buyerUserId;
    @Column(length = 50) private String label;
    @Column(name = "recipient_name", nullable = false, length = 150) private String recipientName;
    @Column(nullable = false, length = 30) private String phone;
    @Column(name = "address_line1", nullable = false, length = 255) private String addressLine1;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 100) private String subdistrict;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, length = 100) private String province;
    @Column(name = "postal_code", nullable = false, length = 10) private String postalCode;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "is_default", nullable = false) private boolean defaultAddress;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected BuyerAddress() {}

    public BuyerAddress(UUID buyerUserId) {
        this.id = UUID.randomUUID();
        this.buyerUserId = buyerUserId;
        this.countryCode = "TH";
    }

    public void apply(String label, String recipientName, String phone, String addressLine1, String addressLine2,
                      String subdistrict, String district, String province, String postalCode) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.subdistrict = subdistrict;
        this.district = district;
        this.province = province;
        this.postalCode = postalCode;
        this.countryCode = "TH";
    }

    public void softDelete(Instant at) {
        this.deletedAt = at;
        this.defaultAddress = false;
    }

    public UUID getId() { return id; }
    public UUID getBuyerUserId() { return buyerUserId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getSubdistrict() { return subdistrict; }
    public String getDistrict() { return district; }
    public String getProvince() { return province; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public boolean isDefault() { return defaultAddress; }
    public void setDefault(boolean value) { this.defaultAddress = value; }
    public Instant getDeletedAt() { return deletedAt; }
}
