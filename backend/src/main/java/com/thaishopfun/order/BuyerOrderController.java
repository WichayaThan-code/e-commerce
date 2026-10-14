package com.thaishopfun.order;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Buyer's own orders. Orders of other buyers answer 404. */
@RestController
@RequestMapping("/api/orders")
public class BuyerOrderController {
    private final OrderQueryService queries;
    private final OrderTransitionService transitions;

    public BuyerOrderController(OrderQueryService queries, OrderTransitionService transitions) {
        this.queries = queries;
        this.transitions = transitions;
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping
    public OrderDtos.PageResponse<OrderDtos.OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
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
        return queries.list(OrderActor.buyer(RequestUser.id(jwt)), null,
            new OrderQueryService.Filters(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction));
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/{orderId}")
    public OrderDtos.OrderDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.get(OrderActor.buyer(RequestUser.id(jwt)), orderId);
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/{orderId}/timeline")
    public List<OrderDtos.TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.timeline(OrderActor.buyer(RequestUser.id(jwt)), orderId);
    }

    /** Before payment only (BUYER_ORDER_CANCEL: "ก่อนชำระและก่อนเริ่มแพ็ก"). After payment use refund-request. */
    @RequirePermission("BUYER_ORDER_CANCEL")
    @PostMapping("/{orderId}/cancel")
    public OrderDtos.TransitionResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                               @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, orderId, OrderTransitionService.Action.CANCEL, request);
    }

    /** No dedicated buyer refund code in the seed; BUYER_ORDER_CANCEL is the closest buyer order-action permission. */
    @RequirePermission("BUYER_ORDER_CANCEL")
    @PostMapping("/{orderId}/refund-request")
    public OrderDtos.TransitionResponse requestRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                      @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, orderId, OrderTransitionService.Action.REQUEST_REFUND, request);
    }

    /** Buyer confirms receipt: DELIVERED → COMPLETED (completion_source BUYER_CONFIRM). */
    @RequirePermission("BUYER_ORDER_READ")
    @PostMapping("/{orderId}/confirm-received")
    public OrderDtos.TransitionResponse confirmReceived(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                        @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, orderId, OrderTransitionService.Action.COMPLETE, request);
    }

    private OrderDtos.TransitionResponse act(Jwt jwt, UUID orderId, OrderTransitionService.Action action, OrderDtos.TransitionRequest request) {
        var actor = OrderActor.buyer(RequestUser.id(jwt));
        var result = transitions.transition(orderId, actor, action, request);
        return new OrderDtos.TransitionResponse(result.changed(), result.from().name(), result.to().name(), queries.get(actor, orderId));
    }
}
