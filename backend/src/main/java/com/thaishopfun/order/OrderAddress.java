package com.thaishopfun.order;

import com.thaishopfun.catalog.AuditedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "order_addresses")
public class OrderAddress extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "address_type", nullable = false, length = 20) private String addressType;
    @Column(name = "recipient_name", nullable = false, length = 150) private String recipientName;
    @Column(nullable = false, length = 30) private String phone;
    @Column(name = "address_line1", nullable = false, length = 255) private String addressLine1;
    @Column(name = "address_line2", length = 255) private String addressLine2;
    @Column(length = 100) private String subdistrict;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, length = 100) private String province;
    @Column(name = "postal_code", nullable = false, length = 10) private String postalCode;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "country_code", nullable = false, length = 2) private String countryCode = "TH";

    protected OrderAddress() {}

    public OrderAddress(String addressType) {
        this.id = UUID.randomUUID();
        this.addressType = addressType;
    }

    void setOrder(Order order) { this.order = order; }
    public UUID getId() { return id; }
    public String getAddressType() { return addressType; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getSubdistrict() { return subdistrict; }
    public void setSubdistrict(String subdistrict) { this.subdistrict = subdistrict; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountryCode() { return countryCode; }
}
