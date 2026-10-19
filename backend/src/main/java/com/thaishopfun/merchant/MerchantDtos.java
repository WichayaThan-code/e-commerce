package com.thaishopfun.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MerchantDtos {
    private MerchantDtos(){}
    public record ShopRequest(
        @NotBlank @Size(max=100) String shopName,
        @Size(max=100) String shopNameEn,
        @Size(max=500) String description,
        @NotBlank @Size(max=100) String category,
        @NotNull SaleType saleType,
        @Size(max=500) String website,
        @Size(max=500) String lineId,
        @Size(max=500) String facebookUrl,
        @Size(max=500) String instagramUrl,
        @Size(max=500) String tiktokUrl,
        @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=100) String province,
        @NotBlank @Size(max=100) String district,
        @NotBlank @Size(max=100) String subdistrict,
        @NotBlank @Pattern(regexp="^[0-9]{5}$") String postalCode
    ){}
    public record OwnerRequest(
        @NotNull OwnerType ownerType,
        @NotBlank String title,
        @NotBlank @Size(max=100) String firstName,
        @NotBlank @Size(max=100) String lastName,
        @NotBlank @Pattern(regexp="^[0-9 -]{13,20}$") String citizenId,
        @NotNull LocalDate birthDate,
        @NotBlank String nationality,
        @NotBlank @Pattern(regexp="^[0-9+ -]{9,20}$") String phone,
        @NotBlank @Email String email,
        @NotBlank String houseNo,
        String villageNo,String building,String alley,String road,
        @NotBlank String province,@NotBlank String district,@NotBlank String subdistrict,
        @NotBlank @Pattern(regexp="^[0-9]{5}$") String postalCode,
        @NotBlank String bankName,
        @NotBlank @Pattern(regexp="^[0-9 -]{8,20}$") String bankAccountNumber,
        @NotBlank String bankAccountName
    ){}
    public record DocumentResponse(UUID id,DocumentType type,String originalFilename,String contentType,long fileSize,String sha256,Instant uploadedAt){static DocumentResponse from(MerchantDocument d){return new DocumentResponse(d.getId(),d.getType(),d.getOriginalFilename(),d.getContentType(),d.getFileSize(),d.getSha256(),d.getUploadedAt());}}
    public record ApplicationResponse(
        UUID id,ApplicationStatus status,String referenceNo,
        String shopName,String shopNameEn,String description,String category,SaleType saleType,String website,String lineId,String facebookUrl,String instagramUrl,String tiktokUrl,String logoPath,
        String shopAddress,String shopProvince,String shopDistrict,String shopSubdistrict,String shopPostalCode,
        OwnerType ownerType,String title,String firstName,String lastName,String citizenIdMasked,LocalDate birthDate,String nationality,String phone,String email,
        String houseNo,String villageNo,String building,String alley,String road,String province,String district,String subdistrict,String postalCode,
        String bankName,String bankAccountMasked,String bankAccountName,List<DocumentResponse> documents,Instant submittedAt,
        String shopPath, UUID ownerId
    ){}
    public record MerchantOwnerResponse(
        UUID id, OwnerType ownerType, String title, String firstName, String lastName,
        String citizenIdMasked, String phone, String email, String sensitiveReview
    ){}
    public record OwnerShopRef(Long shopId, String shopName, ShopStatus status){}
    public record PublicShopResponse(
        Long shopId,
        String name,String nameEn,String nameZh,String description,String descriptionEn,String descriptionZh,String category,
        String address,String province,String district,String subdistrict,String postalCode,
        String slug,String path,ShopStatus status,String logoUrl,String coverUrl,
        List<ShopHour> openingHours,
        ShopThemeResponse theme,
        Instant createdAt,
        Long productCount,
        String phone,String email,String website,String lineId,String facebookUrl,String instagramUrl,String tiktokUrl
    ){}
    public record PublicShopCategoryResponse(Long id, String nameTh, String nameEn, String nameZh, long productCount, String slug) {}
    public record ShopPolicyResponse(String type, String title, String body, Instant updatedAt) {}
    public record ShopPolicyItemRequest(
        @NotBlank @Pattern(regexp = "(?i)shipping|returns|privacy|terms") String type,
        @Size(max = 150) String title,
        @Size(max = 20000) String body
    ) {}
    public record ShopPoliciesUpdateRequest(
        @NotNull @Size(max = 4) List<@Valid ShopPolicyItemRequest> policies
    ) {}
    public record ShopThemeResponse(String templateKey, Map<String, Object> config) {}
    public record ShopThemeUpdateRequest(
        @NotBlank String templateKey,
        @NotNull Map<String, Object> config
    ) {}
    public record ShopThemePresetResponse(String templateKey, Map<String, Object> config) {}
    public record AdminDocumentResponse(UUID id,String type,String label,String filename,long fileSize){}
    public record AdminStoreApplicationResponse(
        Long shopId,String shopName,String ownerName,String phone,String email,
        String province,String category,String address,String description,String website,String lineId,String facebookUrl,String instagramUrl,String tiktokUrl,
        Instant appliedAt,Instant updatedAt,ShopStatus status,String slug,String logoUrl,
        List<AdminDocumentResponse> documents,
        String reinstatementMessage,
        List<OwnerShopRef> otherShops
    ){}
    public record ReinstatementResponse(Long shopId, String status, String message, Instant createdAt) {}
    public record ReinstatementRequestBody(@NotBlank @Size(max = 1000) String message) {}
    public record StoreAccessResponse(boolean approved,boolean hasShop,ShopStatus status,String shopName,String slug,Long shopId){}
    public record MerchantShopSummary(Long shopId,String shopName,ShopStatus status,String slug,UUID ownerId){}
    public record MerchantSettingsResponse(
        Long shopId, ShopStatus status, boolean canOperate,
        String shopName, String shopNameEn, String description, String category,
        String phone, String email,
        String website, String lineId, String facebookUrl, String instagramUrl, String tiktokUrl,
        String address, String province, String district, String subdistrict, String postalCode,
        String coverUrl, String logoUrl, String slug, Instant createdAt, List<ShopHour> openingHours,
        String publicPhone, String publicEmail
    ) {}
    public record ShopHour(
        boolean open,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String start,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String end
    ) {}
    public record OpeningHoursRequest(@NotNull @Size(min = 7, max = 7) List<ShopHour> days) {}
    public record ProductCategoryResponse(long id, String nameTh, String nameEn, String nameZh) {}
    public record MerchantSettingsUpdateRequest(
        @NotBlank @Size(max=100) String shopName,
        @Size(max=100) String shopNameEn,
        @Size(max=500) String description,
        @NotBlank @Size(max=100) String category,
        @NotBlank @Pattern(regexp="^[0-9+ -]{9,20}$") String phone,
        @NotBlank @Email String email,
        @Size(max=500) String website,
        @Size(max=500) String lineId,
        @Size(max=500) String facebookUrl,
        @Size(max=500) String instagramUrl,
        @Size(max=500) String tiktokUrl,
        @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=100) String province,
        @NotBlank @Size(max=100) String district,
        @NotBlank @Size(max=100) String subdistrict,
        @NotBlank @Pattern(regexp="^[0-9]{5}$") String postalCode,
        @Pattern(regexp="^$|^[0-9+ -]{9,20}$") String publicPhone,
        @Email @Size(max=255) String publicEmail
    ) {}
    public record BulkShopRequest(@NotEmpty List<Long> shopIds){}
    public record BulkShopResponse(int updated,int skipped,List<AdminStoreApplicationResponse> applications){}
    /** Marketplace search shop hit: public card fields only (no contact, address, owner or theme data). */
    public record PublicShopSearchResponse(
        String slug, String name, String nameEn, String nameZh, String category, String province,
        String logoUrl, long productCount
    ){}
}

