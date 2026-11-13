package com.thaishopfun.catalog;

import com.thaishopfun.auth.authz.PublicEndpoint;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Serves public inline images embedded in product descriptions. */
@RestController
@PublicEndpoint
@RequestMapping("/api/product-description-images")
public class PublicProductDescriptionImageController {
    private final ProductService products;

    public PublicProductDescriptionImageController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Resource> get(@PathVariable UUID mediaId) {
        return products.serveDescriptionImage(mediaId);
    }
}
