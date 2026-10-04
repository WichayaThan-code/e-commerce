package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shops", uniqueConstraints = @UniqueConstraint(name = "uq_shops_slug", columnNames = "slug"))
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shop_id")
    private Long shopId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "application_id", unique = true) private MerchantApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private Owner owner;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "name_en", length = 100) private String nameEn;
    @Column(length = 500) private String description;
    @Column(nullable = false, length = 100) private String category;
    @Enumerated(EnumType.STRING) @Column(name = "sale_type", nullable = false) private SaleType saleType;
    @Column(length = 500) private String website;
    @Column(name = "line_id", length = 500) private String lineId;
    @Column(name = "facebook_url", length = 500) private String facebookUrl;
    @Column(name = "instagram_url", length = 500) private String instagramUrl;
    @Column(name = "tiktok_url", length = 500) private String tiktokUrl;
    @Column(name = "logo_path", length = 500) private String logoPath;
    @Column(name = "cover_path", length = 500) private String coverPath;
    @Column(name = "opening_hours", length = 800) private String openingHours;
    @Column(nullable = false, length = 500) private String address;
    @Column(nullable = false, length = 100) private String province;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, length = 100) private String subdistrict;
    @Column(name = "postal_code", nullable = false, length = 10) private String postalCode;
    @Column(nullable = false, length = 80) private String slug;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ShopStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Shop() {}

    public Shop(MerchantApplication application, String slug, Owner owner) {
        var now = Instant.now();
        this.application = application;
        this.user = application.getUser();
        this.owner = owner;
        this.name = application.getShopName();
        this.nameEn = blankToNull(application.getShopNameEn());
        this.description = blankToNull(application.getShopDescription());
        this.category = application.getCategory();
        this.saleType = application.getSaleType();
        this.website = blankToNull(application.getWebsite());
        this.lineId = blankToNull(application.getLineId());
        this.facebookUrl = blankToNull(application.getFacebookUrl());
        this.instagramUrl = blankToNull(application.getInstagramUrl());
        this.tiktokUrl = blankToNull(application.getTiktokUrl());
        this.logoPath = application.getLogoPath();
        this.address = application.getShopAddress();
        this.province = application.getShopProvince();
        this.district = application.getShopDistrict();
        this.subdistrict = application.getShopSubdistrict();
        this.postalCode = application.getShopPostalCode();
        this.slug = slug;
        this.status = ShopStatus.PENDING_REVIEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String path() { return "/shop/" + slug; }

    public void approve() {
        status = ShopStatus.APPROVED;
        updatedAt = Instant.now();
        application.markApproved();
    }

    public void reject() {
        status = ShopStatus.REJECTED;
        updatedAt = Instant.now();
        application.markRejected();
    }

    public void suspend() {
        status = ShopStatus.SUSPENDED;
        updatedAt = Instant.now();
    }

    public void reactivate() {
        if (status != ShopStatus.SUSPENDED) {
            throw new IllegalStateException("เปิดใช้งานได้เฉพาะร้านที่ถูกระงับ");
        }
        status = ShopStatus.APPROVED;
        updatedAt = Instant.now();
    }

    public void updateSettings(
        String name,
        String nameEn,
        String description,
        String category,
        String website,
        String lineId,
        String facebookUrl,
        String instagramUrl,
        String tiktokUrl,
        String address,
        String province,
        String district,
        String subdistrict,
        String postalCode
    ) {
        this.name = name;
        this.nameEn = nameEn;
        this.description = description;
        this.category = category;
        this.website = website;
        this.lineId = lineId;
        this.facebookUrl = facebookUrl;
        this.instagramUrl = instagramUrl;
        this.tiktokUrl = tiktokUrl;
        this.address = address;
        this.province = province;
        this.district = district;
        this.subdistrict = subdistrict;
        this.postalCode = postalCode;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return shopId; }
    public void assignSlug(String value) { this.slug = value; }
    public String getName() { return name; }
    public String getNameEn() { return nameEn; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public String getProvince() { return province; }
    public String getDistrict() { return district; }
    public String getSubdistrict() { return subdistrict; }
    public String getPostalCode() { return postalCode; }
    public String getSlug() { return slug; }
    public String getWebsite() { return website; }
    public String getLineId() { return lineId; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getTiktokUrl() { return tiktokUrl; }
    public ShopStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UserAccount getUser() { return user; }
    public Owner getOwner() { return owner; }
    public MerchantApplication getApplication() { return application; }
    public void setCoverPath(String path) {
        this.coverPath = path;
        this.updatedAt = Instant.now();
    }

    public String getCoverPath() { return coverPath; }
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String value) {
        this.openingHours = value;
        this.updatedAt = Instant.now();
    }

    public String getOwnedLogoPath() { return logoPath; }
    public void setLogoPath(String path) {
        this.logoPath = path;
        this.updatedAt = Instant.now();
    }

    public String getLogoPath() {
        if (logoPath != null && !logoPath.isBlank()) return logoPath;
        return application == null ? null : application.getLogoPath();
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
