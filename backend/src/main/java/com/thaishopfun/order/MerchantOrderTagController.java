package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Shop-level order tag CRUD. {shopId} = public shop number. */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/order-tags")
public class MerchantOrderTagController {
    private final OrderQueryService queries;
    private final OrderTagService tags;

    public MerchantOrderTagController(OrderQueryService queries, OrderTagService tags) {
        this.queries = queries;
        this.tags = tags;
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping
    public List<OrderDtos.TagView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId) {
        return tags.listTags(shopUuid(shopId));
    }

    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.TagView create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                    @RequestBody(required = false) OrderDtos.OrderTagRequest body) {
        return tags.createTag(shopUuid(shopId), RequestUser.id(jwt), body);
    }

    @RequireShopPermission("ORDER_UPDATE")
    @PutMapping("/{tagId}")
    public OrderDtos.TagView update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID tagId,
                                    @RequestBody(required = false) OrderDtos.OrderTagRequest body) {
        return tags.updateTag(shopUuid(shopId), RequestUser.id(jwt), tagId, body);
    }

    @RequireShopPermission("ORDER_UPDATE")
    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID tagId) {
        tags.deleteTag(shopUuid(shopId), tagId);
    }

    private UUID shopUuid(Long shopId) {
        return queries.requireShop(shopId).getUuid();
    }
}
