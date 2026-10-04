package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "owners")
public class Owner {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @Enumerated(EnumType.STRING) @Column(name = "owner_type", nullable = false, length = 30) private OwnerType ownerType;
    @Column(nullable = false, length = 30) private String title;
    @Column(name = "first_name", nullable = false, length = 100) private String firstName;
    @Column(name = "last_name", nullable = false, length = 100) private String lastName;
    @Column(name = "citizen_id_hash", length = 64) private String citizenIdHash;
    @Column(name = "citizen_id_encrypted", nullable = false, columnDefinition = "text") private String citizenIdEncrypted;
    @Column(name = "birth_date", nullable = false) private LocalDate birthDate;
    @Column(nullable = false, length = 100) private String nationality;
    @Column(nullable = false, length = 30) private String phone;
    @Column(nullable = false) private String email;
    @Column(name = "house_no", nullable = false, length = 50) private String houseNo;
    @Column(name = "village_no", length = 50) private String villageNo;
    @Column(length = 255) private String building;
    @Column(length = 255) private String alley;
    @Column(length = 255) private String road;
    @Column(nullable = false, length = 100) private String province;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, length = 100) private String subdistrict;
    @Column(name = "postal_code", nullable = false, length = 10) private String postalCode;
    @Column(name = "bank_name", nullable = false, length = 100) private String bankName;
    @Column(name = "bank_account_encrypted", nullable = false, columnDefinition = "text") private String bankAccountEncrypted;
    @Column(name = "bank_account_name", nullable = false) private String bankAccountName;
    @Column(name = "sensitive_review", nullable = false, length = 30) private String sensitiveReview;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Owner() {}

    public Owner(UserAccount user, MerchantDtos.OwnerRequest request, String citizenEncrypted, String citizenHash, String bankEncrypted) {
        var now = Instant.now();
        this.id = UUID.randomUUID();
        this.user = user;
        this.sensitiveReview = "NONE";
        this.createdAt = now;
        apply(request, citizenEncrypted, citizenHash, bankEncrypted, false);
    }

    public void apply(MerchantDtos.OwnerRequest request, String citizenEncrypted, String citizenHash, String bankEncrypted, boolean markSensitiveReview) {
        ownerType = request.ownerType();
        title = request.title().trim();
        firstName = request.firstName().trim();
        lastName = request.lastName().trim();
        citizenIdEncrypted = citizenEncrypted;
        citizenIdHash = citizenHash;
        birthDate = request.birthDate();
        nationality = request.nationality().trim();
        phone = request.phone().trim();
        email = request.email().trim().toLowerCase();
        houseNo = request.houseNo().trim();
        villageNo = blankToNull(request.villageNo());
        building = blankToNull(request.building());
        alley = blankToNull(request.alley());
        road = blankToNull(request.road());
        province = request.province().trim();
        district = request.district().trim();
        subdistrict = request.subdistrict().trim();
        postalCode = request.postalCode().trim();
        bankName = request.bankName().trim();
        bankAccountEncrypted = bankEncrypted;
        bankAccountName = request.bankAccountName().trim();
        updatedAt = Instant.now();
        if (markSensitiveReview) sensitiveReview = "PENDING_REVIEW";
    }

    public void updateContact(String phone, String email) {
        this.phone = phone;
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public OwnerType getOwnerType() { return ownerType; }
    public String getTitle() { return title; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getCitizenIdHash() { return citizenIdHash; }
    public String getCitizenIdEncrypted() { return citizenIdEncrypted; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getNationality() { return nationality; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getHouseNo() { return houseNo; }
    public String getVillageNo() { return villageNo; }
    public String getBuilding() { return building; }
    public String getAlley() { return alley; }
    public String getRoad() { return road; }
    public String getProvince() { return province; }
    public String getDistrict() { return district; }
    public String getSubdistrict() { return subdistrict; }
    public String getPostalCode() { return postalCode; }
    public String getBankName() { return bankName; }
    public String getBankAccountEncrypted() { return bankAccountEncrypted; }
    public String getBankAccountName() { return bankAccountName; }
    public String getSensitiveReview() { return sensitiveReview; }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
