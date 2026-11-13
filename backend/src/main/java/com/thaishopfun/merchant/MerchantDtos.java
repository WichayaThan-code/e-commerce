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
        // English shop name must not contain Thai script (U+0E00–U+0E7F); the Thai name may contain English.
        @Size(max=100) @Pattern(regexp="^[^\\u0E00-\\u0E7F]*$", message="ชื่อร้านภาษาอังกฤษต้องไม่มีตัวอักษรภาษาไทย") String shopNameEn,
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
        @NotBlank @Size(max=10) @Pattern(regexp="^[0-9]{5}$") String postalCode
    ){}
    public record OwnerRequest(
        @NotNull OwnerType ownerType,
        @NotBlank @Size(max = 30, message = "คำนำหน้ายาวเกิน 30 ตัวอักษร") String title,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Pattern(regexp = "^[0-9 -]{13,20}$") String citizenId,
        @NotNull LocalDate birthDate,
        @NotBlank @Size(max = 100, message = "สัญชาติยาวเกิน 100 ตัวอักษร") String nationality,
        @NotBlank @Size(max = 30, message = "เบอร์โทรยาวเกิน 30 ตัวอักษร") @Pattern(regexp = "^[0-9+ -]{9,20}$") String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 50, message = "บ้านเลขที่ยาวเกิน 50 ตัวอักษร") String houseNo,
        @Size(max = 50, message = "หมู่บ้านยาวเกิน 50 ตัวอักษร") String villageNo,
        @Size(max = 255, message = "อาคาร/หมู่บ้านยาวเกิน 255 ตัวอักษร") String building,
        @Size(max = 255, message = "ซอยยาวเกิน 255 ตัวอักษร") String alley,
        @Size(max = 255, message = "ถนนยาวเกิน 255 ตัวอักษร") String road,
        @NotBlank @Size(max = 100, message = "จังหวัดยาวเกิน 100 ตัวอักษร") String province,
        @NotBlank @Size(max = 100, message = "อำเภอ/เขตยาวเกิน 100 ตัวอักษร") String district,
        @NotBlank @Size(max = 100, message = "ตำบล/แขวงยาวเกิน 100 ตัวอักษร") String subdistrict,
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[0-9]{5}$") String postalCode,
        @NotBlank @Size(max = 100, message = "ชื่อธนาคารยาวเกิน 100 ตัวอักษร") String bankName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[0-9 -]{8,20}$") String bankAccountNumber,
        @NotBlank @Size(max = 255, message = "ชื่อบัญชียาวเกิน 255 ตัวอักษร") String bankAccountName
    ){}
    /**
     * Owner details for an application linked to an owner the user already has (second shop). Same rules as
     * {@link OwnerRequest} except the citizen id, which stays the linked owner's (a different id = a different owner).
     */
    public record LinkedOwnerRequest(
        @NotNull OwnerType ownerType,
        @NotBlank @Size(max = 30, message = "คำนำหน้ายาวเกิน 30 ตัวอักษร") String title,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull LocalDate birthDate,
        @NotBlank @Size(max = 100, message = "สัญชาติยาวเกิน 100 ตัวอักษร") String nationality,
        @NotBlank @Size(max = 30, message = "เบอร์โทรยาวเกิน 30 ตัวอักษร") @Pattern(regexp = "^[0-9+ -]{9,20}$") String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 50, message = "บ้านเลขที่ยาวเกิน 50 ตัวอักษร") String houseNo,
        @Size(max = 50, message = "หมู่บ้านยาวเกิน 50 ตัวอักษร") String villageNo,
        @Size(max = 255, message = "อาคาร/หมู่บ้านยาวเกิน 255 ตัวอักษร") String building,
        @Size(max = 255, message = "ซอยยาวเกิน 255 ตัวอักษร") String alley,
        @Size(max = 255, message = "ถนนยาวเกิน 255 ตัวอักษร") String road,
        @NotBlank @Size(max = 100, message = "จังหวัดยาวเกิน 100 ตัวอักษร") String province,
        @NotBlank @Size(max = 100, message = "อำเภอ/เขตยาวเกิน 100 ตัวอักษร") String district,
        @NotBlank @Size(max = 100, message = "ตำบล/แขวงยาวเกิน 100 ตัวอักษร") String subdistrict,
        @NotBlank @Size(max = 10) @Pattern(regexp = "^[0-9]{5}$") String postalCode,
        @NotBlank @Size(max = 100, message = "ชื่อธนาคารยาวเกิน 100 ตัวอักษร") String bankName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[0-9 -]{8,20}$") String bankAccountNumber,
        @NotBlank @Size(max = 255, message = "ชื่อบัญชียาวเกิน 255 ตัวอักษร") String bankAccountName
    ){
        /** Same shape as OwnerRequest for the shared snapshot setter; the citizen id is supplied by the linked owner. */
        OwnerRequest asOwnerRequest() {
            return new OwnerRequest(ownerType, title, firstName, lastName, null, birthDate, nationality, phone, email, houseNo,
                villageNo, building, alley, road, province, district, subdistrict, postalCode, bankName, bankAccountNumber, bankAccountName);
        }
    }
    public record DocumentResponse(UUID id,DocumentType type,String originalFilename,String contentType,long fileSize,String sha256,Instant uploadedAt){static DocumentResponse from(MerchantDocument d){return new DocumentResponse(d.getId(),d.getType(),d.getOriginalFilename(),d.getContentType(),d.getFileSize(),d.getSha256(),d.getUploadedAt());}}
    public record ApplicationResponse(
        UUID id,ApplicationStatus status,String referenceNo,
        String shopName,String shopNameEn,String description,String category,SaleType saleType,String website,String lineId,String facebookUrl,String instagramUrl,String tiktokUrl,String logoPath,
        String shopAddress,String shopProvince,String shopDistrict,String shopSubdistrict,String shopPostalCode,
        OwnerType ownerType,String title,String firstName,String lastName,String citizenIdMasked,LocalDate birthDate,String nationality,String phone,String email,
        String houseNo,String villageNo,String building,String alley,String road,String province,String district,String subdistrict,String postalCode,
        String bankName,String bankAccountMasked,String bankAccountName,List<DocumentResponse> documents,Instant submittedAt,
        String shopPath, UUID ownerId, boolean existingOwner
    ){}
    /** Shop-name availability for the register step (same rule as saveShop/submit). */
    public record ShopNameCheckResponse(boolean available){}
    public record MerchantOwnerResponse(
        UUID id, OwnerType ownerType, String title, String firstName, String lastName,
        String citizenIdMasked, String phone, String email, String sensitiveReview
    ){}
    public record OwnerShopRef(Long shopId, String shopName, ShopStatus status){}
    public record PublicShopResponse(
        Long shopId,
        String name,String nameEn,String nameZh,String description,String descriptionEn,String descriptionZh,String category,
        String address,String province,String district,String subdistrict,String postalCode,
        Double latitude, Double longitude,
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
    public record ThemeMediaResponse(
        UUID id, String kind, String originalFilename, String mimeType, long sizeBytes,
        Integer width, Integer height, String url, Instant createdAt
    ) {}
    public record AdminDocumentResponse(UUID id,String type,String label,String filename,long fileSize){}
    public record AdminStoreApplicationResponse(
        Long shopId,String shopName,String ownerName,String phone,String email,
        String province,String category,String address,String description,String website,String lineId,String facebookUrl,String instagramUrl,String tiktokUrl,
        Instant appliedAt,Instant updatedAt,ShopStatus status,String slug,String logoUrl,
        List<AdminDocumentResponse> documents,
        String reinstatementMessage,
        List<OwnerShopRef> otherShops
    ){}
    /** Full owner contact for PLATFORM_CONTACT_REVEAL (never written into audit payloads). */
    public record ContactRevealResponse(String phone, String email) {}
    public record ReinstatementResponse(Long shopId, String status, String message, Instant createdAt) {}
    public record ReinstatementRequestBody(@NotBlank @Size(max = 1000) String message) {}
    public record StoreAccessResponse(boolean approved,boolean hasShop,ShopStatus status,String shopName,String slug,Long shopId){}
    public record MerchantShopSummary(Long shopId,String shopName,ShopStatus status,String slug,UUID ownerId,String logoUrl){}
    public record MerchantSettingsResponse(
        Long shopId, ShopStatus status, boolean canOperate,
        String shopName, String shopNameEn, String description, String category,
        String phone, String email,
        String website, String lineId, String facebookUrl, String instagramUrl, String tiktokUrl,
        String address, String province, String district, String subdistrict, String postalCode,
        Double latitude, Double longitude,
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
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude,
        @Pattern(regexp="^$|^[0-9+ -]{9,20}$") String publicPhone,
        @Email @Size(max=255) String publicEmail
    ) {}
    /** Field-scoped contact channels for /merchant/settings?tab=integrations (not OAuth/API sync). */
    public record MerchantChannelsUpdateRequest(
        @Size(max=500) String website,
        @Size(max=500) String lineId,
        @Size(max=500) String facebookUrl,
        @Size(max=500) String instagramUrl,
        @Size(max=500) String tiktokUrl
    ) {}
    /**
     * Bulk shop action. For suspend, {@code reasonCode} is required (validated in the service).
     * {@code reason} is optional free-text (≤500). Reactivate may omit both.
     */
    public record BulkShopRequest(
        @NotEmpty List<Long> shopIds,
        @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Z][A-Z0-9_]{1,49}$", message = "reasonCode ต้องเป็นตัวพิมพ์ใหญ่ ตัวเลข หรือ _ (2-50 ตัว)")
        String reasonCode,
        @Size(max = 500, message = "เหตุผลยาวเกิน 500 ตัวอักษร") String reason
    ) {}
    public record BulkShopResponse(int updated,int skipped,List<AdminStoreApplicationResponse> applications){}
    /** Marketplace search shop hit: public card fields only (no contact, address, owner or theme data). */
    public record PublicShopSearchResponse(
        String slug, String name, String nameEn, String nameZh, String category, String province,
        String logoUrl, long productCount
    ){}
}

