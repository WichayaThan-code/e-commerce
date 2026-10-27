package com.thaishopfun.catalog.collection;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** V41 merchant collections: reads need PRODUCT_READ, writes PRODUCT_UPDATE (same matrix as products). */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/collections")
public class MerchantCollectionController {
    private final ShopCollectionService collections;

    public MerchantCollectionController(ShopCollectionService collections) { this.collections = collections; }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping
    public List<CollectionDtos.MerchantCollectionResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return collections.list(RequestUser.id(jwt), shopId);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionDtos.MerchantCollectionDetail create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                          @RequestBody CollectionDtos.CollectionWriteRequest request) {
        return collections.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping("/order")
    public List<CollectionDtos.MerchantCollectionResponse> reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                                   @RequestBody CollectionDtos.OrderRequest request) {
        return collections.reorder(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("PRODUCT_READ")
    @GetMapping("/{id}")
    public CollectionDtos.MerchantCollectionDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID id) {
        return collections.get(RequestUser.id(jwt), shopId, id);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping("/{id}")
    public CollectionDtos.MerchantCollectionDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID id,
                                                          @RequestBody CollectionDtos.CollectionWriteRequest request) {
        return collections.update(RequestUser.id(jwt), shopId, id, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID id) {
        collections.delete(RequestUser.id(jwt), shopId, id);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PostMapping("/{id}/products")
    public CollectionDtos.MerchantCollectionDetail addProducts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID id,
                                                               @RequestBody CollectionDtos.ProductIdsRequest request) {
        return collections.addProducts(RequestUser.id(jwt), shopId, id, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @PutMapping("/{id}/products")
    public CollectionDtos.MerchantCollectionDetail replaceProducts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID id,
                                                                   @RequestBody CollectionDtos.ProductIdsRequest request) {
        return collections.replaceProducts(RequestUser.id(jwt), shopId, id, request);
    }

    @RequireShopPermission("PRODUCT_UPDATE")
    @DeleteMapping("/{id}/products/{productId}")
    public CollectionDtos.MerchantCollectionDetail removeProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                                                 @PathVariable UUID id, @PathVariable UUID productId) {
        return collections.removeProduct(RequestUser.id(jwt), shopId, id, productId);
    }
}
