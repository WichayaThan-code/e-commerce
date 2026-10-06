package com.thaishopfun.merchant;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@PublicEndpoint("public category list: CATALOG_READ (GUEST)")
@RestController
@RequestMapping("/api/categories")
public class ProductCategoryController {
    private final ProductCategoryService categories;

    public ProductCategoryController(ProductCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<MerchantDtos.ProductCategoryResponse> list() {
        return categories.listActive();
    }
}
