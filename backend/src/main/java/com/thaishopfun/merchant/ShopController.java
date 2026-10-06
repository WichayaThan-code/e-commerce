package com.thaishopfun.merchant;

import org.springframework.core.io.FileSystemResource;
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

@RestController
@RequestMapping("/api/shops")
public class ShopController {
    private final ShopCatalogService shops;
    public ShopController(ShopCatalogService shops) { this.shops = shops; }

    @GetMapping
    public List<MerchantDtos.PublicShopResponse> list() { return shops.list(); }

    @GetMapping("/{slug}")
    public MerchantDtos.PublicShopResponse get(@PathVariable String slug) { return shops.publicView(slug); }

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
