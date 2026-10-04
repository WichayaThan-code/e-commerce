package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantShopSettingsService {
    private final ShopRepository shops;
    private final MerchantApplicationRepository applications;
    private final MerchantStorageService storage;
    private final ProductCategoryService categories;

    public MerchantShopSettingsService(ShopRepository shops, MerchantApplicationRepository applications, MerchantStorageService storage, ProductCategoryService categories) {
        this.shops = shops;
        this.applications = applications;
        this.storage = storage;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.MerchantShopSummary> list(UUID userId) {
        return shops.findByUser_IdOrderByShopIdDesc(userId).stream()
            .map(shop -> new MerchantDtos.MerchantShopSummary(shop.getId(), shop.getName(), shop.getStatus(), shop.getSlug(), shop.getOwner() == null ? null : shop.getOwner().getId()))
            .toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.MerchantSettingsResponse get(UUID userId, Long shopId) {
        var shop = ownedShop(userId, shopId);
        var owner = shop.getOwner();
        var phone = owner == null ? shop.getApplication().getOwnerPhone() : owner.getPhone();
        var email = owner == null ? shop.getApplication().getOwnerEmail() : owner.getEmail();
        return new MerchantDtos.MerchantSettingsResponse(
            shop.getId(),
            shop.getStatus(),
            shop.getStatus() == ShopStatus.APPROVED,
            shop.getName(),
            shop.getNameEn(),
            shop.getDescription(),
            shop.getCategory(),
            phone,
            email,
            shop.getWebsite(),
            shop.getLineId(),
            shop.getFacebookUrl(),
            shop.getInstagramUrl(),
            shop.getTiktokUrl(),
            shop.getAddress(),
            shop.getProvince(),
            shop.getDistrict(),
            shop.getSubdistrict(),
            shop.getPostalCode(),
            coverUrl(shop),
            logoUrl(shop),
            shop.getSlug(),
            shop.getCreatedAt(),
            openingHours(shop)
        );
    }

    @Transactional
    public MerchantDtos.MerchantSettingsResponse updateHours(UUID userId, Long shopId, MerchantDtos.OpeningHoursRequest request) {
        var shop = ownedShop(userId, shopId);
        for (var day : request.days()) {
            if (day.open() && day.start().compareTo(day.end()) >= 0) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_HOURS", "เวลาปิดต้องอยู่หลังเวลาเปิด");
            }
        }
        shop.setOpeningHours(encodeHours(request.days()));
        shops.save(shop);
        return get(userId, shop.getId());
    }

    @Transactional
    public MerchantDtos.MerchantSettingsResponse uploadCover(UUID userId, Long shopId, MultipartFile file) {
        var shop = ownedShop(userId, shopId);
        var saved = storage.store(shop.getApplication().getId(), file, "COVER");
        if (saved.contentType() == null || !saved.contentType().startsWith("image/")) {
            storage.deleteQuietly(saved.path());
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "รูปปกต้องเป็นไฟล์ JPG, PNG หรือ WEBP");
        }
        var previous = shop.getCoverPath();
        shop.setCoverPath(saved.path());
        shops.save(shop);
        if (previous != null && !previous.equals(saved.path())) storage.deleteQuietly(previous);
        return get(userId, shop.getId());
    }

    @Transactional
    public MerchantDtos.MerchantSettingsResponse uploadLogo(UUID userId, Long shopId, MultipartFile file) {
        var shop = ownedShop(userId, shopId);
        var saved = storage.store(shop.getApplication().getId(), file, "LOGO");
        if (saved.contentType() == null || !saved.contentType().startsWith("image/")) {
            storage.deleteQuietly(saved.path());
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "โลโก้ต้องเป็นไฟล์ JPG, PNG หรือ WEBP");
        }
        var previous = shop.getOwnedLogoPath();
        shop.setLogoPath(saved.path());
        shops.save(shop);
        if (previous != null && !previous.equals(saved.path())) storage.deleteQuietly(previous);
        return get(userId, shop.getId());
    }

    private String logoUrl(Shop shop) {
        if (storage.openPublicImage(shop.getLogoPath()).isEmpty()) return null;
        var version = shop.getUpdatedAt() == null ? "" : "?v=" + shop.getUpdatedAt().toEpochMilli();
        return "/api/shops/" + shop.getSlug() + "/logo" + version;
    }

    private List<MerchantDtos.ShopHour> openingHours(Shop shop) {
        var raw = shop.getOpeningHours();
        if (raw == null || raw.isBlank()) return defaultHours();
        var parts = raw.split(";");
        if (parts.length != 7) return defaultHours();
        var days = new ArrayList<MerchantDtos.ShopHour>();
        for (var part : parts) {
            var bits = part.split(",");
            if (bits.length != 3 || !bits[1].matches("^([01]\\d|2[0-3]):[0-5]\\d$") || !bits[2].matches("^([01]\\d|2[0-3]):[0-5]\\d$")) return defaultHours();
            days.add(new MerchantDtos.ShopHour("1".equals(bits[0]), bits[1], bits[2]));
        }
        return days;
    }

    private static String encodeHours(List<MerchantDtos.ShopHour> days) {
        return days.stream().map(day -> (day.open() ? "1" : "0") + "," + day.start() + "," + day.end()).reduce((left, right) -> left + ";" + right).orElse("");
    }

    private static List<MerchantDtos.ShopHour> defaultHours() {
        var days = new java.util.ArrayList<MerchantDtos.ShopHour>();
        for (var index = 0; index < 7; index++) {
            var weekday = index < 5;
            days.add(new MerchantDtos.ShopHour(weekday, weekday ? "08:00" : "09:00", weekday ? "20:00" : "18:00"));
        }
        return days;
    }

    private String coverUrl(Shop shop) {
        if (storage.openPublicImage(shop.getCoverPath()).isEmpty()) return null;
        var version = shop.getUpdatedAt() == null ? "" : "?v=" + shop.getUpdatedAt().toEpochMilli();
        return "/api/shops/" + shop.getSlug() + "/cover" + version;
    }

    @Transactional
    public MerchantDtos.MerchantSettingsResponse update(UUID userId, Long shopId, MerchantDtos.MerchantSettingsUpdateRequest request) {
        var shop = ownedShop(userId, shopId);
        var trimmedName = request.shopName().trim();
        if (applications.existsByShopNameIgnoreCaseAndIdNot(trimmedName, shop.getApplication().getId())) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_EXISTS", "ชื่อร้านนี้มีในระบบแล้ว");
        }
        categories.requireActiveOrCurrent(request.category(), shop.getCategory());
        shop.updateSettings(
            trimmedName,
            trimToNull(request.shopNameEn()),
            trimToNull(request.description()),
            request.category().trim(),
            trimToNull(request.website()),
            trimToNull(request.lineId()),
            trimToNull(request.facebookUrl()),
            trimToNull(request.instagramUrl()),
            trimToNull(request.tiktokUrl()),
            request.address().trim(),
            request.province().trim(),
            request.district().trim(),
            request.subdistrict().trim(),
            request.postalCode().trim()
        );
        var owner = shop.getOwner();
        if (owner != null) {
            owner.updateContact(request.phone().trim(), request.email().trim().toLowerCase());
        }
        var application = shop.getApplication();
        application.syncFromShop(shop, request.phone().trim(), request.email().trim().toLowerCase());
        applications.save(application);
        shops.save(shop);
        return get(userId, shop.getId());
    }

    private Shop ownedShop(UUID userId, Long shopId) {
        var owned = shops.findByUser_IdOrderByShopIdDesc(userId);
        if (owned.isEmpty()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ยังไม่มีร้านค้าที่สมัครไว้");
        }
        if (shopId == null) return owned.get(0);
        return owned.stream().filter(shop -> shop.getId().equals(shopId)).findFirst()
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
