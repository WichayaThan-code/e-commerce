package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
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

    public ShopCatalogService(ShopRepository shops, ShopMemberRepository members, MerchantStorageService storage) {
        this.shops = shops;
        this.members = members;
        this.storage = storage;
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
    }

    @Transactional(readOnly = true)
    public String pathFor(UUID applicationId) {
        return shops.findByApplication_Id(applicationId).map(Shop::path).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.PublicShopResponse> list() {
        return shops.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(shop -> shop.getStatus() == ShopStatus.APPROVED)
            .map(this::toPublic)
            .toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.PublicShopResponse publicView(String slug) {
        var shop = shops.findBySlug(slug).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        return toPublic(shop);
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

    private MerchantDtos.PublicShopResponse toPublic(Shop shop) {
        var logo = storage.openPublicImage(shop.getLogoPath()).isPresent() ? "/api/shops/" + shop.getSlug() + "/logo" : null;
        var cover = storage.openPublicImage(shop.getCoverPath()).isEmpty() ? null
            : "/api/shops/" + shop.getSlug() + "/cover?v=" + shop.getUpdatedAt().toEpochMilli();
        return new MerchantDtos.PublicShopResponse(shop.getId(), shop.getName(), shop.getNameEn(), shop.getDescription(), shop.getCategory(), shop.getAddress(), shop.getProvince(), shop.getDistrict(), shop.getSubdistrict(), shop.getPostalCode(), shop.getSlug(), shop.path(), shop.getStatus(), logo, cover);
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
