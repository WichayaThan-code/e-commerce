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

    /**
     * Product name/description are never machine-translated on save (no provider call, no PENDING job).
     * English/Chinese come from the merchant's own optional input; a blank field stores the Thai text as a
     * MANUAL translation so the storefront shows Thai and nothing later re-queues or overwrites it.
     * The description is rich HTML: its source key is the plain text (same as {@link EntityThaiSourceRegistry}),
     * while the Thai fallback keeps the HTML so formatting still renders.
     */
    public void onProductSaved(Product product, CatalogDtos.ProductWriteRequest request) {
        if (product == null || product.getId() == null) return;
        try {
            var entityId = product.getId().toString();
            storeManualOrThai(entityId, "name", product.getNameTh(), product.getNameTh(),
                request == null ? null : request.nameEn(), request == null ? null : request.nameZh());
            storeManualOrThai(entityId, "description",
                ProductDescriptionHtml.plainText(product.getDescriptionTh()), product.getDescriptionTh(),
                request == null ? null : request.descriptionEn(), request == null ? null : request.descriptionZh());
            queueOptions(product, entityId);
        } catch (Exception exception) {
            log.warn("product translation save skipped — {}", exception.getMessage());
        }
    }

    private void storeManualOrThai(String entityId, String field, String sourceText, String thai, String english, String chinese) {
        var source = TranslationTextSupport.blankToNull(sourceText);
        if (source == null) {
            translations.clearField(TranslationEntityType.PRODUCT, entityId, field);
            return;
        }
        var fallback = TranslationTextSupport.blankToNull(thai) == null ? source : thai;
        translations.recordHuman(TranslationEntityType.PRODUCT, entityId, field, "en", source, orThai(english, fallback));
        translations.recordHuman(TranslationEntityType.PRODUCT, entityId, field, "zh", source, orThai(chinese, fallback));
    }

    private static String orThai(String value, String thai) {
        var text = TranslationTextSupport.blankToNull(value);
        return text == null ? thai : text;
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
