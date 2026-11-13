package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.merchant.plan.PlanQuotaService;
import com.thaishopfun.merchant.theme.ShopThemeService;
import com.thaishopfun.translation.ContentTranslationBridge;
import com.thaishopfun.translation.ContentTranslationIndex;
import com.thaishopfun.translation.TranslationEntityType;
import com.thaishopfun.translation.TranslationService;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
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
    private final ShopThemeService themes;
    private final EntityManager entities;
    private final PlanQuotaService planQuota;

    public ShopCatalogService(
        ShopRepository shops,
        ShopMemberRepository members,
        MerchantStorageService storage,
        TranslationService translations,
        ContentTranslationBridge translationBridge,
        AuthorizationService authorization,
        ShopThemeService themes,
        EntityManager entities,
        PlanQuotaService planQuota
    ) {
        this.shops = shops;
        this.members = members;
        this.storage = storage;
        this.translations = translations;
        this.translationBridge = translationBridge;
        this.authorization = authorization;
        this.themes = themes;
        this.entities = entities;
        this.planQuota = planQuota;
    }

    @Transactional
    public void openFrom(MerchantApplication application) {
        if (shops.findByApplication_Id(application.getId()).isPresent()) return;
        var owner = application.getLinkedOwner();
        if (owner == null) throw new AuthException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED", "กรุณาระบุเจ้าของร้านก่อนเปิดร้าน");
        var shop = shops.saveAndFlush(new Shop(application, "tmp-" + UUID.randomUUID().toString().substring(0, 12), owner));
        // V1 invariant (deferred trigger): a non-closed shop needs exactly one ACTIVE STORE_OWNER member.
        members.save(ShopMember.storeOwner(shop.getUuid(), shop.getUser().getId()));
        planQuota.assignFreeIfMissing(shop);
        shops.save(shop);
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

    /** Marketplace search: APPROVED shops whose name (th/en) or slug contains the term. Public card fields only. */
    @Transactional(readOnly = true)
    public List<MerchantDtos.PublicShopSearchResponse> searchPublic(String q, int limit) {
        if (q == null || q.isBlank()) return List.of();
        var trimmed = q.trim();
        var term = (trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed).toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var found = entities.createQuery(
                "select s from Shop s where s.status = :approved and (lower(s.name) like :q escape '!'"
                    + " or lower(coalesce(s.nameEn, '')) like :q escape '!' or lower(s.slug) like :q escape '!')"
                    + " order by s.createdAt desc",
                Shop.class)
            .setParameter("approved", ShopStatus.APPROVED)
            .setParameter("q", "%" + term + "%")
            .setMaxResults(Math.max(1, Math.min(limit, 50)))
            .getResultList();
        var index = translations.loadIndex(TranslationEntityType.SHOP, found.stream().map(shop -> String.valueOf(shop.getId())).toList());
        return found.stream().map(shop -> {
            var entityId = String.valueOf(shop.getId());
            var logo = storage.openPublicImage(shop.getLogoPath()).isPresent() ? "/api/shops/" + shop.getSlug() + "/logo" : null;
            long productCount = entities.createQuery(
                    "select count(p) from Product p where p.shop.shopId = :shopId and p.deletedAt is null and p.status = :status", Long.class)
                .setParameter("shopId", shop.getId())
                .setParameter("status", ProductStatus.PUBLISHED)
                .getSingleResult();
            return new MerchantDtos.PublicShopSearchResponse(
                shop.getSlug(), shop.getName(),
                index.resolve(entityId, "name", "en", shop.getName(), shop.getNameEn()),
                index.resolve(entityId, "name", "zh", shop.getName(), null),
                shop.getCategory(), shop.getProvince(), logo, productCount
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.PublicShopResponse publicView(String slug, UUID viewerId) {
        var shop = requireVisibleShop(slug, viewerId);
        var index = translations.loadIndex(TranslationEntityType.SHOP, List.of(String.valueOf(shop.getId())));
        return toPublic(shop, index);
    }

    /**
     * Resolve a storefront shop that is APPROVED, or visible to the viewer as a preview.
     * Shared by public catalog and policy endpoints so visibility rules stay in one place.
     */
    @Transactional(readOnly = true)
    public Shop requireVisibleShop(String slug, UUID viewerId) {
        var shop = shops.findBySlug(slug).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        if (shop.getStatus() != ShopStatus.APPROVED && !canPreview(shop, viewerId)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
        }
        return shop;
    }

    /**
     * A shop that is not APPROVED is visible only to its owner, its active members and platform staff with
     * PLATFORM_SHOP_READ (super admin included), all read from the role tables (no JWT role claim).
     */
    boolean canPreview(Shop shop, UUID viewerId) {
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

    @Transactional(readOnly = true)
    public List<MerchantDtos.PublicShopCategoryResponse> publicCategories(String slug, UUID viewerId) {
        var shop = requireVisibleShop(slug, viewerId);
        var rows = entities.createQuery(
            "select c.categoryNo, c.nameTh, c.nameEn, c.nameZh, count(p), c.slug from Product p join p.category c "
                + "where p.shop.shopId = :shopId and p.deletedAt is null and p.status = :status "
                + "group by c.categoryNo, c.nameTh, c.nameEn, c.nameZh, c.sortOrder, c.slug order by c.sortOrder asc, c.nameTh asc",
            Object[].class
        )
            .setParameter("shopId", shop.getId())
            .setParameter("status", ProductStatus.PUBLISHED)
            .getResultList();
        return rows.stream()
            .map(row -> new MerchantDtos.PublicShopCategoryResponse(
                (Long) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (Long) row[4],
                (String) row[5]
            ))
            .toList();
    }

    private MerchantDtos.PublicShopResponse toPublic(Shop shop, ContentTranslationIndex index) {
        var entityId = String.valueOf(shop.getId());
        var logo = storage.openPublicImage(shop.getLogoPath()).isPresent() ? "/api/shops/" + shop.getSlug() + "/logo" : null;
        var cover = storage.openPublicImage(shop.getCoverPath()).isEmpty() ? null
            : "/api/shops/" + shop.getSlug() + "/cover?v=" + shop.getUpdatedAt().toEpochMilli();
        var productCount = entities.createQuery(
            "select count(p) from Product p where p.shop.shopId = :shopId and p.deletedAt is null and p.status = :status",
            Long.class
        )
            .setParameter("shopId", shop.getId())
            .setParameter("status", ProductStatus.PUBLISHED)
            .getSingleResult();
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
            shop.getLatitude(),
            shop.getLongitude(),
            shop.getSlug(),
            shop.path(),
            shop.getStatus(),
            logo,
            cover,
            openingHours(shop),
            themes.publicTheme(shop),
            shop.getCreatedAt(),
            productCount,
            shop.getPublicPhone(),
            shop.getPublicEmail(),
            shop.getWebsite(),
            shop.getLineId(),
            shop.getFacebookUrl(),
            shop.getInstagramUrl(),
            shop.getTiktokUrl()
        );
    }

    private List<MerchantDtos.ShopHour> openingHours(Shop shop) {
        var raw = shop.getOpeningHours();
        if (raw == null || raw.isBlank()) return defaultHours();
        var parts = raw.split(";");
        if (parts.length != 7) return defaultHours();
        var days = new ArrayList<MerchantDtos.ShopHour>();
        for (var part : parts) {
            var bits = part.split(",");
            if (bits.length != 3 || !bits[1].matches("^([01]\\d|2[0-3]):[0-5]\\d$") || !bits[2].matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
                return defaultHours();
            }
            days.add(new MerchantDtos.ShopHour("1".equals(bits[0]), bits[1], bits[2]));
        }
        return days;
    }

    private static List<MerchantDtos.ShopHour> defaultHours() {
        var days = new ArrayList<MerchantDtos.ShopHour>();
        for (var index = 0; index < 7; index++) {
            var weekday = index < 5;
            days.add(new MerchantDtos.ShopHour(weekday, weekday ? "08:00" : "09:00", weekday ? "20:00" : "18:00"));
        }
        return days;
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
