package com.thaishopfun.cart;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Shop coupons = THE merchant promotions API (frontend /merchant/promotions). V27 seeded PROMOTION_READ (reads) and
 * PROMOTION_MANAGE (every write) for the store roles, so those replace the interim SHOP_READ / SHOP_UPDATE.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/coupons")
public class MerchantCouponController {
    private final CouponService coupons;

    public MerchantCouponController(CouponService coupons) { this.coupons = coupons; }

    @RequireShopPermission("PROMOTION_READ")
    @GetMapping
    public CartDtos.CouponListResponse list(@PathVariable Long shopId,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String state,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return coupons.list(shopId, q, state, status, type, page, size);
    }

    /** Same filters as list; single data sheet (no Meta); all matching rows up to the export cap. */
    @RequireShopPermission("PROMOTION_READ")
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@PathVariable Long shopId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String lang,
                                         Locale locale) {
        Locale loc = lang != null && !lang.isBlank()
            ? Locale.forLanguageTag(lang.trim())
            : (locale == null ? Locale.forLanguageTag("th") : locale);
        byte[] body = coupons.exportWorkbook(shopId, q, status, type, loc);
        String filename = CouponService.suggestedFilename(shopId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    @RequireShopPermission("PROMOTION_READ")
    @GetMapping("/{couponId}")
    public CartDtos.CouponResponse get(@PathVariable Long shopId, @PathVariable UUID couponId) { return coupons.get(shopId, couponId); }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartDtos.CouponResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @RequestBody CartDtos.CouponRequest request) {
        return coupons.create(RequestUser.id(jwt), shopId, request);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PutMapping("/{couponId}")
    public CartDtos.CouponResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId,
                                          @RequestBody CartDtos.CouponRequest request) {
        return coupons.update(RequestUser.id(jwt), shopId, couponId, request);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping("/{couponId}/disable")
    public CartDtos.CouponResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId) {
        return coupons.setActive(RequestUser.id(jwt), shopId, couponId, false);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @PostMapping("/{couponId}/enable")
    public CartDtos.CouponResponse enable(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID couponId) {
        return coupons.setActive(RequestUser.id(jwt), shopId, couponId, true);
    }

    @RequireShopPermission("PROMOTION_MANAGE")
    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long shopId, @PathVariable UUID couponId) {
        coupons.delete(shopId, couponId);
    }
}
