package com.thaishopfun.search;

import com.thaishopfun.catalog.CatalogDtos;
import com.thaishopfun.merchant.MerchantDtos;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {}

    /** GET /api/search: shops and brands are capped previews; products are paged. Public data only. */
    public record SearchResponse(
        String q,
        String brand,
        List<MerchantDtos.PublicShopSearchResponse> shops,
        List<CatalogDtos.PublicBrandResponse> brands,
        CatalogDtos.PageResponse<CatalogDtos.PublicSearchProductResponse> products
    ) {}
}
