package com.thaishopfun.customer;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One address of a shop customer (V22 shop_customer_addresses), Thai structure. Soft-deleted via deleted_at. */
@Entity
@Table(name = "shop_customer_addresses")
public class ShopCustomerAddress extends AuditedEntity {
    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(length = 50) private String label;
    @Column(name = "recipient_name", nullable = false, length = 150) private String recipientName;
    @Column(length = 30) private String phone;
    @Column(name = "address_line1", nullable = false, length = 255) private String addressLine1;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 100) private String subdistrict;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, length = 100) private String province;
    @Column(name = "postal_code", nullable = false, length = 10) private String postalCode;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "is_default_shipping", nullable = false) private boolean defaultShipping;
    @Column(name = "is_default_billing", nullable = false) private boolean defaultBilling;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected ShopCustomerAddress() {}

    public ShopCustomerAddress(UUID customerId) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.countryCode = "TH";
    }

    public void apply(String label, String recipientName, String phone, String addressLine1, String addressLine2,
                      String subdistrict, String district, String province, String postalCode, String countryCode) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.subdistrict = subdistrict;
        this.district = district;
        this.province = province;
        this.postalCode = postalCode;
        this.countryCode = countryCode == null ? "TH" : countryCode;
    }

    public void softDelete(Instant at) { this.deletedAt = at; this.defaultShipping = false; this.defaultBilling = false; }
    public void moveTo(UUID customerId) { this.customerId = customerId; }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
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
    public boolean isDefaultShipping() { return defaultShipping; }
    public void setDefaultShipping(boolean value) { this.defaultShipping = value; }
    public boolean isDefaultBilling() { return defaultBilling; }
    public void setDefaultBilling(boolean value) { this.defaultBilling = value; }
    public Instant getDeletedAt() { return deletedAt; }
}
