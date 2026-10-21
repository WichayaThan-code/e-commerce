package com.thaishopfun.search;

import com.thaishopfun.catalog.ProductService;
import com.thaishopfun.merchant.ShopCatalogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {
    static final int SHOP_LIMIT = 12;
    static final int BRAND_LIMIT = 20;

    private final ProductService products;
    private final ShopCatalogService shops;

    public SearchService(ProductService products, ShopCatalogService shops) {
        this.products = products;
        this.shops = shops;
    }

    public SearchDtos.SearchResponse search(String q, String brand, String sort, Integer page, Integer size, String locale) {
        var term = q == null ? "" : q.trim();
        var brandFilter = brand == null || brand.isBlank() ? null : brand.trim();
        // shops/brands are only previewed on the first page and when no brand filter narrows the result
        var first = page == null || page <= 0;
        var shopHits = first && brandFilter == null ? shops.searchPublic(term, SHOP_LIMIT) : List.<com.thaishopfun.merchant.MerchantDtos.PublicShopSearchResponse>of();
        var brandHits = first && brandFilter == null ? products.publicBrandSearch(term, BRAND_LIMIT) : List.<com.thaishopfun.catalog.CatalogDtos.PublicBrandResponse>of();
        return new SearchDtos.SearchResponse(term, brandFilter, shopHits, brandHits, products.publicSearch(term, brandFilter, sort, page, size, locale));
    }
}
