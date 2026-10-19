package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin view of every shop's orders. No PLATFORM_ORDER_* codes exist in the seed: reads use
 * PLATFORM_SHOP_READ, the override uses PLATFORM_SHOP_SUSPEND (platform intervention in a shop). Every admin
 * transition requires a reason and still follows {@link OrderStateMachine} (actor ADMIN).
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderQueryService queries;
    private final OrderTransitionService transitions;

    public AdminOrderController(OrderQueryService queries, OrderTransitionService transitions) {
        this.queries = queries;
        this.transitions = transitions;
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping
    public OrderDtos.PageResponse<OrderDtos.OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
                                                               @RequestParam(required = false) Long shopId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String paymentStatus,
                                                               @RequestParam(required = false) String fulfillmentStatus,
                                                               @RequestParam(required = false) String from,
                                                               @RequestParam(required = false) String to,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size,
                                                               @RequestParam(required = false) String sort,
                                                               @RequestParam(required = false) String direction) {
        var shop = shopId == null ? null : queries.requireShop(shopId).getUuid();
        return queries.list(OrderActor.admin(RequestUser.id(jwt)), shop,
            new OrderQueryService.Filters(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/{orderId}")
    public OrderDtos.OrderDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.get(OrderActor.admin(RequestUser.id(jwt)), orderId);
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/{orderId}/timeline")
    public List<OrderDtos.TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.timeline(OrderActor.admin(RequestUser.id(jwt)), orderId);
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/{orderId}/transitions")
    public OrderDtos.AllowedTransitions allowed(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.allowed(OrderActor.admin(RequestUser.id(jwt)), orderId);
    }

    /** body.status = target, body.reason required (+ carrierCode / trackingNumber for DISPATCHED, restock for REFUNDED). */
    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/{orderId}/transition")
    public OrderDtos.TransitionResponse transition(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                   @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        var actor = OrderActor.admin(RequestUser.id(jwt));
        var result = transitions.transition(orderId, actor, OrderTransitionService.Action.ADMIN, request);
        return new OrderDtos.TransitionResponse(result.changed(), result.from().name(), result.to().name(), queries.get(actor, orderId));
    }
}
