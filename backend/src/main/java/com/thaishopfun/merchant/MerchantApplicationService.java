package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MerchantApplicationService {
    private final MerchantApplicationRepository applications;
    private final MerchantDocumentRepository documents;
    private final UserAccountRepository users;
    private final SensitiveDataCipher cipher;
    private final MerchantStorageService storage;
    private final ShopCatalogService shops;
    private final OwnerAccountService ownerAccounts;
    private final ProductCategoryService categories;

    public MerchantApplicationService(MerchantApplicationRepository applications, MerchantDocumentRepository documents,
                                      UserAccountRepository users, SensitiveDataCipher cipher, MerchantStorageService storage,
                                      ShopCatalogService shops, OwnerAccountService ownerAccounts,
                                      ProductCategoryService categories) {
        this.applications=applications; this.documents=documents; this.users=users; this.cipher=cipher; this.storage=storage; this.shops=shops; this.ownerAccounts=ownerAccounts; this.categories=categories;
    }

    @Transactional
    public MerchantDtos.ApplicationResponse saveShop(UUID userId, MerchantDtos.ShopRequest request) {
        var application = getOrCreate(userId);
        editable(application);
        if (applications.existsByShopNameIgnoreCaseAndIdNot(request.shopName().trim(), application.getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_EXISTS", "ชื่อร้านนี้มีในระบบแล้ว");
        }
        categories.requireActiveOrCurrent(request.category(), application.getCategory());
        application.updateShop(request);
        try {
            return response(applications.saveAndFlush(application));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_EXISTS", "ชื่อร้านนี้มีในระบบแล้ว");
        }
    }

    @Transactional
    public MerchantDtos.ApplicationResponse saveOwner(UUID userId, MerchantDtos.OwnerRequest request) {
        var application=getOrCreate(userId); editable(application);
        application.attachOwner(ownerAccounts.upsert(userId, request));
        return response(applications.save(application));
    }

    @Transactional
    public MerchantDtos.ApplicationResponse attachOwner(UUID userId, UUID ownerId) {
        var application = getOrCreate(userId);
        editable(application);
        application.attachOwner(ownerAccounts.requireOwned(userId, ownerId));
        return response(applications.save(application));
    }

    @Transactional
    public MerchantDtos.ApplicationResponse upload(UUID userId, DocumentType type, MultipartFile file) {
        var application=getOrCreate(userId); editable(application);
        var saved=storage.store(application.getId(), file, type.name());
        var previous=documents.findByApplication_IdAndType(application.getId(), type).orElse(null);
        try {
            if (previous != null) {
                documents.delete(previous);
                documents.flush();
            }
            documents.save(new MerchantDocument(application,type,saved.originalName(),saved.storedName(),saved.path(),saved.contentType(),saved.size(),saved.sha256()));
            if (type == DocumentType.LOGO) application.setLogoPath(saved.path());
            applications.save(application);
        } catch (RuntimeException exception) {
            storage.deleteQuietly(saved.path());
            throw exception;
        }
        if (previous != null) storage.deleteQuietly(previous.getStoragePath());
        return response(application);
    }

    @Transactional
    public MerchantDtos.ApplicationResponse get(UUID userId) { return response(getOrCreate(userId)); }

    @Transactional
    public MerchantDtos.ApplicationResponse openAnotherShop(UUID userId, UUID ownerId) {
        var owner = ownerAccounts.requireOwned(userId, ownerId);
        var draft = getOrCreate(userId);
        editable(draft);
        draft.clearShopDetails();
        draft.attachOwner(owner);
        applications.save(draft);
        documents.findByApplication_IdAndType(draft.getId(), DocumentType.LOGO).ifPresent(existing -> {
            documents.delete(existing);
            documents.flush();
            storage.deleteQuietly(existing.getStoragePath());
        });
        return response(draft);
    }

    @Transactional
    public MerchantDtos.ApplicationResponse submit(UUID userId) {
        var application=getOrCreate(userId); editable(application); validateComplete(application);
        var owner = application.getLinkedOwner();
        if (owner == null || owner.getUser() == null || !userId.equals(owner.getUser().getId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "OWNER_FORBIDDEN", "เจ้าของร้านนี้ไม่ใช่ของผู้ส่งคำขอ");
        }
        var reference="TSF-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase();
        application.submit(reference);
        var saved = applications.save(application);
        shops.openFrom(saved);
        return response(saved);
    }

    private MerchantApplication getOrCreate(UUID userId) {
        return applications.findFirstByUser_IdAndStatusOrderByUpdatedAtDesc(userId, ApplicationStatus.DRAFT)
            .orElseGet(() -> applications.save(new MerchantApplication(
                users.findById(userId).orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,"USER_NOT_FOUND","ไม่พบบัญชีผู้ใช้")))));
    }

    private void validateComplete(MerchantApplication a) {
        if (blank(a.getShopName())||blank(a.getCategory())||a.getSaleType()==null||blank(a.getShopAddress())||blank(a.getShopProvince())||blank(a.getShopDistrict())||blank(a.getShopSubdistrict())||blank(a.getShopPostalCode())) incomplete("กรุณากรอกข้อมูลร้านค้าให้ครบถ้วน");
        if (a.getOwnerType()==null||blank(a.getOwnerFirstName())||blank(a.getOwnerLastName())||blank(a.getCitizenIdEncrypted())||a.getBirthDate()==null||blank(a.getOwnerPhone())||blank(a.getOwnerEmail())||blank(a.getOwnerHouseNo())||blank(a.getOwnerProvince())||blank(a.getOwnerDistrict())||blank(a.getOwnerSubdistrict())||blank(a.getOwnerPostalCode())||blank(a.getBankName())||blank(a.getBankAccountEncrypted())||blank(a.getBankAccountName())) incomplete("กรุณากรอกข้อมูลเจ้าของร้านและข้อมูลรับเงินให้ครบถ้วน");
        if (!hasDocument(a, DocumentType.IDENTITY)) incomplete("กรุณาอัปโหลดเอกสารยืนยันตัวตน");
        if (!hasDocument(a, DocumentType.ADDRESS)) incomplete("กรุณาอัปโหลดเอกสารยืนยันที่อยู่");
        if (a.getOwnerType()==OwnerType.COMPANY && !hasDocument(a, DocumentType.BUSINESS)) incomplete("กรุณาอัปโหลดเอกสารยืนยันธุรกิจ");
    }

    private MerchantDtos.ApplicationResponse response(MerchantApplication a) {
        var docs=documents.findByApplication_Id(a.getId()).stream().map(MerchantDtos.DocumentResponse::from).toList();
        var ownerId = a.getLinkedOwner() == null ? null : a.getLinkedOwner().getId();
        return new MerchantDtos.ApplicationResponse(a.getId(),a.getStatus(),a.getReferenceNo(),a.getShopName(),a.getShopNameEn(),a.getShopDescription(),a.getCategory(),a.getSaleType(),a.getWebsite(),a.getLineId(),a.getFacebookUrl(),a.getInstagramUrl(),a.getTiktokUrl(),a.getLogoPath(),a.getShopAddress(),a.getShopProvince(),a.getShopDistrict(),a.getShopSubdistrict(),a.getShopPostalCode(),a.getOwnerType(),a.getOwnerTitle(),a.getOwnerFirstName(),a.getOwnerLastName(),cipher.mask(a.getCitizenIdEncrypted()),a.getBirthDate(),a.getNationality(),a.getOwnerPhone(),a.getOwnerEmail(),a.getOwnerHouseNo(),a.getOwnerVillageNo(),a.getOwnerBuilding(),a.getOwnerAlley(),a.getOwnerRoad(),a.getOwnerProvince(),a.getOwnerDistrict(),a.getOwnerSubdistrict(),a.getOwnerPostalCode(),a.getBankName(),cipher.mask(a.getBankAccountEncrypted()),a.getBankAccountName(),docs,a.getSubmittedAt(),shops.pathFor(a.getId()),ownerId);
    }

    private boolean hasDocument(MerchantApplication application, DocumentType type) {
        if (documents.findByApplication_IdAndType(application.getId(), type).isPresent()) return true;
        var owner = application.getLinkedOwner();
        return owner != null && documents.existsByApplication_LinkedOwner_IdAndType(owner.getId(), type);
    }

    private void editable(MerchantApplication a){try{a.ensureEditable();}catch(IllegalStateException e){throw new AuthException(HttpStatus.CONFLICT,"APPLICATION_ALREADY_SUBMITTED",e.getMessage());}}
    private void incomplete(String message){throw new AuthException(HttpStatus.BAD_REQUEST,"APPLICATION_INCOMPLETE",message);}
    private static boolean blank(String value){return value==null||value.isBlank();}
}
