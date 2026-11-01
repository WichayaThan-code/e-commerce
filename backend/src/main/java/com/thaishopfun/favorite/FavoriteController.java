package com.thaishopfun.favorite;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Signed-in buyer's favourites (buyer cart permissions, like the coupon wallet). Responses never carry user ids. */
@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {
    public record AddRequest(String productId) {}

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }

    @RequirePermission("CART_READ")
    @GetMapping
    public List<FavoriteService.FavoriteItem> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String shopSlug) {
        return favorites.list(RequestUser.id(jwt), shopSlug);
    }

    /** 201 = added, 200 = already a favourite. */
    @RequirePermission("CART_UPDATE")
    @PostMapping
    public ResponseEntity<FavoriteService.ToggleResponse> add(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) AddRequest request) {
        var result = favorites.add(RequestUser.id(jwt), request == null ? null : request.productId());
        return ResponseEntity.status(result.alreadyFavorite() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @RequirePermission("CART_UPDATE")
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String productId) {
        favorites.remove(RequestUser.id(jwt), productId);
        return ResponseEntity.noContent().build();
    }
}
