package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {
    private final ProductCategoryRepository categories;

    public ProductCategoryService(ProductCategoryRepository categories) {
        this.categories = categories;
    }

    public List<MerchantDtos.ProductCategoryResponse> listActive() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(category -> new MerchantDtos.ProductCategoryResponse(
                category.getId(), category.getNameTh(), category.getNameEn(), category.getNameZh()))
            .toList();
    }

    public void requireActiveOrCurrent(String submitted, String current) {
        var name = submitted == null ? "" : submitted.trim();
        if (current != null && name.equals(current.trim())) return;
        if (!categories.existsByNameThAndActiveTrue(name)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY", "หมวดสินค้าไม่ถูกต้อง");
        }
    }
}
