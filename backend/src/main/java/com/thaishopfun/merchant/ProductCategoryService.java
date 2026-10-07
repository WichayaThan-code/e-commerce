package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.translation.ContentTranslationIndex;
import com.thaishopfun.translation.TranslationEntityType;
import com.thaishopfun.translation.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {
    private final ProductCategoryRepository categories;
    private final TranslationService translations;

    public ProductCategoryService(ProductCategoryRepository categories, TranslationService translations) {
        this.categories = categories;
        this.translations = translations;
    }

    public List<MerchantDtos.ProductCategoryResponse> listActive() {
        var rows = categories.findByActiveTrueOrderBySortOrderAsc();
        var index = translations.loadIndex(
            TranslationEntityType.PRODUCT_CATEGORY,
            rows.stream().map(category -> String.valueOf(category.getId())).toList()
        );
        return rows.stream().map(category -> toResponse(category, index)).toList();
    }

    public void requireActiveOrCurrent(String submitted, String current) {
        var name = submitted == null ? "" : submitted.trim();
        if (current != null && name.equals(current.trim())) return;
        if (!categories.existsByNameThAndActiveTrue(name)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY", "หมวดสินค้าไม่ถูกต้อง");
        }
    }

    private MerchantDtos.ProductCategoryResponse toResponse(ProductCategory category, ContentTranslationIndex index) {
        var entityId = String.valueOf(category.getId());
        return new MerchantDtos.ProductCategoryResponse(
            category.getId(),
            category.getNameTh(),
            index.resolve(entityId, "name", "en", category.getNameTh(), category.getNameEn()),
            index.resolve(entityId, "name", "zh", category.getNameTh(), category.getNameZh())
        );
    }
}
