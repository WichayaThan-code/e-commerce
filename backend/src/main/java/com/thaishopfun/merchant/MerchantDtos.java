package com.thaishopfun.merchant;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        String shopName,String shopNameEn,String description,String category,SaleType saleType,String website,String logoPath,
        String shopAddress,String shopProvince,String shopDistrict,String shopSubdistrict,String shopPostalCode,
        OwnerType ownerType,String title,String firstName,String lastName,String citizenIdMasked,LocalDate birthDate,String nationality,String phone,String email,
        String houseNo,String villageNo,String building,String alley,String road,String province,String district,String subdistrict,String postalCode,
        String bankName,String bankAccountMasked,String bankAccountName,List<DocumentResponse> documents,Instant submittedAt
    ){}
}

