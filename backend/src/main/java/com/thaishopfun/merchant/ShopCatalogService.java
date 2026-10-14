package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.translation.ContentTranslationBridge;
import com.thaishopfun.translation.ContentTranslationIndex;
import com.thaishopfun.translation.TranslationEntityType;
import com.thaishopfun.translation.TranslationService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ShopCatalogService {
    private final ShopRepository shops;
    private final ShopMemberRepository members;
    private final MerchantStorageService storage;
    private final TranslationService translations;
    private final ContentTranslationBridge translationBridge;
    private final AuthorizationService authorization;

    public ShopCatalogService(
        ShopRepository shops,
        ShopMemberRepository members,
        MerchantStorageService storage,
        TranslationService translations,
        ContentTranslationBridge translationBridge,
        AuthorizationService authorization
    ) {
        this.shops = shops;
        this.members = members;
        this.storage = storage;
        this.translations = translations;
        this.translationBridge = translationBridge;
        this.authorization = authorization;
    }

    @Transactional
    public void openFrom(MerchantApplication application) {
        if (shops.findByApplication_Id(application.getId()).isPresent()) return;
        var owner = application.getLinkedOwner();
        if (owner == null) throw new AuthException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED", "กรุณาระบุเจ้าของร้านก่อนเปิดร้าน");
        var shop = shops.saveAndFlush(new Shop(application, "tmp-" + UUID.randomUUID().toString().substring(0, 12), owner));
        // V1 invariant (deferred trigger): a non-closed shop needs exactly one ACTIVE STORE_OWNER member.
        members.save(ShopMember.storeOwner(shop.getUuid(), shop.getUser().getId()));
        shop.assignSlug(uniqueSlug(ShopSlugs.base(application.getShopNameEn(), application.getShopName(), shop.getId())));
        translationBridge.onShopSaved(shop, application.getShopNameEn());
    }

    @Transactional(readOnly = true)
    public String pathFor(UUID applicationId) {
        return shops.findByApplication_Id(applicationId).map(Shop::path).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.PublicShopResponse> list() {
        var approved = shops.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(shop -> shop.getStatus() == ShopStatus.APPROVED)
            .toList();
        var index = translations.loadIndex(
            TranslationEntityType.SHOP,
            approved.stream().map(shop -> String.valueOf(shop.getId())).toList()
        );
        return approved.stream().map(shop -> toPublic(shop, index)).toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.PublicShopResponse publicView(String slug, UUID viewerId) {
        var shop = shops.findBySlug(slug).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        if (shop.getStatus() != ShopStatus.APPROVED && !canPreview(shop, viewerId)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
        }
        var index = translations.loadIndex(TranslationEntityType.SHOP, List.of(String.valueOf(shop.getId())));
        return toPublic(shop, index);
    }

    /**
     * A shop that is not APPROVED is visible only to its owner, its active members and platform staff with
     * PLATFORM_SHOP_READ (super admin included), all read from the role tables (no JWT role claim).
     */
    private boolean canPreview(Shop shop, UUID viewerId) {
        if (viewerId == null) return false;
        if (shop.getUser() != null && viewerId.equals(shop.getUser().getId())) return true;
        if (authorization.grants(viewerId).shopRoles().containsKey(shop.getId())) return true;
        return authorization.hasPermission(viewerId, "PLATFORM_SHOP_READ");
    }

    @Transactional(readOnly = true)
    public Path logoFile(String slug) {
        var shop = shops.findBySlug(slug).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        return storage.openPublicImage(shop.getLogoPath()).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "LOGO_NOT_FOUND", "ไม่พบโลโก้ร้าน"));
    }

    @Transactional(readOnly = true)
    public Path coverFile(String slug) {
        var shop = shops.findBySlug(slug).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        return storage.openPublicImage(shop.getCoverPath()).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "COVER_NOT_FOUND", "ไม่พบรูปปกร้าน"));
    }

    private MerchantDtos.PublicShopResponse toPublic(Shop shop, ContentTranslationIndex index) {
        var entityId = String.valueOf(shop.getId());
        var logo = storage.openPublicImage(shop.getLogoPath()).isPresent() ? "/api/shops/" + shop.getSlug() + "/logo" : null;
        var cover = storage.openPublicImage(shop.getCoverPath()).isEmpty() ? null
            : "/api/shops/" + shop.getSlug() + "/cover?v=" + shop.getUpdatedAt().toEpochMilli();
        return new MerchantDtos.PublicShopResponse(
            shop.getId(),
            shop.getName(),
            index.resolve(entityId, "name", "en", shop.getName(), shop.getNameEn()),
            index.resolve(entityId, "name", "zh", shop.getName(), null),
            shop.getDescription(),
            index.resolve(entityId, "description", "en", shop.getDescription(), null),
            index.resolve(entityId, "description", "zh", shop.getDescription(), null),
            shop.getCategory(),
            shop.getAddress(),
            shop.getProvince(),
            shop.getDistrict(),
            shop.getSubdistrict(),
            shop.getPostalCode(),
            shop.getSlug(),
            shop.path(),
            shop.getStatus(),
            logo,
            cover
        );
    }

    private String uniqueSlug(String base) {
        var candidate = base;
        for (int suffix = 2; shops.existsBySlug(candidate); suffix++) {
            var ending = "-" + suffix;
            var room = Math.max(1, 80 - ending.length());
            var stem = base.length() > room ? base.substring(0, room).replaceAll("-+$", "") : base;
            candidate = stem + ending;
        }
        return candidate;
    }
}
