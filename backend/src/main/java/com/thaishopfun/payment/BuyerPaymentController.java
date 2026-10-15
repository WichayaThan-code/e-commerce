package com.thaishopfun.payment;

import com.thaishopfun.auth.RequestUser;
import com.thaishopfun.auth.authz.RequirePermission;
import com.thaishopfun.order.OrderActor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Buyer side of paying an order by direct transfer and following its refunds. Other buyers' orders answer 404. */
@RestController
@RequestMapping("/api/orders/{orderId}")
public class BuyerPaymentController {
    private final OrderPaymentService payments;
    private final RefundService refunds;

    public BuyerPaymentController(OrderPaymentService payments, RefundService refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    /** Amount due + the shop's receiving accounts (only while the order is PENDING_PAYMENT) + payment attempts. */
    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/payment-info")
    public PaymentDtos.PaymentInfo info(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return payments.info(buyer(jwt), orderId, base(orderId));
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/payments")
    public List<PaymentDtos.PaymentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return payments.list(buyer(jwt), orderId, base(orderId));
    }

    /** multipart: file (JPG/PNG/WEBP/PDF ≤ 5 MB), amount, transferredAt, reference, bankName?, provider? (BANK_TRANSFER | PROMPTPAY). */
    @RequirePermission("BUYER_PAYMENT_CREATE")
    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentDtos.PaymentView> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                          @RequestPart(value = "file", required = false) MultipartFile file,
                                                          @RequestParam(required = false) String amount,
                                                          @RequestParam(required = false) String transferredAt,
                                                          @RequestParam(required = false) String reference,
                                                          @RequestParam(required = false) String bankName,
                                                          @RequestParam(required = false) String provider) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(payments.submitSlip(buyer(jwt), orderId, file, amount, transferredAt, reference, bankName, provider, base(orderId)));
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/payments/proofs/{proofId}/file")
    public ResponseEntity<Resource> slip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID proofId) {
        return payments.proofFile(buyer(jwt), orderId, proofId);
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/refunds")
    public PaymentDtos.RefundSummary refunds(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return refunds.summary(buyer(jwt), orderId, base(orderId));
    }

    @RequirePermission("BUYER_ORDER_READ")
    @GetMapping("/refunds/{refundId}/proof-file")
    public ResponseEntity<Resource> refundProof(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId) {
        return refunds.proofFile(buyer(jwt), orderId, refundId);
    }

    /** No buyer refund-acknowledge code in the seed; BUYER_ORDER_READ (same as confirm-received of an order). */
    @RequirePermission("BUYER_ORDER_READ")
    @PostMapping("/refunds/{refundId}/confirm-received")
    public PaymentDtos.RefundActionResponse confirmReceived(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId,
                                                            @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.confirmReceived(buyer(jwt), orderId, refundId, request, base(orderId));
    }

    /** body.note required: the buyer says the money did not arrive / is wrong. */
    @RequirePermission("BUYER_ORDER_READ")
    @PostMapping("/refunds/{refundId}/dispute")
    public PaymentDtos.RefundActionResponse dispute(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @PathVariable UUID refundId,
                                                    @RequestBody(required = false) PaymentDtos.RefundActionRequest request) {
        return refunds.dispute(buyer(jwt), orderId, refundId, request, base(orderId));
    }

    private static OrderActor buyer(Jwt jwt) {
        return OrderActor.buyer(RequestUser.id(jwt));
    }

    private static String base(UUID orderId) {
        return "/api/orders/" + orderId;
    }
}
