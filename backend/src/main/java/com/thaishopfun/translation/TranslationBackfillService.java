package com.thaishopfun.translation;

import com.thaishopfun.catalog.ProductRepository;
import com.thaishopfun.merchant.ProductCategoryRepository;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TranslationBackfillService implements TranslationService.BackfillSource {
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final ShopRepository shops;

    public TranslationBackfillService(ProductRepository products, ProductCategoryRepository categories, ShopRepository shops) {
        this.products = products;
        this.categories = categories;
        this.shops = shops;
    }

    @Override
    public List<BackfillItem> items(TranslationEntityType entityType) {
        return switch (entityType) {
            case PRODUCT_OPTION -> products.findAll().stream()
                .map(product -> {
                    var fields = new LinkedHashMap<String, String>();
                    for (var slot : OptionTranslationFields.fromSchemaJson(product.getOptionSchema())) {
                        if (TranslationTextSupport.translatable(slot.thai())) fields.put(slot.field(), slot.thai());
                    }
                    return new BackfillItem(product.getId().toString(), fields);
                })
                .filter(item -> !item.fields().isEmpty())
                .toList();
            case PRODUCT -> products.findAll().stream()
                .map(product -> {
                    var fields = new LinkedHashMap<String, String>();
                    fields.put("name", product.getNameTh());
                    if (product.getDescriptionTh() != null && !product.getDescriptionTh().isBlank()) {
                        fields.put("description", product.getDescriptionTh());
                    }
                    return new BackfillItem(product.getId().toString(), fields);
                })
                .toList();
            case PRODUCT_CATEGORY -> categories.findAll().stream()
                .map(category -> new BackfillItem(String.valueOf(category.getId()), Map.of("name", category.getNameTh())))
                .toList();
            case SHOP -> shops.findAll().stream()
                .map(shop -> {
                    var fields = new LinkedHashMap<String, String>();
                    fields.put("name", shop.getName());
                    if (shop.getDescription() != null && !shop.getDescription().isBlank()) {
                        fields.put("description", shop.getDescription());
                    }
                    return new BackfillItem(String.valueOf(shop.getId()), fields);
                })
                .toList();
        };
    }
}
