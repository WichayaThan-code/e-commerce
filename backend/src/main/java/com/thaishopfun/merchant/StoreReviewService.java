package com.thaishopfun.merchant;

import com.thaishopfun.admin.SuspensionReasonCodes;
import com.thaishopfun.audit.PlatformAuditService;
import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class StoreReviewService {
    private final ShopRepository shops;
    private final MerchantDocumentRepository documents;
    private final MerchantStorageService storage;
    private final ShopReinstatementService reinstatements;
    private final AuthorizationService authorization;
    private final ShopSuspensionService suspensions;
    private final PlatformAuditService platformAudit;

    public StoreReviewService(ShopRepository shops, MerchantDocumentRepository documents, MerchantStorageService storage, ShopReinstatementService reinstatements, AuthorizationService authorization,
                              ShopSuspensionService suspensions, PlatformAuditService platformAudit) {
        this.shops = shops;
        this.documents = documents;
        this.storage = storage;
        this.reinstatements = reinstatements;
        this.authorization = authorization;
        this.suspensions = suspensions;
        this.platformAudit = platformAudit;
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.AdminStoreApplicationResponse> list() {
        return shops.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toAdmin).toList();
    }

    /**
     * Returns full owner phone/email for application review. Audits {@code CONTACT_REVEALED} without storing raw values.
     */
    @Transactional
    public MerchantDtos.ContactRevealResponse revealContact(Long shopId, UUID actorId) {
        var shop = shop(shopId);
        var owner = shop.getOwner();
        var application = shop.getApplication();
        var phone = owner == null ? application.getOwnerPhone() : owner.getPhone();
        var email = owner == null ? application.getOwnerEmail() : owner.getEmail();
        platformAudit.record(PlatformAuditService.Entry.platform(actorId, "CONTACT_REVEALED", "SHOP", shop.getUuid())
            .shop(shop.getUuid())
            .permission("PLATFORM_CONTACT_REVEAL")
            .change(Map.of("contact", "masked"), Map.of("contact", "revealed", "fields", "phone,email")));
        return new MerchantDtos.ContactRevealResponse(phone, email);
    }

    @Transactional(readOnly = true)
    public byte[] export() {
        var headers = List.of("shop_id", "ชื่อร้าน", "เจ้าของร้าน", "เบอร์โทร", "อีเมล", "จังหวัด", "หมวดหมู่", "ที่อยู่", "สถานะ", "วันที่สมัคร", "เว็บไซต์");
        var rows = list().stream().map(item -> List.of(
            String.valueOf(item.shopId()), item.shopName(), item.ownerName(), item.phone(), item.email(),
            item.province(), item.category(), item.address(), statusLabel(item.status()),
            String.valueOf(item.appliedAt()), item.website()
        )).toList();
        return SpreadsheetFile.xlsx(headers, rows);
    }

    @Transactional(readOnly = true)
    public Path documentFile(Long shopId, UUID documentId) {
        var shop = shop(shopId);
        var document = documents.findById(documentId)
            .filter(item -> item.getApplication().getId().equals(shop.getApplication().getId()))
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "ไม่พบเอกสารของร้านนี้"));
        return storage.openStoredFile(document.getStoragePath())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "ไม่พบไฟล์เอกสาร"));
    }

    @Transactional(readOnly = true)
    public String documentFilename(Long shopId, UUID documentId) {
        var shop = shop(shopId);
        return documents.findById(documentId)
            .filter(item -> item.getApplication().getId().equals(shop.getApplication().getId()))
            .map(MerchantDocument::getOriginalFilename)
            .orElse("document");
    }

    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse approve(Long shopId) {
        return approve(shopId, null);
    }

    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse approve(Long shopId, UUID actorId) {
        var shop = shop(shopId);
        if (shop.getStatus() == ShopStatus.APPROVED || shop.getStatus() == ShopStatus.SUSPENDED) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_ALREADY_APPROVED", "ร้านนี้ได้รับการอนุมัติแล้ว ไม่สามารถอนุมัติซ้ำได้");
        }
        suspensions.requireMerchantActive(shop);
        var before = shop.getStatus();
        shop.approve(); // the owner's STORE_OWNER membership (shop_members) already exists; it is the source of truth
        suspensions.recordReview(actorId, "SHOP_APPROVED", shop, before);
        return toAdmin(shop);
    }

    /** Pre-existing endpoint: 409 when not APPROVED; reasonCode required + allowlisted; reasonText optional ≤500. */
    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse suspend(Long shopId, UUID actorId, String reasonCode, String reason) {
        var code = SuspensionReasonCodes.requireSuspend(reasonCode);
        var shop = shop(shopId);
        suspensions.suspendShop(shop, actorId, code, clean(reason), false);
        return toAdmin(shop);
    }

    @Transactional
    public MerchantDtos.BulkShopResponse approveMany(List<Long> shopIds) {
        return approveMany(shopIds, null);
    }

    @Transactional
    public MerchantDtos.BulkShopResponse approveMany(List<Long> shopIds, UUID actorId) {
        return applyMany(shopIds, ShopStatus.PENDING_REVIEW, shop -> {
            if (suspensions.merchantSuspended(shop)) return false;
            shop.approve();
            suspensions.recordReview(actorId, "SHOP_APPROVED", shop, ShopStatus.PENDING_REVIEW);
            return true;
        });
    }

    @Transactional
    public MerchantDtos.BulkShopResponse rejectMany(List<Long> shopIds) {
        return rejectMany(shopIds, null);
    }

    @Transactional
    public MerchantDtos.BulkShopResponse rejectMany(List<Long> shopIds, UUID actorId) {
        return applyMany(shopIds, ShopStatus.PENDING_REVIEW, shop -> {
            shop.reject();
            suspensions.recordReview(actorId, "SHOP_REJECTED", shop, ShopStatus.PENDING_REVIEW);
            return true;
        });
    }

    @Transactional
    public MerchantDtos.BulkShopResponse suspendMany(List<Long> shopIds, UUID actorId, String reasonCode, String reason) {
        var code = SuspensionReasonCodes.requireSuspend(reasonCode);
        var correlationId = UUID.randomUUID().toString();
        var text = clean(reason);
        return applyMany(shopIds, ShopStatus.APPROVED,
            shop -> suspensions.suspendShop(shop, actorId, code, text, false, correlationId));
    }

    @Transactional
    public MerchantDtos.BulkShopResponse reactivateMany(List<Long> shopIds, UUID actorId, String reasonCode, String reason) {
        var correlationId = UUID.randomUUID().toString();
        var code = SuspensionReasonCodes.resolveRestore(reasonCode, ShopSuspensionService.DEFAULT_RESTORE_CODE);
        var text = clean(reason);
        return applyMany(shopIds, ShopStatus.SUSPENDED, shop -> {
            if (suspensions.merchantSuspended(shop)) return false;
            return suspensions.reactivateShop(shop, actorId, code, text, false, correlationId);
        });
    }

    private MerchantDtos.BulkShopResponse applyMany(List<Long> shopIds, ShopStatus required, java.util.function.Predicate<Shop> action) {
        var updated = new java.util.ArrayList<MerchantDtos.AdminStoreApplicationResponse>();
        var skipped = 0;
        for (var shopId : shopIds.stream().distinct().toList()) {
            var shop = shops.findByShopId(shopId).orElse(null);
            if (shop == null || shop.getStatus() != required) {
                skipped++;
                continue;
            }
            if (!action.test(shop)) {
                skipped++;
                continue;
            }
            updated.add(toAdmin(shop));
        }
        return new MerchantDtos.BulkShopResponse(updated.size(), skipped, updated);
    }

    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse reject(Long shopId) {
        return reject(shopId, null);
    }

    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse reject(Long shopId, UUID actorId) {
        var shop = shop(shopId);
        if (shop.getStatus() != ShopStatus.REJECTED) {
            var before = shop.getStatus();
            shop.reject();
            suspensions.recordReview(actorId, "SHOP_REJECTED", shop, before);
        }
        return toAdmin(shop);
    }

    /** Pre-existing endpoint: 409 when not SUSPENDED (or the merchant is suspended); reasonText optional. */
    @Transactional
    public MerchantDtos.AdminStoreApplicationResponse reactivate(Long shopId, UUID actorId, String reasonCode, String reason) {
        var shop = shop(shopId);
        var code = SuspensionReasonCodes.resolveRestore(reasonCode, ShopSuspensionService.DEFAULT_RESTORE_CODE);
        suspensions.reactivateShop(shop, actorId, code, clean(reason), false);
        return toAdmin(shop);
    }

    private static String clean(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.StoreAccessResponse access(UUID userId, Long shopId, String permission) {
        if (shopId != null && authorization.isSuperAdmin(userId)) {
            var shop = shop(shopId);
            return new MerchantDtos.StoreAccessResponse(shop.getStatus() == ShopStatus.APPROVED, true, shop.getStatus(), shop.getName(), shop.getSlug(), shop.getId());
        }
        var allowed = authorization.shopsWithPermission(userId, permission);
        var owned = shops.findMemberShops(userId).stream().filter(shop -> allowed.contains(shop.getId())).toList();
        if (owned.isEmpty()) return new MerchantDtos.StoreAccessResponse(false, false, null, null, null, null);
        Shop shop;
        if (shopId == null) {
            shop = owned.stream().filter(item -> item.getStatus() == ShopStatus.APPROVED).findFirst().orElse(owned.get(0));
        } else {
            shop = owned.stream().filter(item -> item.getId().equals(shopId)).findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        }
        return new MerchantDtos.StoreAccessResponse(shop.getStatus() == ShopStatus.APPROVED, true, shop.getStatus(), shop.getName(), shop.getSlug(), shop.getId());
    }

    private Shop shop(Long shopId) {
        return shops.findByShopId(shopId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบใบสมัครร้านค้า"));
    }

    private MerchantDtos.AdminStoreApplicationResponse toAdmin(Shop shop) {
        var owner = shop.getOwner();
        var application = shop.getApplication();
        var ownerName = owner == null
            ? join(application.getOwnerTitle(), application.getOwnerFirstName(), application.getOwnerLastName())
            : join(owner.getTitle(), owner.getFirstName(), owner.getLastName());
        var phone = com.thaishopfun.admin.AdminContactMask.phone(owner == null ? application.getOwnerPhone() : owner.getPhone());
        var email = com.thaishopfun.admin.AdminContactMask.email(owner == null ? application.getOwnerEmail() : owner.getEmail());
        var otherShops = owner == null ? List.<MerchantDtos.OwnerShopRef>of() : shops.findByOwner_IdOrderByShopIdAsc(owner.getId()).stream()
            .filter(item -> !item.getId().equals(shop.getId()))
            .map(item -> new MerchantDtos.OwnerShopRef(item.getId(), item.getName(), item.getStatus()))
            .toList();
        var logo = storage.openPublicImage(shop.getLogoPath()).isPresent() ? "/api/shops/" + shop.getSlug() + "/logo" : null;
        var docs = documents.findByApplication_Id(application.getId()).stream()
            .map(document -> new MerchantDtos.AdminDocumentResponse(document.getId(), document.getType().name(), label(document.getType()), document.getOriginalFilename(), document.getFileSize()))
            .toList();
        return new MerchantDtos.AdminStoreApplicationResponse(
            shop.getId(), shop.getName(), ownerName, phone, email, shop.getProvince(), shop.getCategory(),
            address(shop), shop.getDescription() == null ? "" : shop.getDescription(), shop.getWebsite() == null ? "" : shop.getWebsite(),
            shop.getLineId() == null ? "" : shop.getLineId(), shop.getFacebookUrl() == null ? "" : shop.getFacebookUrl(),
            shop.getInstagramUrl() == null ? "" : shop.getInstagramUrl(), shop.getTiktokUrl() == null ? "" : shop.getTiktokUrl(),
            shop.getCreatedAt(), shop.getUpdatedAt(), shop.getStatus(), shop.getSlug(), logo, docs,
            reinstatements.pendingMessage(shop.getId()),
            otherShops
        );
    }

    private static String address(Shop shop) {
        return Stream.of(shop.getAddress(), shop.getSubdistrict(), shop.getDistrict(), shop.getProvince(), shop.getPostalCode())
            .filter(value -> value != null && !value.isBlank())
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(value -> value != null && !value.isBlank()).reduce((left, right) -> left + " " + right).orElse("");
    }

    private static String statusLabel(ShopStatus status) {
        return switch (status) {
            case PENDING_REVIEW -> "รออนุมัติ";
            case APPROVED -> "ใช้งาน";
            case REJECTED -> "ปฏิเสธ";
            case SUSPENDED -> "ถูกระงับ";
        };
    }

    private static String label(DocumentType type) {
        return switch (type) {
            case IDENTITY -> "สำเนาบัตรประชาชน";
            case PORTRAIT -> "รูปถ่ายเจ้าของร้าน";
            case ADDRESS -> "หลักฐานที่อยู่ร้าน";
            case BUSINESS -> "เอกสารยืนยันธุรกิจ";
            case LOGO -> "โลโก้ร้านค้า";
        };
    }
}
