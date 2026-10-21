package com.thaishopfun.payment;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequireShopPermission;
import com.thaishopfun.order.OrderActor;
import com.thaishopfun.order.OrderQueryService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Shop side: verify / reject transfer slips and handle refunds of its orders. {shopId} = public shop number; orders of
 * other shops answer 404. No PAYMENT_* codes exist in the seed: reading uses ORDER_READ, slip decisions ORDER_UPDATE,
 * refunds ORDER_REFUND. Money details are visible before confirm-accept (V1 design; see MerchantVisibility).
 */
@RestController
@RequestMapping("/api/merchant/shops/{shopId}/orders/{orderId}")
public class MerchantPaymentController {
    private final OrderPaymentService payments;
    private final RefundService refunds;
    private final OrderQueryService queries;

    public MerchantPaymentController(OrderPaymentService payments, RefundService refunds, OrderQueryService queries) {
        this.payments = payments;
        this.refunds = refunds;
        this.queries = queries;
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/payments")
    public List<PaymentDtos.PaymentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return payments.list(actor(jwt, shopId), orderId, base(shopId, orderId));
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/payments/proofs/{proofId}/file")
    public ResponseEntity<Resource> slip(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId, @PathVariable UUID proofId) {
        return payments.proofFile(actor(jwt, shopId), orderId, proofId);
    }

    /** Slip checked: order → PAID_AWAITING_MERCHANT_CONFIRM. Amount mismatch needs acceptAmountMismatch=true + reason. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentDtos.PaymentActionResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                     @PathVariable UUID paymentId, @RequestBody(required = false) PaymentDtos.PaymentActionRequest request) {
        return payments.confirm(actor(jwt, shopId), orderId, paymentId, request, base(shopId, orderId));
    }

    /** reason required; the order stays PENDING_PAYMENT and the buyer may send a new slip. */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping("/payments/{paymentId}/reject")
    public PaymentDtos.PaymentActionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                    @PathVariable UUID paymentId, @RequestBody(required = false) PaymentDtos.PaymentActionRequest request) {
        return payments.reject(actor(jwt, shopId), orderId, paymentId, request, base(shopId, orderId));
    }

    /**
     * COD: cash collected for a DELIVERED order → payment CAPTURED / order PAID. JSON {amount, collectedAt?, reference?,
     * reason?, acceptAmountMismatch?, expectedVersion?}; 409 NOT_COD_ORDER / INVALID_STATUS_TRANSITION / PAYMENT_AMOUNT_MISMATCH.
     */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping(value = "/cod/collect", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PaymentDtos.PaymentActionResponse collectCod(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                        @RequestBody(required = false) PaymentDtos.CodCollectRequest request) {
        var req = request == null ? new PaymentDtos.CodCollectRequest(null, null, null, null, null, null) : request;
        return payments.collectCod(actor(jwt, shopId), orderId, req.amount() == null ? null : req.amount().toPlainString(), req.collectedAt(),
            req.reference(), req.reason(), req.acceptAmountMismatch(), req.expectedVersion(), null, base(shopId, orderId));
    }

    /** Same as JSON, multipart with an optional evidence file (receipt / carrier COD remittance; JPG, PNG, WEBP, PDF). */
    @RequireShopPermission("ORDER_UPDATE")
    @PostMapping(value = "/cod/collect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PaymentDtos.PaymentActionResponse collectCodWithFile(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                                @RequestPart(value = "file", required = false) MultipartFile file,
                                                                @RequestParam(required = false) String amount,
                                                                @RequestParam(required = false) String collectedAt,
                                                                @RequestParam(required = false) String reference,
                                                                @RequestParam(required = false) String reason,
                                                                @RequestParam(required = false) Boolean acceptAmountMismatch,
                                                                @RequestParam(required = false) Integer expectedVersion) {
        return payments.collectCod(actor(jwt, shopId), orderId, amount, collectedAt, reference, reason, acceptAmountMismatch,
            expectedVersion, file, base(shopId, orderId));
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/refunds")
    public PaymentDtos.RefundSummary refunds(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId) {
        return refunds.summary(actor(jwt, shopId), orderId, base(shopId, orderId));
    }

    @RequireShopPermission("ORDER_READ")
    @GetMapping("/refunds/{refundId}/proof-file")
    public ResponseEntity<Resource> refundProof(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId, @PathVariable UUID refundId) {
        return refunds.proofFile(actor(jwt, shopId), orderId, refundId);
    }

    @RequireShopPermission("ORDER_REFUND")
    @PostMapping("/refunds/{refundId}/approve")
    public PaymentDtos.RefundActionResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                    @PathVariable UUID refundId, @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.approve(actor(jwt, shopId), orderId, refundId, request, base(shopId, orderId));
    }

    @RequireShopPermission("ORDER_REFUND")
    @PostMapping("/refunds/{refundId}/reject")
    public PaymentDtos.RefundActionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                   @PathVariable UUID refundId, @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.reject(actor(jwt, shopId), orderId, refundId, request, base(shopId, orderId));
    }

    /** multipart: file (refund slip), reference?, transferredAt?. The shop has already transferred the money itself. */
    @RequireShopPermission("ORDER_REFUND")
    @PostMapping(value = "/refunds/{refundId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PaymentDtos.RefundActionResponse proof(@AuthenticationPrincipal Jwt jwt, @PathVariable Long shopId, @PathVariable UUID orderId,
                                                  @PathVariable UUID refundId, @RequestPart(value = "file", required = false) MultipartFile file,
                                                  @RequestParam(required = false) String reference, @RequestParam(required = false) String transferredAt) {
        return refunds.uploadProof(actor(jwt, shopId), orderId, refundId, file, reference, transferredAt, base(shopId, orderId));
    }

    private OrderActor actor(Jwt jwt, Long shopId) {
        return OrderActor.merchant(RequestUser.id(jwt), queries.requireShop(shopId).getUuid());
    }

    private static String base(Long shopId, UUID orderId) {
        return "/api/merchant/shops/" + shopId + "/orders/" + orderId;
    }
}
