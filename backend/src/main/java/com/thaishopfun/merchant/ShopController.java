package com.thaishopfun.merchant;

<<<<<<< HEAD
import com.thaishopfun.auth.RequestUser;
=======
import com.thaishopfun.auth.authz.PublicEndpoint;
>>>>>>> 232b7724b5084e8924c048593b686e77c9566a9d
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

/** Public storefront (CATALOG_READ is granted to GUEST in the matrix, so these endpoints need no sign-in). */
@PublicEndpoint("public storefront: CATALOG_READ (GUEST)")
@RestController
@RequestMapping("/api/shops")
public class ShopController {
    private final ShopCatalogService shops;
    public ShopController(ShopCatalogService shops) { this.shops = shops; }

    @GetMapping
    public List<MerchantDtos.PublicShopResponse> list() { return shops.list(); }

    @GetMapping("/{slug}")
    public MerchantDtos.PublicShopResponse get(@PathVariable String slug, @AuthenticationPrincipal Jwt jwt) {
        return shops.publicView(slug, RequestUser.id(jwt), RequestUser.admin(jwt));
    }

    @GetMapping("/{slug}/logo")
    public ResponseEntity<Resource> logo(@PathVariable String slug) {
        return image(shops.logoFile(slug));
    }

    @GetMapping("/{slug}/cover")
    public ResponseEntity<Resource> cover(@PathVariable String slug) {
        return image(shops.coverFile(slug));
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
