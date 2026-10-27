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
    /** UUID-only so literal paths like {@code /bulk/...} are not captured as orderId. */
    private static final String ORDER = "/{orderId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";

    private final OrderQueryService queries;
    private final OrderTransitionService transitions;
    private final OrderTagService tags;
    private final com.thaishopfun.payment.RefundService refunds;

    public MerchantOrderController(OrderQueryService queries, OrderTransitionService transitions, OrderTagService tags,
                                   com.thaishopfun.payment.RefundService refunds) {
        this.refunds = refunds;
        this.queries = queries;
        this.transitions = transitions;
        this.tags = tags;
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
                                                               @RequestParam(required = false) String direction,
                                                               @RequestParam(required = false) String acceptance,
                                                               @RequestParam(required = false) UUID tag,
                                                               @RequestParam(required = false) String paymentMethod,
                                                               @RequestParam(required = false) String carrierCode,
                                                               @RequestParam(required = false) Boolean hasTracking,
                                                               @RequestParam(required = false) String customer) {
        return queries.list(actor(jwt, shopId), null,
            new OrderQueryService.Filters(status, paymentStatus, fulfillmentStatus, from, to, q, page, size, sort, direction,
                acceptance, tag, paymentMethod, carrierCode, hasTracking, customer));
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping(ORDER)
    public OrderDtos.OrderDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.get(actor(jwt, shopId), orderId);
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping(ORDER + "/timeline")
    public List<OrderDtos.TimelineEntry> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.timeline(actor(jwt, shopId), orderId);
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping(ORDER + "/transitions")
    public OrderDtos.AllowedTransitions allowed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return queries.allowed(actor(jwt, shopId), orderId);
    }

    /** Manual payment (bank transfer / PromptPay slip checked by the shop). Gateway callbacks use the SYSTEM actor. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping(ORDER + "/mark-paid")
    public OrderDtos.TransitionResponse markPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                 @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.MARK_PAID, request);
    }

    /**
     * Accept the order (merchant_confirmed_at). Until then the shop sees only id / number / created time. Allowed from
     * PENDING_PAYMENT (status unchanged; a later payment confirm goes straight to MERCHANT_CONFIRMED), from
     * PAID_AWAITING_MERCHANT_CONFIRM (to MERCHANT_CONFIRMED, same as /confirm) and from REFUND_REQUESTED / CANCELLED /
     * REFUNDED (status unchanged, so a refund owed can be settled). Repeating it returns changed=false.
     * body: optional reason (note), expectedVersion. No ORDER_ACCEPT code in the seed; ORDER_UPDATE is used.
     */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping(ORDER + "/accept")
    public OrderDtos.TransitionResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                               @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.ACCEPT, request);
    }

    /** No ORDER_CONFIRM code in the seed; ORDER_UPDATE is used. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping(ORDER + "/confirm")
    public OrderDtos.TransitionResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.CONFIRM, request);
    }

    /** body.status: READY_TO_PACK | PACKING | PACKED. */
    @RequireShopPermission("SHIPMENT_PACK")
    @PostMapping(ORDER + "/process")
    public OrderDtos.TransitionResponse process(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.PROCESS, request);
    }

    /** body.carrierCode + body.trackingNumber required. Stock is committed (COMMIT_SALE) here. */
    @RequireShopPermission("SHIPMENT_DISPATCH")
    @PostMapping(ORDER + "/ship")
    public OrderDtos.TransitionResponse ship(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                             @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.SHIP, request);
    }

    @RequireShopPermission("SHIPMENT_UPDATE")
    @PostMapping(ORDER + "/deliver")
    public OrderDtos.TransitionResponse deliver(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.DELIVER, request);
    }

    /** body.reason required. Paid orders are refunded in full automatically; held stock is released. */
    @RequireShopPermission("ORDER_CANCEL")
    @PostMapping(ORDER + "/cancel")
    public OrderDtos.TransitionResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                               @RequestBody(required = false) OrderDtos.TransitionRequest request) {
        return act(jwt, shopId, orderId, OrderTransitionService.Action.CANCEL, request);
    }

    /** Merchant-only internal note. body.internalNote (null/blank clears) + expectedVersion. */
    @RequireShopPermission("ORDER_UPDATE")
    @PatchMapping(ORDER + "/internal-note")
    public OrderDtos.InternalNoteResponse internalNote(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                       @RequestBody(required = false) OrderDtos.InternalNoteRequest body) {
        return tags.updateInternalNote(actor(jwt, shopId), orderId, body);
    }

    /** Replace tags on one order (max 10). body.tagIds + expectedVersion. */
    @RequireShopPermission("ORDER_UPDATE")
    @PutMapping(ORDER + "/tags")
    public OrderDtos.OrderTagsResponse setTags(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                               @RequestBody(required = false) OrderDtos.OrderTagsRequest body) {
        return tags.setOrderTags(actor(jwt, shopId), orderId, body);
    }

    /**
     * Approve the order's open refund request (REQUESTED to APPROVED). The order becomes REFUNDED only after the shop
     * uploads its refund proof (POST .../refunds/{refundId}/proof). body.restock (default true) applies after dispatch.
     */
    @RequireShopPermission("ORDER_REFUND")
    @PostMapping(ORDER + "/refund/approve")
    public com.thaishopfun.payment.PaymentDtos.RefundActionResponse approveRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                      @RequestBody(required = false) com.thaishopfun.payment.PaymentDtos.RefundActionRequest request) {
        var actor = actor(jwt, shopId);
        return refunds.approve(actor, orderId, refunds.openRefundId(actor, orderId), request, fileBase(shopId, orderId));
    }

    /** body.reason required; the open refund is rejected and the order goes back to the status it had before the request. */
    @RequireShopPermission("ORDER_REFUND")
    @PostMapping(ORDER + "/refund/reject")
    public com.thaishopfun.payment.PaymentDtos.RefundActionResponse rejectRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                     @RequestBody(required = false) com.thaishopfun.payment.PaymentDtos.RefundActionRequest request) {
        var actor = actor(jwt, shopId);
        return refunds.reject(actor, orderId, refunds.openRefundId(actor, orderId), request, fileBase(shopId, orderId));
    }

    private static String fileBase(Long shopId, UUID orderId) {
        return "/api/merchant/shops/" + shopId + "/orders/" + orderId;
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
