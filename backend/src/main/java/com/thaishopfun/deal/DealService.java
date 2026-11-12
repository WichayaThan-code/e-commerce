package com.thaishopfun.deal;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.catalog.ProductService;
import com.thaishopfun.catalog.ProductStatus;
import com.thaishopfun.coupon.CouponCollectionRepository;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import com.thaishopfun.merchant.ShopStatus;
import com.thaishopfun.promotion.Coupon;
import com.thaishopfun.promotion.CouponRepository;
import com.thaishopfun.promotion.Promotion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DealService {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final int DEFAULT_PAGE = 24;
    private static final int MAX_PAGE = 48;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DealCampaignRepository campaigns;
    private final DealCampaignProductRepository campaignProducts;
    private final ProductRepository products;
    private final ProductService productService;
    private final ShopRepository shops;
    private final CouponRepository coupons;
    private final CouponCollectionRepository collections;
    private final EntityManager entities;

    public DealService(DealCampaignRepository campaigns, DealCampaignProductRepository campaignProducts,
                       ProductRepository products, ProductService productService, ShopRepository shops,
                       CouponRepository coupons, CouponCollectionRepository collections, EntityManager entities) {
        this.campaigns = campaigns;
        this.campaignProducts = campaignProducts;
        this.products = products;
        this.productService = productService;
        this.shops = shops;
        this.coupons = coupons;
        this.collections = collections;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public DealDtos.PageResponse<?> publicDeals(String tabRaw, Long categoryId, String priceBand, Integer minDiscount,
                                                Integer page, Integer size, String locale, UUID buyerId) {
        DealTab tab;
        try { tab = DealTab.fromParam(tabRaw); }
        catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "TAB_INVALID", "แท็บไม่ถูกต้อง");
        }
        if (tab == DealTab.COUPONS) return publicCoupons(categoryId, page, size, buyerId);

        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? DEFAULT_PAGE : Math.min(size, MAX_PAGE);
        var band = parsePriceBand(priceBand);
        Instant endsAt = null;
        String title = null;
        List<UUID> orderedIds;

        if (tab.campaignKind() != null) {
            var live = campaigns.findLiveByKind(tab.campaignKind(), Instant.now());
            if (live.isEmpty()) {
                return new DealDtos.PageResponse<>(List.of(), pageNumber, pageSize, 0, null, null);
            }
            endsAt = live.stream().map(DealCampaign::getEndsAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
            title = pickTitle(live.get(0), locale);
            var campaignIds = live.stream().map(DealCampaign::getId).toList();
            orderedIds = campaignProducts.findProductIdsByCampaignIdIn(campaignIds);
            orderedIds = filterPublishedIds(orderedIds, categoryId, band.min(), band.max(), minDiscount);
        } else if (tab == DealTab.UNDER199) {
            orderedIds = queryCatalogIds(categoryId, band.min(), band.max() != null ? band.max() : new BigDecimal("199"),
                minDiscount, false, pageNumber, pageSize);
            // For under199 without priceBand, force max 199; pagination applied in query when no campaign order
            var total = countCatalog(categoryId, band.min(), band.max() != null ? band.max() : new BigDecimal("199"), minDiscount, false);
            var cards = toDealCards(orderedIds, pageNumber * pageSize, null, locale);
            return new DealDtos.PageResponse<>(cards, pageNumber, pageSize, total, null, null);
        } else { // SPECIAL
            if (band.max() == null && band.min() == null && (priceBand == null || priceBand.isBlank())) {
                orderedIds = queryCatalogIds(categoryId, null, null, minDiscount, true, pageNumber, pageSize);
                var total = countCatalog(categoryId, null, null, minDiscount, true);
                var cards = toDealCards(orderedIds, pageNumber * pageSize, null, locale);
                return new DealDtos.PageResponse<>(cards, pageNumber, pageSize, total, null, null);
            }
            orderedIds = queryCatalogIds(categoryId, band.min(), band.max(), minDiscount, true, pageNumber, pageSize);
            var total = countCatalog(categoryId, band.min(), band.max(), minDiscount, true);
            var cards = toDealCards(orderedIds, pageNumber * pageSize, null, locale);
            return new DealDtos.PageResponse<>(cards, pageNumber, pageSize, total, null, null);
        }

        var total = orderedIds.size();
        var from = Math.min(total, pageNumber * pageSize);
        var pageIds = orderedIds.subList(from, Math.min(total, from + pageSize));
        var cards = toDealCards(pageIds, from, endsAt, locale);
        return new DealDtos.PageResponse<>(cards, pageNumber, pageSize, total, endsAt, title);
    }

    @Transactional(readOnly = true)
    public DealDtos.PageResponse<DealDtos.DealCouponResponse> publicCoupons(Long categoryId, Integer page, Integer size, UUID buyerId) {
        var pageNumber = page == null || page < 0 ? 0 : page;
        var pageSize = size == null || size < 1 ? DEFAULT_PAGE : Math.min(size, MAX_PAGE);
        var now = Instant.now();
        var rows = entities.createQuery(
                "select p from MerchantPromotion p where p.active = true and p.publicVisible = true"
                    + " and p.startsAt <= :now and (p.endsAt is null or p.endsAt > :now)"
                    + " order by case when p.endsAt is null then 1 else 0 end, p.endsAt asc, p.createdDateTime desc",
                Promotion.class)
            .setParameter("now", now)
            .getResultList();
        if (rows.isEmpty()) {
            return new DealDtos.PageResponse<>(List.of(), pageNumber, pageSize, 0, null, null);
        }
        var promoIds = rows.stream().map(Promotion::getId).toList();
        var codeByPromo = firstActiveCodes(promoIds);
        var usedByPromo = usageCounts(promoIds);
        var shopUuids = rows.stream().map(Promotion::getShopId).collect(Collectors.toSet());
        var shopByUuid = new HashMap<UUID, Shop>();
        for (var shop : shops.findAllById(shopUuids)) {
            if (shop.getStatus() == ShopStatus.APPROVED) shopByUuid.put(shop.getUuid(), shop);
        }

        var items = new ArrayList<DealDtos.DealCouponResponse>();
        for (var promo : rows) {
            var shop = shopByUuid.get(promo.getShopId());
            if (shop == null) continue;
            var code = codeByPromo.get(promo.getId());
            if (code == null) continue;
            var limit = promo.getUsageLimitTotal();
            if (limit != null && usedByPromo.getOrDefault(promo.getId(), 0L) >= limit) continue;
            // categoryId is unused for coupons (shop-wide); kept for API symmetry
            Boolean collected = null;
            if (buyerId != null) {
                var couponId = codeByPromo.containsKey(promo.getId()) ? couponIdFor(promo.getId(), code) : null;
                collected = couponId != null && collections.existsByCouponIdAndUserId(couponId, buyerId);
            }
            items.add(new DealDtos.DealCouponResponse(
                code, promo.getName(), promo.getType(), promo.getValue(), promo.getMinOrderAmount(),
                promo.getMaxDiscountAmount(), promo.getStartsAt(), promo.getEndsAt(),
                shop.getId(), shop.getSlug(), shop.getName(), collected));
        }
        var total = items.size();
        var from = Math.min(total, pageNumber * pageSize);
        var pageItems = items.subList(from, Math.min(total, from + pageSize));
        return new DealDtos.PageResponse<>(pageItems, pageNumber, pageSize, total, null, null);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public List<DealDtos.AdminCampaignResponse> adminList(String kindRaw) {
        List<DealCampaign> rows;
        if (kindRaw == null || kindRaw.isBlank()) {
            rows = campaigns.findAllByOrderBySortOrderAscCreatedDateTimeDesc();
        } else {
            DealCampaignKind kind;
            try { kind = DealCampaignKind.valueOf(kindRaw.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "KIND_INVALID", "ชนิดแคมเปญไม่ถูกต้อง", "kind");
            }
            rows = campaigns.findByKindOrderBySortOrderAscCreatedDateTimeDesc(kind);
        }
        return rows.stream().map(c -> toAdmin(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public DealDtos.AdminCampaignResponse adminGet(UUID id) {
        return toAdmin(requireCampaign(id), true);
    }

    @Transactional
    public DealDtos.AdminCampaignResponse create(DealDtos.CampaignWriteRequest request, UUID actorId) {
        var kind = parseKind(request == null ? null : request.kind());
        var campaign = new DealCampaign(kind);
        applyWrite(campaign, request, actorId, true);
        campaigns.save(campaign);
        return toAdmin(campaign, true);
    }

    @Transactional
    public DealDtos.AdminCampaignResponse update(UUID id, DealDtos.CampaignWriteRequest request, UUID actorId) {
        var campaign = requireCampaign(id);
        if (request != null && request.kind() != null && !request.kind().isBlank()) {
            campaign.setKind(parseKind(request.kind()));
        }
        applyWrite(campaign, request, actorId, false);
        return toAdmin(campaign, true);
    }

    @Transactional
    public void delete(UUID id) {
        var campaign = requireCampaign(id);
        campaigns.delete(campaign);
    }

    @Transactional
    public DealDtos.AdminCampaignResponse attachProducts(UUID id, DealDtos.CampaignProductIdsRequest request, UUID actorId) {
        var campaign = requireCampaign(id);
        var ids = request == null || request.productIds() == null ? List.<UUID>of() : request.productIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new AuthException(HttpStatus.BAD_REQUEST, "PRODUCTS_REQUIRED", "ต้องระบุสินค้า", "productIds");
        var found = products.findAllById(ids);
        var byId = found.stream().collect(Collectors.toMap(Product::getId, p -> p));
        for (var productId : ids) {
            var product = byId.get(productId);
            if (product == null || product.getDeletedAt() != null) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOUND", "ไม่พบสินค้า " + productId, "productIds");
            }
            if (product.getStatus() != ProductStatus.PUBLISHED) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_PUBLISHED", "สินค้าต้องเผยแพร่ก่อนแนบ", "productIds");
            }
            if (campaignProducts.existsByCampaignIdAndProductId(campaign.getId(), productId)) continue;
            var sort = (int) campaignProducts.countByCampaignId(campaign.getId());
            campaignProducts.save(new DealCampaignProduct(campaign.getId(), productId, sort, actorId));
        }
        campaign.stamp(actorId);
        return toAdmin(campaign, true);
    }

    @Transactional
    public DealDtos.AdminCampaignResponse detachProduct(UUID id, UUID productId, UUID actorId) {
        var campaign = requireCampaign(id);
        campaignProducts.deleteByCampaignIdAndProductId(campaign.getId(), productId);
        campaign.stamp(actorId);
        return toAdmin(campaign, true);
    }

    // ---- helpers ----

    private List<DealDtos.DealProductResponse> toDealCards(List<UUID> orderedIds, int rankOffset, Instant endsAt, String locale) {
        var sold = Map.<UUID, Long>of();
        var cards = productService.rankedPublicCards(orderedIds, sold, rankOffset, locale);
        return cards.stream().map(row -> DealDtos.DealProductResponse.from(row, endsAt)).toList();
    }

    private List<UUID> filterPublishedIds(List<UUID> orderedIds, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer minDiscount) {
        if (orderedIds.isEmpty()) return List.of();
        // Preserve order while filtering via a single fetch of candidates
        var byId = new HashMap<UUID, Product>();
        for (var product : products.findAllById(orderedIds)) byId.put(product.getId(), product);
        var result = new ArrayList<UUID>();
        for (var id : orderedIds) {
            var product = byId.get(id);
            if (product == null || product.getDeletedAt() != null) continue;
            if (product.getStatus() != ProductStatus.PUBLISHED) continue;
            if (product.getShop() == null || product.getShop().getStatus() != ShopStatus.APPROVED) continue;
            if (categoryId != null) {
                if (product.getCategory() == null || !categoryId.equals(product.getCategory().getId())) continue;
            }
            var price = product.getBasePrice();
            if (minPrice != null && (price == null || price.compareTo(minPrice) < 0)) continue;
            if (maxPrice != null && (price == null || price.compareTo(maxPrice) > 0)) continue;
            if (minDiscount != null && minDiscount > 0 && discountPct(product) < minDiscount) continue;
            result.add(id);
        }
        return result;
    }

    private List<UUID> queryCatalogIds(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer minDiscount,
                                       boolean onSaleOnly, int page, int size) {
        var where = catalogWhere(categoryId, minPrice, maxPrice, minDiscount, onSaleOnly);
        var query = entities.createQuery("select p.id" + where + " order by p.publishedAt desc, p.createdDateTime desc, p.id asc", UUID.class);
        bindCatalog(query, categoryId, minPrice, maxPrice, minDiscount);
        return query.setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    private long countCatalog(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer minDiscount, boolean onSaleOnly) {
        var where = catalogWhere(categoryId, minPrice, maxPrice, minDiscount, onSaleOnly);
        var query = entities.createQuery("select count(p.id)" + where, Long.class);
        bindCatalog(query, categoryId, minPrice, maxPrice, minDiscount);
        return query.getSingleResult();
    }

    private String catalogWhere(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer minDiscount, boolean onSaleOnly) {
        var where = new StringBuilder(" from Product p where p.deletedAt is null and p.status = :status and p.shop.status = :approved");
        if (categoryId != null) where.append(" and p.category.categoryNo = :categoryId");
        if (minPrice != null) where.append(" and p.basePrice >= :minPrice");
        if (maxPrice != null) where.append(" and p.basePrice <= :maxPrice");
        if (onSaleOnly) where.append(" and p.compareAtPrice is not null and p.basePrice is not null and p.compareAtPrice > p.basePrice");
        if (minDiscount != null && minDiscount > 0) {
            where.append(" and p.compareAtPrice is not null and p.basePrice is not null and p.compareAtPrice > p.basePrice");
            where.append(" and ((p.compareAtPrice - p.basePrice) * 100 / p.compareAtPrice) >= :minDiscount");
        }
        return where.toString();
    }

    private void bindCatalog(TypedQuery<?> query, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, Integer minDiscount) {
        query.setParameter("status", ProductStatus.PUBLISHED);
        query.setParameter("approved", ShopStatus.APPROVED);
        if (categoryId != null) query.setParameter("categoryId", categoryId);
        if (minPrice != null) query.setParameter("minPrice", minPrice);
        if (maxPrice != null) query.setParameter("maxPrice", maxPrice);
        if (minDiscount != null && minDiscount > 0) query.setParameter("minDiscount", BigDecimal.valueOf(minDiscount));
    }

    private static int discountPct(Product product) {
        var price = product.getBasePrice();
        var compare = product.getCompareAtPrice();
        if (price == null || compare == null || compare.compareTo(BigDecimal.ZERO) <= 0 || compare.compareTo(price) <= 0) return 0;
        return compare.subtract(price).multiply(HUNDRED).divide(compare, 0, RoundingMode.HALF_UP).intValue();
    }

    private record PriceBand(BigDecimal min, BigDecimal max) {}

    private PriceBand parsePriceBand(String raw) {
        if (raw == null || raw.isBlank()) return new PriceBand(null, null);
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "under100", "lt100", "0-99" -> new PriceBand(null, new BigDecimal("99.99"));
            case "100-300" -> new PriceBand(new BigDecimal("100"), new BigDecimal("300"));
            case "301-500" -> new PriceBand(new BigDecimal("301"), new BigDecimal("500"));
            case "501-1000" -> new PriceBand(new BigDecimal("501"), new BigDecimal("1000"));
            case "over1000", "gt1000" -> new PriceBand(new BigDecimal("1000.01"), null);
            default -> throw new AuthException(HttpStatus.BAD_REQUEST, "PRICE_BAND_INVALID", "ช่วงราคาไม่ถูกต้อง", "priceBand");
        };
    }

    private Map<UUID, String> firstActiveCodes(List<UUID> promoIds) {
        var result = new LinkedHashMap<UUID, String>();
        if (promoIds.isEmpty()) return result;
        for (var coupon : coupons.findByPromotionIdInOrderByCreatedDateTimeAsc(promoIds)) {
            if (!coupon.isActive()) continue;
            result.putIfAbsent(coupon.getPromotionId(), coupon.getCode());
        }
        return result;
    }

    private Map<UUID, Long> usageCounts(List<UUID> promoIds) {
        var result = new HashMap<UUID, Long>();
        if (promoIds.isEmpty()) return result;
        for (var row : entities.createQuery(
                "select u.promotionId, count(u) from MerchantPromotionUsage u where u.promotionId in :ids group by u.promotionId",
                Object[].class)
            .setParameter("ids", promoIds).getResultList()) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private UUID couponIdFor(UUID promotionId, String code) {
        return coupons.findByPromotionIdOrderByCreatedDateTimeAsc(promotionId).stream()
            .filter(Coupon::isActive)
            .filter(c -> c.getCode().equalsIgnoreCase(code))
            .map(Coupon::getId)
            .findFirst().orElse(null);
    }

    private DealCampaign requireCampaign(UUID id) {
        return campaigns.findById(id).orElseThrow(() ->
            new AuthException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "ไม่พบแคมเปญดีล"));
    }

    private DealCampaignKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "KIND_REQUIRED", "ต้องระบุชนิดแคมเปญ", "kind");
        }
        try { return DealCampaignKind.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "KIND_INVALID", "ชนิดแคมเปญไม่ถูกต้อง", "kind");
        }
    }

    private void applyWrite(DealCampaign campaign, DealDtos.CampaignWriteRequest request, UUID actorId, boolean creating) {
        if (request == null) request = new DealDtos.CampaignWriteRequest(null, null, null, null, null, null, null, null);
        var titleTh = trim(request.titleTh());
        if (titleTh == null) throw new AuthException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED", "ต้องระบุชื่อแคมเปญ", "titleTh");
        if (titleTh.length() > 150) throw new AuthException(HttpStatus.BAD_REQUEST, "TITLE_TOO_LONG", "ชื่อแคมเปญยาวเกินไป", "titleTh");
        var startsAt = parseInstant(request.startsAt(), "startsAt", true);
        var endsAt = parseInstant(request.endsAt(), "endsAt", false);
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ENDS_BEFORE_START", "วันสิ้นสุดต้องหลังวันเริ่ม", "endsAt");
        }
        if (creating && startsAt == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "STARTS_REQUIRED", "ต้องระบุวันเริ่ม", "startsAt");
        }
        if (!creating && startsAt == null) startsAt = campaign.getStartsAt();
        var active = request.active() == null ? (creating || campaign.isActive()) : request.active();
        var sort = request.sortOrder() == null ? (creating ? 0 : campaign.getSortOrder()) : request.sortOrder();
        campaign.apply(titleTh, trim(request.titleEn()), trim(request.titleZh()), startsAt, endsAt, active, sort, actorId);
    }

    private Instant parseInstant(String raw, String field, boolean required) {
        var value = trim(raw);
        if (value == null) {
            if (required) throw new AuthException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED", "ต้องระบุวันที่", field);
            return null;
        }
        try {
            if (value.endsWith("Z") || value.contains("+") || value.lastIndexOf('-') > 7 && value.contains("T") && value.length() > 19) {
                return Instant.parse(value);
            }
            return LocalDateTime.parse(value).atZone(BANGKOK).toInstant();
        } catch (DateTimeParseException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "รูปแบบวันที่ไม่ถูกต้อง", field);
        }
    }

    private DealDtos.AdminCampaignResponse toAdmin(DealCampaign campaign, boolean withProducts) {
        var count = campaignProducts.countByCampaignId(campaign.getId());
        List<DealDtos.AdminCampaignProduct> attached = List.of();
        if (withProducts) {
            var links = campaignProducts.findByCampaignIdOrderBySortOrderAscCreatedDateTimeAsc(campaign.getId());
            var ids = links.stream().map(DealCampaignProduct::getProductId).toList();
            var byId = new HashMap<UUID, Product>();
            for (var product : products.findAllById(ids)) byId.put(product.getId(), product);
            attached = new ArrayList<>();
            for (var link : links) {
                var product = byId.get(link.getProductId());
                if (product == null) continue;
                var shop = product.getShop();
                attached.add(new DealDtos.AdminCampaignProduct(
                    product.getId(), product.getSlug(), product.getNameTh(),
                    shop == null ? null : shop.getSlug(), shop == null ? null : shop.getName(),
                    product.getBasePrice(), product.getCompareAtPrice(), link.getSortOrder()));
            }
        }
        return new DealDtos.AdminCampaignResponse(
            campaign.getId(), campaign.getKind(), campaign.getTitleTh(), campaign.getTitleEn(), campaign.getTitleZh(),
            campaign.getStartsAt(), campaign.getEndsAt(), campaign.isActive(), campaign.getSortOrder(), count,
            campaign.getCreatedDateTime(), campaign.getModifiedDateTime(), attached);
    }

    private static String pickTitle(DealCampaign campaign, String locale) {
        var lang = locale == null ? "th" : locale.toLowerCase(Locale.ROOT);
        if (lang.startsWith("en") && campaign.getTitleEn() != null && !campaign.getTitleEn().isBlank()) return campaign.getTitleEn();
        if (lang.startsWith("zh") && campaign.getTitleZh() != null && !campaign.getTitleZh().isBlank()) return campaign.getTitleZh();
        return campaign.getTitleTh();
    }

    private static String trim(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
