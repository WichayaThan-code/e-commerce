package com.thaishopfun.merchant.theme;

import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.merchant.MerchantDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** AG2: CUSTOM theme media library (images / MP4 / WOFF2) for the merchant's current shop. */
@RestController
@RequestMapping("/api/merchant/settings/theme/media")
public class MerchantThemeMediaController {
    private final ShopThemeMediaService media;

    public MerchantThemeMediaController(ShopThemeMediaService media) {
        this.media = media;
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MerchantDtos.ThemeMediaResponse upload(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestParam("kind") String kind,
        @RequestPart("file") MultipartFile file
    ) {
        return media.upload(UUID.fromString(jwt.getSubject()), shopId, kind, file);
    }

    @RequireShopPermission(value = "SHOP_READ", defaultShop = true)
    @GetMapping
    public List<MerchantDtos.ThemeMediaResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false) String kind
    ) {
        return media.list(UUID.fromString(jwt.getSubject()), shopId, kind);
    }

    @RequireShopPermission(value = "SHOP_UPDATE", defaultShop = true)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long shopId,
        @PathVariable UUID id
    ) {
        media.softDelete(UUID.fromString(jwt.getSubject()), shopId, id);
    }
}
