package com.thaishopfun.merchant;

import com.thaishopfun.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="merchant_applications")
public class MerchantApplication {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private UserAccount user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="owner_id") private Owner linkedOwner;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ApplicationStatus status;
    @Column(name="reference_no", unique=true,length=30) private String referenceNo;
    @Column(name="shop_name",length=100) private String shopName;
    @Column(name="shop_name_en",length=100) private String shopNameEn;
    @Column(name="shop_description",length=500) private String shopDescription;
    @Column(length=100) private String category;
    @Enumerated(EnumType.STRING) @Column(name="sale_type") private SaleType saleType;
    @Column(length=500) private String website;
    @Column(name="line_id",length=500) private String lineId;
    @Column(name="facebook_url",length=500) private String facebookUrl;
    @Column(name="instagram_url",length=500) private String instagramUrl;
    @Column(name="tiktok_url",length=500) private String tiktokUrl;
    @Column(name="logo_path",length=500) private String logoPath;
    @Column(name="shop_address",length=500) private String shopAddress;
    @Column(name="shop_province",length=100) private String shopProvince;
    @Column(name="shop_district",length=100) private String shopDistrict;
    @Column(name="shop_subdistrict",length=100) private String shopSubdistrict;
    @Column(name="shop_postal_code",length=10) private String shopPostalCode;
    @Enumerated(EnumType.STRING) @Column(name="owner_type") private OwnerType ownerType;
    @Column(name="owner_title") private String ownerTitle;
    @Column(name="owner_first_name") private String ownerFirstName;
    @Column(name="owner_last_name") private String ownerLastName;
    @Column(name="citizen_id_encrypted",columnDefinition="text") private String citizenIdEncrypted;
    @Column(name="citizen_id_hash",length=64) private String citizenIdHash;
    @Column(name="birth_date") private LocalDate birthDate;
    private String nationality;
    @Column(name="owner_phone") private String ownerPhone;
    @Column(name="owner_email") private String ownerEmail;
    @Column(name="owner_house_no") private String ownerHouseNo;
    @Column(name="owner_village_no") private String ownerVillageNo;
    @Column(name="owner_building") private String ownerBuilding;
    @Column(name="owner_alley") private String ownerAlley;
    @Column(name="owner_road") private String ownerRoad;
    @Column(name="owner_province") private String ownerProvince;
    @Column(name="owner_district") private String ownerDistrict;
    @Column(name="owner_subdistrict") private String ownerSubdistrict;
    @Column(name="owner_postal_code") private String ownerPostalCode;
    @Column(name="bank_name") private String bankName;
    @Column(name="bank_account_encrypted",columnDefinition="text") private String bankAccountEncrypted;
    @Column(name="bank_account_name") private String bankAccountName;
    @Column(name="submitted_at") private Instant submittedAt;
    @Column(name="created_date_time", nullable=false) private Instant createdAt;
    @Column(name="modified_date_time", nullable=false) private Instant updatedAt;

    protected MerchantApplication() {}
    public MerchantApplication(UserAccount user) { var now=Instant.now(); this.id=UUID.randomUUID(); this.user=user; this.status=ApplicationStatus.DRAFT; this.createdAt=now; this.updatedAt=now; }
    public void ensureEditable(){ if(status!=ApplicationStatus.DRAFT) throw new IllegalStateException("ใบสมัครถูกส่งแล้วและไม่สามารถแก้ไขได้"); }
    public void updateShop(MerchantDtos.ShopRequest r){ensureEditable();shopName=r.shopName().trim();shopNameEn=trim(r.shopNameEn());shopDescription=trim(r.description());category=r.category().trim();saleType=r.saleType();website=trim(r.website());lineId=trim(r.lineId());facebookUrl=trim(r.facebookUrl());instagramUrl=trim(r.instagramUrl());tiktokUrl=trim(r.tiktokUrl());shopAddress=r.address().trim();shopProvince=r.province().trim();shopDistrict=r.district().trim();shopSubdistrict=r.subdistrict().trim();shopPostalCode=r.postalCode().trim();updatedAt=Instant.now();}
    public void updateOwner(MerchantDtos.OwnerRequest r,String citizenEncrypted,String citizenHash,String bankEncrypted){ensureEditable();ownerType=r.ownerType();ownerTitle=r.title().trim();ownerFirstName=r.firstName().trim();ownerLastName=r.lastName().trim();citizenIdEncrypted=citizenEncrypted;citizenIdHash=citizenHash;birthDate=r.birthDate();nationality=r.nationality().trim();ownerPhone=r.phone().trim();ownerEmail=r.email().trim().toLowerCase();ownerHouseNo=r.houseNo().trim();ownerVillageNo=trim(r.villageNo());ownerBuilding=trim(r.building());ownerAlley=trim(r.alley());ownerRoad=trim(r.road());ownerProvince=r.province().trim();ownerDistrict=r.district().trim();ownerSubdistrict=r.subdistrict().trim();ownerPostalCode=r.postalCode().trim();bankName=trim(r.bankName());bankAccountEncrypted=bankEncrypted;bankAccountName=trim(r.bankAccountName());updatedAt=Instant.now();}
    public void attachOwner(Owner owner){ensureEditable();linkedOwner=owner;ownerType=owner.getOwnerType();ownerTitle=owner.getTitle();ownerFirstName=owner.getFirstName();ownerLastName=owner.getLastName();citizenIdEncrypted=owner.getCitizenIdEncrypted();citizenIdHash=owner.getCitizenIdHash();birthDate=owner.getBirthDate();nationality=owner.getNationality();ownerPhone=owner.getPhone();ownerEmail=owner.getEmail();ownerHouseNo=owner.getHouseNo();ownerVillageNo=owner.getVillageNo();ownerBuilding=owner.getBuilding();ownerAlley=owner.getAlley();ownerRoad=owner.getRoad();ownerProvince=owner.getProvince();ownerDistrict=owner.getDistrict();ownerSubdistrict=owner.getSubdistrict();ownerPostalCode=owner.getPostalCode();bankName=owner.getBankName();bankAccountEncrypted=owner.getBankAccountEncrypted();bankAccountName=owner.getBankAccountName();updatedAt=Instant.now();}
    public void clearShopDetails(){ensureEditable();shopName=null;shopNameEn=null;shopDescription=null;category=null;saleType=null;website=null;lineId=null;facebookUrl=null;instagramUrl=null;tiktokUrl=null;logoPath=null;shopAddress=null;shopProvince=null;shopDistrict=null;shopSubdistrict=null;shopPostalCode=null;updatedAt=Instant.now();}
    public void syncFromShop(Shop shop,String phone,String email){shopName=shop.getName();shopNameEn=shop.getNameEn();shopDescription=shop.getDescription();category=shop.getCategory();website=shop.getWebsite();lineId=shop.getLineId();facebookUrl=shop.getFacebookUrl();instagramUrl=shop.getInstagramUrl();tiktokUrl=shop.getTiktokUrl();shopAddress=shop.getAddress();shopProvince=shop.getProvince();shopDistrict=shop.getDistrict();shopSubdistrict=shop.getSubdistrict();shopPostalCode=shop.getPostalCode();ownerPhone=phone;ownerEmail=email;updatedAt=Instant.now();}
    public void setLogoPath(String path){ensureEditable();logoPath=path;updatedAt=Instant.now();}
    public void submit(String reference){ensureEditable();status=ApplicationStatus.SUBMITTED;referenceNo=reference;submittedAt=Instant.now();updatedAt=submittedAt;}
    public void markApproved(){status=ApplicationStatus.APPROVED;updatedAt=Instant.now();}
    public void markRejected(){status=ApplicationStatus.REJECTED;updatedAt=Instant.now();}
    private static String trim(String value){return value==null?null:value.trim();}

    public UUID getId(){return id;} public UserAccount getUser(){return user;} public Owner getLinkedOwner(){return linkedOwner;} public ApplicationStatus getStatus(){return status;} public String getReferenceNo(){return referenceNo;}
    public String getShopName(){return shopName;} public String getShopNameEn(){return shopNameEn;} public String getShopDescription(){return shopDescription;} public String getCategory(){return category;} public SaleType getSaleType(){return saleType;} public String getWebsite(){return website;} public String getLineId(){return lineId;} public String getFacebookUrl(){return facebookUrl;} public String getInstagramUrl(){return instagramUrl;} public String getTiktokUrl(){return tiktokUrl;} public String getLogoPath(){return logoPath;} public String getShopAddress(){return shopAddress;} public String getShopProvince(){return shopProvince;} public String getShopDistrict(){return shopDistrict;} public String getShopSubdistrict(){return shopSubdistrict;} public String getShopPostalCode(){return shopPostalCode;}
    public OwnerType getOwnerType(){return ownerType;} public String getOwnerTitle(){return ownerTitle;} public String getOwnerFirstName(){return ownerFirstName;} public String getOwnerLastName(){return ownerLastName;} public String getCitizenIdEncrypted(){return citizenIdEncrypted;} public String getCitizenIdHash(){return citizenIdHash;} public LocalDate getBirthDate(){return birthDate;} public String getNationality(){return nationality;} public String getOwnerPhone(){return ownerPhone;} public String getOwnerEmail(){return ownerEmail;} public String getOwnerHouseNo(){return ownerHouseNo;} public String getOwnerVillageNo(){return ownerVillageNo;} public String getOwnerBuilding(){return ownerBuilding;} public String getOwnerAlley(){return ownerAlley;} public String getOwnerRoad(){return ownerRoad;} public String getOwnerProvince(){return ownerProvince;} public String getOwnerDistrict(){return ownerDistrict;} public String getOwnerSubdistrict(){return ownerSubdistrict;} public String getOwnerPostalCode(){return ownerPostalCode;}
    public String getBankName(){return bankName;} public String getBankAccountEncrypted(){return bankAccountEncrypted;} public String getBankAccountName(){return bankAccountName;} public Instant getSubmittedAt(){return submittedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
