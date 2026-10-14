package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Shop order management. {shopId} = public shop number; orders of other shops answer 404. */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/orders")
public class MerchantOrderController {
    private final OrderQueryService queries;
    private final OrderTransitionService transitions;

    public MerchantOrderController(OrderQueryService queries, OrderTransitionService transitions) {
        this.queries = queries;
        this.transitions = transitions;
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping
    public OrderDtos.PageResponse<OrderDtos.OrderSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId,
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
        return queries.list(actor(jwt, shopId), null,
            new OrderQueryService.Filters(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction));
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/{orderId}")
    public OrderDtos.OrderDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.get(actor(jwt, shopId), orderId);
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/{orderId}/timeline")
    public List<OrderDtos.TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.timeline(actor(jwt, shopId), orderId);
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/{orderId}/transitions")
    public OrderDtos.AllowedTransitions allowed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.allowed(actor(jwt, shopId), orderId);
    }

    /** Manual payment (bank transfer / PromptPay slip checked by the shop). Gateway callbacks use the SYSTEM actor. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/{orderId}/mark-paid")
    public OrderDtos.TransitionResponse markPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                 @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.MARK_PAID, request);
    }

    /** No ORDER_CONFIRM code in the seed; ORDER_UPDATE is used. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/{orderId}/confirm")
    public OrderDtos.TransitionResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.CONFIRM, request);
    }

    /** body.status: READY_TO_PACK | PACKING | PACKED. */
    @RequireShopPermission("SHIPMENT_PACK")
    @PostMapping("/{orderId}/process")
    public OrderDtos.TransitionResponse process(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.PROCESS, request);
    }

    /** body.carrierCode + body.trackingNumber required. Stock is committed (COMMIT_SALE) here. */
    @RequireShopPermission("SHIPMENT_DISPATCH")
    @PostMapping("/{orderId}/ship")
    public OrderDtos.TransitionResponse ship(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                             @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.SHIP, request);
    }

    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping("/{orderId}/deliver")
    public OrderDtos.TransitionResponse deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.DELIVER, request);
    }

    /** body.reason required. Paid orders are refunded in full automatically; held stock is released. */
    @RequireShopPermission("ORDER_CANCEL")
    @PostMapping("/{orderId}/cancel")
    public OrderDtos.TransitionResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                               @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.CANCEL, request);
    }

    /** body.restock (default true) applies when the order was already shipped. */
    @RequireShopPermission("ORDER_REFUND")
    @PostMapping("/{orderId}/refund/approve")
    public OrderDtos.TransitionResponse approveRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                      @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.APPROVE_REFUND, request);
    }

    /** body.reason required; the order goes back to the status it had before the request. */
    @RequireShopPermission("ORDER_REFUND")
    @PostMapping("/{orderId}/refund/reject")
    public OrderDtos.TransitionResponse rejectRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                     @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.REJECT_REFUND, request);
    }

    private OrderActor actor(Jwt jwt, Long shopId) {
        return OrderActor.merchant(RequestUser.id(jwt), queries.requireShop(shopId).getUuid());
    }

    private OrderDtos.TransitionResponse act(Jwt jwt, Long shopId, UUID orderId, OrderTransitionService.Action action, OrderDtos.TransitionRequest request) {
        var actor = actor(jwt, shopId);
        var result = transitions.transition(orderId, actor, action, request);
        return new OrderDtos.TransitionResponse(result.changed(), result.from().name(), result.to().name(), queries.get(actor, orderId));
    }
}
