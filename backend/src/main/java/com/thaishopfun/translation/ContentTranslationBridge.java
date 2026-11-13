package com.thaishopfun.translation;

import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.catalog.Product;
import com.thaishopfun.catalog.ProductDescriptionHtml;
import com.thaishopfun.merchant.ProductCategory;
import com.thaishopfun.merchant.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Component
public class ContentTranslationBridge {
    private static final Logger log = LoggerFactory.getLogger(ContentTranslationBridge.class);

    private final TranslationService translations;

    public ContentTranslationBridge(TranslationService translations) {
        this.translations = translations;
    }

    public void onProductSaved(Product product, CatalogDtos.ProductWriteRequest request) {
        if (product == null || product.getId() == null) return;
        try {
            var entityId = product.getId().toString();
            var thaiFields = new LinkedHashMap<String, String>();
            thaiFields.put("name", product.getNameTh());
            var descriptionPlain = ProductDescriptionHtml.plainText(product.getDescriptionTh());
            if (!descriptionPlain.isBlank()) {
                thaiFields.put("description", descriptionPlain);
            }
            translations.requestTranslation(TranslationEntityType.PRODUCT, entityId, thaiFields);
            queueOptions(product, entityId);
            if (request != null) {
                translations.recordHuman(TranslationEntityType.PRODUCT, entityId, "name", "en", product.getNameTh(), request.nameEn());
                translations.recordHuman(TranslationEntityType.PRODUCT, entityId, "name", "zh", product.getNameTh(), request.nameZh());
                translations.recordHuman(TranslationEntityType.PRODUCT, entityId, "description", "en", descriptionPlain, request.descriptionEn());
                translations.recordHuman(TranslationEntityType.PRODUCT, entityId, "description", "zh", descriptionPlain, request.descriptionZh());
            }
        } catch (Exception exception) {
            log.warn("product translation queue skipped — {}", exception.getMessage());
        }
    }

    private void queueOptions(Product product, String entityId) {
        var fields = new LinkedHashMap<String, String>();
        var keep = new LinkedHashSet<String>();
        for (var slot : OptionTranslationFields.fromSchemaJson(product.getOptionSchema())) {
            if (!TranslationTextSupport.translatable(slot.thai())) continue;
            fields.put(slot.field(), slot.thai());
            keep.add(slot.field());
        }
        translations.requestTranslation(TranslationEntityType.PRODUCT_OPTION, entityId, fields);
        translations.dropUnlisted(TranslationEntityType.PRODUCT_OPTION, entityId, keep);
    }

    public void onCategorySaved(ProductCategory category) {
        if (category == null || category.getId() == null) return;
        try {
            translations.requestTranslation(
                TranslationEntityType.PRODUCT_CATEGORY,
                String.valueOf(category.getId()),
                Map.of("name", category.getNameTh())
            );
        } catch (Exception exception) {
            log.warn("category translation queue skipped — {}", exception.getMessage());
        }
    }

    public void onShopSaved(Shop shop, String manualNameEn) {
        if (shop == null || shop.getId() == null) return;
        try {
            var entityId = String.valueOf(shop.getId());
            var thaiFields = new LinkedHashMap<String, String>();
            thaiFields.put("name", shop.getName());
            if (shop.getDescription() != null && !shop.getDescription().isBlank()) {
                thaiFields.put("description", shop.getDescription());
            }
            translations.requestTranslation(TranslationEntityType.SHOP, entityId, thaiFields);
            translations.recordHuman(TranslationEntityType.SHOP, entityId, "name", "en", shop.getName(), manualNameEn);
        } catch (Exception exception) {
            log.warn("shop translation queue skipped — {}", exception.getMessage());
        }
    }

}
