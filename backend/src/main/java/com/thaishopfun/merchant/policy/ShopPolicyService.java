package com.thaishopfun.merchant.policy;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.authz.AuthorizationService;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.common.ValidationMessages;
import com.thaishopfun.merchant.MerchantDtos;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopCatalogService;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ShopPolicyService {
    private final ShopPolicyRepository policies;
    private final ShopRepository shops;
    private final ShopCatalogService catalog;
    private final AuthorizationService authorization;

    public ShopPolicyService(
        ShopPolicyRepository policies,
        ShopRepository shops,
        ShopCatalogService catalog,
        AuthorizationService authorization
    ) {
        this.policies = policies;
        this.shops = shops;
        this.catalog = catalog;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.ShopPolicyResponse> publicList(String slug, UUID viewerId) {
        var shop = catalog.requireVisibleShop(slug, viewerId);
        return orderedResponses(policies.findAllByShopUuid(shop.getUuid()), true);
    }

    @Transactional(readOnly = true)
    public MerchantDtos.ShopPolicyResponse publicOne(String slug, String typePath, UUID viewerId) {
        var shop = catalog.requireVisibleShop(slug, viewerId);
        var type = ShopPolicyType.fromPath(typePath)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "ไม่พบนโยบายร้าน"));
        var policy = policies.findByShopUuidAndType(shop.getUuid(), type)
            .filter(row -> row.getBody() != null && !row.getBody().isBlank())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "ไม่พบนโยบายร้าน"));
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.ShopPolicyResponse> merchantList(UUID userId, Long shopId) {
        var shop = ownedShop(userId, shopId);
        return orderedResponses(policies.findAllByShopUuid(shop.getUuid()), false);
    }

    @Transactional
    public List<MerchantDtos.ShopPolicyResponse> merchantSave(UUID userId, Long shopId, MerchantDtos.ShopPoliciesUpdateRequest request) {
        var shop = ownedShop(userId, shopId);
        var byType = new LinkedHashMap<ShopPolicyType, MerchantDtos.ShopPolicyItemRequest>();
        for (var item : request.policies()) {
            var type = ShopPolicyType.fromPath(item.type())
                .orElseThrow(() -> new ValidationException(List.of(
                    ValidationMessages.violation("policies", "VALIDATION_ERROR")
                )));
            if (byType.containsKey(type)) {
                throw new ValidationException(List.of(
                    ValidationMessages.violation("policies", "VALIDATION_ERROR")
                ));
            }
            byType.put(type, item);
        }

        var existing = policies.findAllByShopUuid(shop.getUuid());
        var existingByType = new EnumMap<ShopPolicyType, ShopPolicy>(ShopPolicyType.class);
        for (var row : existing) existingByType.put(row.getType(), row);

        for (var type : ShopPolicyType.values()) {
            var item = byType.get(type);
            var current = existingByType.get(type);
            if (item == null || item.body() == null || item.body().isBlank()) {
                if (current != null) policies.delete(current);
                continue;
            }
            var title = trimToNull(item.title());
            var body = item.body().trim();
            if (current == null) {
                policies.save(new ShopPolicy(shop, type, title, body, userId));
            } else {
                current.update(title, body, userId);
                policies.save(current);
            }
        }
        return merchantList(userId, shop.getId());
    }

    private List<MerchantDtos.ShopPolicyResponse> orderedResponses(List<ShopPolicy> rows, boolean requireBody) {
        var byType = new EnumMap<ShopPolicyType, ShopPolicy>(ShopPolicyType.class);
        for (var row : rows) {
            if (requireBody && (row.getBody() == null || row.getBody().isBlank())) continue;
            byType.put(row.getType(), row);
        }
        var result = new ArrayList<MerchantDtos.ShopPolicyResponse>();
        for (var type : ShopPolicyType.values()) {
            var row = byType.get(type);
            if (row != null) result.add(toResponse(row));
        }
        return result;
    }

    private static MerchantDtos.ShopPolicyResponse toResponse(ShopPolicy policy) {
        return new MerchantDtos.ShopPolicyResponse(
            policy.getType().path(),
            policy.getTitle(),
            policy.getBody(),
            policy.getUpdatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Shop ownedShop(UUID userId, Long shopId) {
        if (shopId != null && authorization.isSuperAdmin(userId)) {
            return shops.findByShopId(shopId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
        }
        var owned = shops.findMemberShops(userId);
        if (owned.isEmpty()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ยังไม่มีร้านค้าที่สมัครไว้");
        }
        if (shopId == null) return owned.get(0);
        return owned.stream().filter(shop -> shop.getId().equals(shopId)).findFirst()
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
