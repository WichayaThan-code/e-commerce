package com.thaishopfun.merchant;

import com.thaishopfun.admin.notification.AdminNotificationService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import com.thaishopfun.common.IntegritySqlStates;
import com.thaishopfun.merchant.delivery.ShopLifecycleEmailPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
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
    private final MerchantDraftFactory drafts;
    private final ShopRepository shopRepository;
    private final AdminNotificationService adminNotifications;
    private final ShopLifecycleEmailPublisher shopMails;

    public MerchantApplicationService(MerchantApplicationRepository applications, MerchantDocumentRepository documents,
                                      UserAccountRepository users, SensitiveDataCipher cipher, MerchantStorageService storage,
                                      ShopCatalogService shops, OwnerAccountService ownerAccounts,
                                      ProductCategoryService categories, MerchantDraftFactory drafts,
                                      ShopRepository shopRepository, AdminNotificationService adminNotifications,
                                      ShopLifecycleEmailPublisher shopMails) {
        this.applications=applications; this.documents=documents; this.users=users; this.cipher=cipher; this.storage=storage; this.shops=shops; this.ownerAccounts=ownerAccounts; this.categories=categories; this.drafts=drafts;
        this.shopRepository = shopRepository;
        this.adminNotifications = adminNotifications;
        this.shopMails = shopMails;
    }

    @Transactional
    public MerchantDtos.ApplicationResponse saveShop(UUID userId, MerchantDtos.ShopRequest request) {
        var application = getOrCreate(userId);
        editable(application);
        if (applications.existsByShopNameIgnoreCaseAndIdNot(request.shopName().trim(), application.getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_EXISTS", "ชื่อร้านนี้ถูกใช้แล้ว ลองเปลี่ยนชื่อ", "shopName");
        }
        categories.requireActiveOrCurrent(request.category(), application.getCategory());
        application.updateShop(request);
        try {
            return response(applications.saveAndFlush(application));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            // Only map unique-shop-name collisions; other integrity errors (e.g. 22001 value too long) rethrow.
            if (IntegritySqlStates.isUniqueViolation(exception) && IntegritySqlStates.mentions(exception, "shop_name", "uq_merchant_applications_shop_name")) {
                throw new AuthException(HttpStatus.CONFLICT, "SHOP_EXISTS", "ชื่อร้านนี้ถูกใช้แล้ว ลองเปลี่ยนชื่อ", "shopName");
            }
            throw exception;
        }
    }

    /**
     * Read-only twin of the saveShop uniqueness rule (trimmed, case-insensitive, every application incl. existing shops
     * and pending ones). The caller's own current draft never counts, so re-checking an unchanged name stays available.
     */
    @Transactional(readOnly = true)
    public MerchantDtos.ShopNameCheckResponse checkShopName(UUID userId, String name) {
        var trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return new MerchantDtos.ShopNameCheckResponse(false);
        var draft = findDraft(userId);
        var taken = draft == null
            ? applications.existsByShopNameIgnoreCase(trimmed)
            : applications.existsByShopNameIgnoreCaseAndIdNot(trimmed, draft.getId());
        return new MerchantDtos.ShopNameCheckResponse(!taken);
    }

    @Transactional(readOnly = true)
    public void validateOwner(UUID userId, MerchantDtos.OwnerRequest request) {
        ownerAccounts.assertCitizenAvailable(userId, request.citizenId());
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

    /**
     * Opens the caller's own draft document of the given type. Missing or foreign documents both look like 404.
     * File bytes are resolved through {@link MerchantStorageService#openStoredFile(String)} (same as admin download).
     */
    @Transactional(readOnly = true)
    public OwnDocumentFile openOwnDocument(UUID userId, DocumentType type) {
        var application = findDraft(userId);
        if (application == null || application.getUser() == null || !userId.equals(application.getUser().getId())) {
            throw documentNotFound();
        }
        var document = documents.findByApplication_IdAndType(application.getId(), type).orElseThrow(this::documentNotFound);
        if (document.getApplication() == null || !application.getId().equals(document.getApplication().getId())) {
            throw documentNotFound();
        }
        var path = storage.openStoredFile(document.getStoragePath()).orElseThrow(this::documentNotFound);
        return new OwnDocumentFile(path, document.getContentType(), document.getOriginalFilename());
    }

    public record OwnDocumentFile(Path path, String contentType, String originalFilename) {}

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
        shopRepository.findByApplication_Id(saved.getId()).ifPresent(shop -> {
            adminNotifications.publishShopApplication(shop.getId(), shop.getName());
            shopMails.applicationSubmitted(shop);
        });
        return response(saved);
    }

    /**
     * The user's current DRAFT (latest one), or a new DRAFT. A user may hold several submitted/approved/rejected
     * applications (one per shop) but only one DRAFT at a time, so a new application can always be started
     * after REJECTED/WITHDRAWN. Concurrent first requests (e.g. two GET /draft) cannot create two drafts or 500:
     * the loser of the insert race re-reads the winner's draft.
     */
    private MerchantApplication getOrCreate(UUID userId) {
        var existing = findDraft(userId);
        if (existing != null) {
            ensureOwnerPrefill(existing, userId);
            return existing;
        }
        try {
            drafts.createDraft(userId);
        } catch (org.springframework.dao.DataIntegrityViolationException raced) {
            // another request created the draft first; fall through and read it
        }
        var draft = findDraft(userId);
        if (draft == null) throw new AuthException(HttpStatus.CONFLICT, "DRAFT_CONFLICT", "กำลังสร้างใบสมัคร กรุณาลองใหม่อีกครั้ง");
        ensureOwnerPrefill(draft, userId);
        return draft;
    }

    /**
     * Returning merchants: copy personal + bank details from their existing owner profile onto the draft
     * so the second shop does not require re-entry (documents are also reused via {@link #existingOwner}).
     */
    private void ensureOwnerPrefill(MerchantApplication draft, UUID userId) {
        if (draft == null || draft.getLinkedOwner() != null) return;
        ownerAccounts.primaryOwned(userId).ifPresent(owner -> {
            draft.attachOwner(owner);
            applications.save(draft);
        });
    }

    private MerchantApplication findDraft(UUID userId) {
        return applications.findFirstByUser_IdAndStatusOrderByUpdatedAtDesc(userId, ApplicationStatus.DRAFT).orElse(null);
    }

    private void validateComplete(MerchantApplication a) {
        if (blank(a.getShopName())||blank(a.getCategory())||a.getSaleType()==null||blank(a.getShopAddress())||blank(a.getShopProvince())||blank(a.getShopDistrict())||blank(a.getShopSubdistrict())||blank(a.getShopPostalCode())) incomplete("กรุณากรอกข้อมูลร้านค้าให้ครบถ้วน");
        if (a.getOwnerType()==null||blank(a.getOwnerFirstName())||blank(a.getOwnerLastName())||blank(a.getCitizenIdEncrypted())||a.getBirthDate()==null||blank(a.getOwnerPhone())||blank(a.getOwnerEmail())||blank(a.getOwnerHouseNo())||blank(a.getOwnerProvince())||blank(a.getOwnerDistrict())||blank(a.getOwnerSubdistrict())||blank(a.getOwnerPostalCode())||blank(a.getBankName())||blank(a.getBankAccountEncrypted())||blank(a.getBankAccountName())) incomplete("กรุณากรอกข้อมูลเจ้าของร้านและข้อมูลรับเงินให้ครบถ้วน");
        // Owner already runs another shop: identity documents were verified with that shop (decided server-side).
        boolean reuse = existingOwner(a);
        if (!reuse && !hasDocument(a, DocumentType.IDENTITY)) incomplete("กรุณาอัปโหลดเอกสารยืนยันตัวตน");
        if (!reuse && !hasDocument(a, DocumentType.ADDRESS)) incomplete("กรุณาอัปโหลดเอกสารยืนยันที่อยู่");
        if (!reuse && a.getOwnerType()==OwnerType.COMPANY && !hasDocument(a, DocumentType.BUSINESS)) incomplete("กรุณาอัปโหลดเอกสารยืนยันธุรกิจ");
    }

    private MerchantDtos.ApplicationResponse response(MerchantApplication a) {
        var docs=documents.findByApplication_Id(a.getId()).stream().map(MerchantDtos.DocumentResponse::from).toList();
        var ownerId = a.getLinkedOwner() == null ? null : a.getLinkedOwner().getId();
        return new MerchantDtos.ApplicationResponse(a.getId(),a.getStatus(),a.getReferenceNo(),a.getShopName(),a.getShopNameEn(),a.getShopDescription(),a.getCategory(),a.getSaleType(),a.getWebsite(),a.getLineId(),a.getFacebookUrl(),a.getInstagramUrl(),a.getTiktokUrl(),a.getLogoPath(),a.getShopAddress(),a.getShopProvince(),a.getShopDistrict(),a.getShopSubdistrict(),a.getShopPostalCode(),a.getOwnerType(),a.getOwnerTitle(),a.getOwnerFirstName(),a.getOwnerLastName(),cipher.mask(a.getCitizenIdEncrypted()),a.getBirthDate(),a.getNationality(),a.getOwnerPhone(),a.getOwnerEmail(),a.getOwnerHouseNo(),a.getOwnerVillageNo(),a.getOwnerBuilding(),a.getOwnerAlley(),a.getOwnerRoad(),a.getOwnerProvince(),a.getOwnerDistrict(),a.getOwnerSubdistrict(),a.getOwnerPostalCode(),a.getBankName(),cipher.mask(a.getBankAccountEncrypted()),a.getBankAccountName(),docs,a.getSubmittedAt(),shops.pathFor(a.getId()),ownerId,existingOwner(a));
    }

    /**
     * True when the linked owner already has another shop that is not rejected (pending, approved or suspended).
     * Such an owner opens further shops without re-entering owner info or re-uploading identity documents.
     */
    private boolean existingOwner(MerchantApplication application) {
        var owner = application.getLinkedOwner();
        if (owner == null) return false;
        return shopRepository.findByOwner_IdOrderByShopIdAsc(owner.getId()).stream()
            .anyMatch(shop -> shop.getStatus() != ShopStatus.REJECTED
                && (shop.getApplication() == null || !application.getId().equals(shop.getApplication().getId())));
    }

    private boolean hasDocument(MerchantApplication application, DocumentType type) {
        if (documents.findByApplication_IdAndType(application.getId(), type).isPresent()) return true;
        var owner = application.getLinkedOwner();
        return owner != null && documents.existsByApplication_LinkedOwner_IdAndType(owner.getId(), type);
    }

    private void editable(MerchantApplication a){try{a.ensureEditable();}catch(IllegalStateException e){throw new AuthException(HttpStatus.CONFLICT,"APPLICATION_ALREADY_SUBMITTED",e.getMessage());}}
    private void incomplete(String message){throw new AuthException(HttpStatus.BAD_REQUEST,"APPLICATION_INCOMPLETE",message);}
    private AuthException documentNotFound(){return new AuthException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","ไม่พบเอกสาร");}
    private static boolean blank(String value){return value==null||value.isBlank();}
}
