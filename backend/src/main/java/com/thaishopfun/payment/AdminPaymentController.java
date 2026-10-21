package com.thaishopfun.payment;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.order.OrderActor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform support view of payments and refunds. No PLATFORM_ORDER_* / PLATFORM_PAYMENT_* codes in the seed:
 * read = PLATFORM_SHOP_READ, interventions = PLATFORM_SHOP_SUSPEND (as for admin order transitions); every
 * intervention requires a reason.
 */
@RestController
@RequestMapping("/api/admin/orders/{orderId}")
public class AdminPaymentController {
    private final OrderPaymentService payments;
    private final RefundService refunds;

    public AdminPaymentController(OrderPaymentService payments, RefundService refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/payments")
    public List<PaymentDtos.PaymentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return payments.list(admin(jwt), orderId, base(orderId));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/payments/proofs/{proofId}/file")
    public ResponseEntity<Resource> slip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID proofId) {
        return payments.proofFile(admin(jwt), orderId, proofId);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentDtos.PaymentActionResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID paymentId,
                                                     @RequestBody(required = false) PaymentDtos.PaymentActionRequest request) {
        requireReason(request == null ? null : request.reason());
        return payments.confirm(admin(jwt), orderId, paymentId, request, base(orderId));
    }

    /** COD cash collected recorded by an admin (e.g. carrier remittance report); reason required. */
    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/cod/collect")
    public PaymentDtos.PaymentActionResponse collectCod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                        @RequestBody(required = false) PaymentDtos.CodCollectRequest request) {
        var req = request == null ? new PaymentDtos.CodCollectRequest(null, null, null, null, null, null) : request;
        requireReason(req.reason());
        return payments.collectCod(admin(jwt), orderId, req.amount() == null ? null : req.amount().toPlainString(), req.collectedAt(),
            req.reference(), req.reason(), req.acceptAmountMismatch(), req.expectedVersion(), null, base(orderId));
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/payments/{paymentId}/reject")
    public PaymentDtos.PaymentActionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID paymentId,
                                                    @RequestBody(required = false) PaymentDtos.PaymentActionRequest request) {
        return payments.reject(admin(jwt), orderId, paymentId, request, base(orderId));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/refunds")
    public PaymentDtos.RefundSummary refunds(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return refunds.summary(admin(jwt), orderId, base(orderId));
    }

    @RequirePermission("PLATFORM_SHOP_READ")
    @GetMapping("/refunds/{refundId}/proof-file")
    public ResponseEntity<Resource> refundProof(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId) {
        return refunds.proofFile(admin(jwt), orderId, refundId);
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/refunds/{refundId}/approve")
    public PaymentDtos.RefundActionResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId,
                                                    @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.approve(admin(jwt), orderId, refundId, request, base(orderId));
    }

    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/refunds/{refundId}/reject")
    public PaymentDtos.RefundActionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId,
                                                   @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.reject(admin(jwt), orderId, refundId, request, base(orderId));
    }

    /** Close a disputed / unacknowledged refund as COMPLETED (reason required). */
    @RequirePermission("PLATFORM_SHOP_SUSPEND")
    @PostMapping("/refunds/{refundId}/resolve")
    public PaymentDtos.RefundActionResponse resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId,
                                                    @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.adminResolve(admin(jwt), orderId, refundId, request, base(orderId));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new com.thaishopfun.common.ValidationException(List.of(new com.thaishopfun.common.FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผล")));
        }
    }

    private static OrderActor admin(Jwt jwt) {
        return OrderActor.admin(RequestUser.id(jwt));
    }

    private static String base(UUID orderId) {
        return "/api/admin/orders/" + orderId;
    }
}
