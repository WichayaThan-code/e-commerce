package com.thaishopfun.translation;

import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.merchant.ProductCategoryRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EntityThaiSourceRegistry {
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final ShopRepository shops;

    public EntityThaiSourceRegistry(ProductRepository products, ProductCategoryRepository categories, ShopRepository shops) {
        this.products = products;
        this.categories = categories;
        this.shops = shops;
    }

    public String thaiText(TranslationEntityType type, String entityId, String field) {
        return switch (type) {
            case PRODUCT -> productField(entityId, field);
            case PRODUCT_CATEGORY -> categoryField(entityId, field);
            case SHOP -> shopField(entityId, field);
        };
    }

    private String productField(String entityId, String field) {
        var product = products.findById(UUID.fromString(entityId)).orElse(null);
        if (product == null) return null;
        return switch (field) {
            case "name" -> product.getNameTh();
            case "description" -> product.getDescriptionTh();
            default -> null;
        };
    }

    private String categoryField(String entityId, String field) {
        if (!"name".equals(field)) return null;
        var category = categories.findByCategoryNo(Long.parseLong(entityId)).orElse(null);
        return category == null ? null : category.getNameTh();
    }

    private String shopField(String entityId, String field) {
        var shop = shops.findByShopId(Long.parseLong(entityId)).orElse(null);
        if (shop == null) return null;
        return switch (field) {
            case "name" -> shop.getName();
            case "description" -> shop.getDescription();
            default -> null;
        };
    }
}
