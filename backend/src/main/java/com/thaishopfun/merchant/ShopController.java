package com.thaishopfun.merchant;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.PublicEndpoint;
import com.thaishopfun.merchant.policy.ShopPolicyService;
import com.thaishopfun.merchant.theme.ShopThemeMediaService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Public storefront (CATALOG_READ is granted to GUEST in the matrix, so these endpoints need no sign-in). */
@PublicEndpoint("public storefront: CATALOG_READ (GUEST)")
@RestController
@RequestMapping("/api/shops")
public class ShopController {
    private final ShopCatalogService shops;
    private final ShopPolicyService policies;

    private final ShopThemeMediaService themeMedia;

    public ShopController(ShopCatalogService shops, ShopPolicyService policies, ShopThemeMediaService themeMedia) {
        this.shops = shops;
        this.policies = policies;
        this.themeMedia = themeMedia;
    }

    @GetMapping
    public List<MerchantDtos.PublicShopResponse> list() { return shops.list(); }

    @GetMapping("/{slug}")
    public MerchantDtos.PublicShopResponse get(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return shops.publicView(slug, RequestUser.id(jwt));
    }

    @GetMapping("/{slug}/categories")
    public List<MerchantDtos.PublicShopCategoryResponse> categories(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return shops.publicCategories(slug, RequestUser.id(jwt));
    }

    @GetMapping("/{slug}/policies")
    public List<MerchantDtos.ShopPolicyResponse> policies(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return policies.publicList(slug, RequestUser.id(jwt));
    }

    @GetMapping("/{slug}/policies/{type}")
    public MerchantDtos.ShopPolicyResponse policy(
        @PathVariable String slug,
        @PathVariable String type,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return policies.publicOne(slug, type, RequestUser.id(jwt));
    }

    @GetMapping("/{slug}/logo")
    public ResponseEntity<Resource> logo(@PathVariable String slug) {
        return image(shops.logoFile(slug));
    }

    @GetMapping("/{slug}/cover")
    public ResponseEntity<Resource> cover(@PathVariable String slug) {
        return image(shops.coverFile(slug));
    }

    /** AG2 CUSTOM theme media (banner image / video / font). Same visibility rules as the storefront itself. */
    @GetMapping("/{slug}/theme-media/{id}")
    public ResponseEntity<Resource> themeMedia(
        @PathVariable String slug,
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var shop = shops.requireVisibleShop(slug, RequestUser.id(jwt));
        var file = themeMedia.resolvePublicFile(shop, id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mimeType()))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(file.path()));
    }

    private ResponseEntity<Resource> image(java.nio.file.Path file) {
        var name = file.getFileName().toString().toLowerCase();
        var type = name.endsWith(".png") ? MediaType.IMAGE_PNG
            : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
            : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
            .contentType(type)
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(new FileSystemResource(file));
    }
}
