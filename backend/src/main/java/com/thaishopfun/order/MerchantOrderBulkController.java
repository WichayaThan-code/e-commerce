package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Bulk order actions. Own mapping so {@code /orders/bulk/...} is never captured by
 * {@code /orders/{orderId}/...}. Each action carries the same shop permission as the single-order endpoint.
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/orders/bulk")
public class MerchantOrderBulkController {
    private final OrderQueryService queries;
    private final OrderBulkService bulk;

    public MerchantOrderBulkController(OrderQueryService queries, OrderBulkService bulk) {
        this.queries = queries;
        this.bulk = bulk;
    }

    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/accept")
    public OrderDtos.BulkResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                         @RequestBody(required = false) OrderDtos.BulkRequest body) {
        return run(jwt, shopId, "accept", body, idempotencyKey);
    }

    @RequireShopPermission("SHIPMENT_PACK")
    @PostMapping("/process")
    public OrderDtos.BulkResponse process(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @RequestBody(required = false) OrderDtos.BulkRequest body) {
        return run(jwt, shopId, "process", body, idempotencyKey);
    }

    @RequireShopPermission("SHIPMENT_DISPATCH")
    @PostMapping("/ship")
    public OrderDtos.BulkResponse ship(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       @RequestBody(required = false) OrderDtos.BulkRequest body) {
        return run(jwt, shopId, "ship", body, idempotencyKey);
    }

    @RequireShopPermission("ORDER_CANCEL")
    @PostMapping("/cancel")
    public OrderDtos.BulkResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                         @RequestBody(required = false) OrderDtos.BulkRequest body) {
        return run(jwt, shopId, "cancel", body, idempotencyKey);
    }

    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/tags")
    public OrderDtos.BulkResponse tags(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       @RequestBody(required = false) OrderDtos.BulkTagsRequest body) {
        var actor = actor(jwt, shopId);
        return bulk.runTags(actor.shopUuid(), actor.userId(), actor, body, idempotencyKey);
    }

    private OrderDtos.BulkResponse run(Jwt jwt, Long shopId, String action, OrderDtos.BulkRequest body, String idempotencyKey) {
        var actor = actor(jwt, shopId);
        return bulk.runAction(actor.shopUuid(), actor.userId(), actor, action, body, idempotencyKey);
    }

    private OrderActor actor(Jwt jwt, Long shopId) {
        return OrderActor.merchant(RequestUser.id(jwt), queries.requireShop(shopId).getUuid());
    }
}
