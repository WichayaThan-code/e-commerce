package com.thaishopfun.order;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money rows of an order (payments, payment_proofs, refunds) and their status history. Called inside an order
 * transaction that already holds the order row lock, so totals cannot race. Never moves money: it records what the
 * buyer / shop / gateway reported.
 * <ul>
 *   <li>captured = sum of payments in CAPTURED or REFUNDED</li>
 *   <li>refunded = sum of refunds with proof (PROOF_UPLOADED, COMPLETED, DISPUTED, legacy SUCCEEDED)</li>
 *   <li>refundable for a new request = captured − every refund that is not REJECTED / FAILED</li>
 * </ul>
 */
@Service
public class OrderMoneyLedger {
    private final PaymentRepository payments;
    private final PaymentProofRepository proofs;
    private final RefundRepository refunds;
    private final PaymentStatusHistoryRepository paymentHistory;
    private final RefundStatusHistoryRepository refundHistory;
    private final OrderStatusHistoryRepository orderHistory;
    private final OrderFinancialSnapshotRepository snapshots;

    public OrderMoneyLedger(PaymentRepository payments, PaymentProofRepository proofs, RefundRepository refunds,
                            PaymentStatusHistoryRepository paymentHistory, RefundStatusHistoryRepository refundHistory,
                            OrderStatusHistoryRepository orderHistory, OrderFinancialSnapshotRepository snapshots) {
        this.payments = payments;
        this.proofs = proofs;
        this.refunds = refunds;
        this.paymentHistory = paymentHistory;
        this.refundHistory = refundHistory;
        this.orderHistory = orderHistory;
        this.snapshots = snapshots;
    }

    // ---------------- totals ----------------

    public BigDecimal captured(UUID orderId) {
        return payments.findByOrderId(orderId).stream()
            .filter(p -> Payment.CAPTURED.equals(p.getStatus()) || Payment.REFUNDED.equals(p.getStatus()))
            .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal refunded(UUID orderId) {
        return sum(orderId, RefundStatus.REFUNDED);
    }

    /** What a new refund request may still ask for. */
    public BigDecimal refundable(UUID orderId) {
        return captured(orderId).subtract(sum(orderId, RefundStatus.RESERVING)).max(BigDecimal.ZERO);
    }

    private BigDecimal sum(UUID orderId, java.util.Set<RefundStatus> statuses) {
        return refunds.findByOrderId(orderId).stream().filter(r -> statuses.contains(r.getStatus()))
            .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasPendingReview(UUID orderId) {
        return payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(orderId, Payment.PENDING).isPresent();
    }

    int nextAttempt(UUID orderId) {
        return payments.findFirstByOrderIdOrderByAttemptNoDesc(orderId).map(p -> p.getAttemptNo() + 1).orElse(1);
    }

    // ---------------- payments ----------------

    /**
     * Order → PAID: captures the pending slip payment (and verifies its slip) if there is one, reuses a payment a
     * gateway already captured, or records a new captured payment (shop marks paid without a slip).
     */
    Payment capture(Order order, OrderActor actor, String provider, String reference, BigDecimal received, String reason,
                    Map<String, Object> metadata) {
        var now = Instant.now();
        var pending = payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(order.getId(), Payment.PENDING);
        Payment payment;
        if (pending.isPresent()) {
            payment = pending.get();
            payment.capture(received == null ? payment.getAmount() : received, reference, now);
            payment.stamp(actor.userId());
            UUID proofId = null;
            for (var proof : proofs.findByPaymentIdAndStatus(payment.getId(), PaymentProof.PENDING_REVIEW)) {
                proof.verify(actor.userId(), now);
                proof.stamp(actor.userId());
                proofId = proof.getId();
            }
            var meta = new LinkedHashMap<String, Object>();
            meta.put("amount", payment.getAmount());
            if (proofId != null) meta.put("proofId", proofId);
            paymentHistory(payment, Payment.PENDING, Payment.CAPTURED, "PAYMENT_CONFIRMED", actor, reason, meta, proofId);
        } else {
            var already = payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(order.getId(), Payment.CAPTURED);
            if (already.isPresent()) {
                payment = already.get();
            } else {
                payment = Payment.captured(order.getId(), order.getShopId(), provider,
                    received == null ? order.getGrandTotal() : received, reference, now, nextAttempt(order.getId()));
                payment.stamp(actor.userId());
                payments.save(payment);
                paymentHistory(payment, null, Payment.CAPTURED, "PAYMENT_CAPTURED", actor, reason, Map.of("amount", payment.getAmount()), null);
            }
        }
        metadata.put("event", "PAYMENT_CAPTURED");
        metadata.put("paymentId", payment.getId());
        metadata.put("provider", payment.getProvider());
        metadata.put("amount", payment.getAmount());
        if (payment.getAmount().compareTo(order.getGrandTotal()) != 0) metadata.put("amountMismatch", true);
        if (reference != null) metadata.put("paymentReference", reference);
        return payment;
    }

    /** Admin cancels an order whose slip is still waiting for review: the attempt is closed, nothing was captured. */
    void cancelPendingPayment(Order order, OrderActor actor, String reason) {
        payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(order.getId(), Payment.PENDING).ifPresent(payment -> {
            var now = Instant.now();
            payment.setStatus(Payment.CANCELLED);
            payment.stamp(actor.userId());
            for (var proof : proofs.findByPaymentIdAndStatus(payment.getId(), PaymentProof.PENDING_REVIEW)) {
                proof.reject(actor.userId(), now, reason);
                proof.stamp(actor.userId());
            }
            paymentHistory(payment, Payment.PENDING, Payment.CANCELLED, "PAYMENT_CANCELLED", actor, reason, Map.of(), null);
        });
    }

    // ---------------- refunds ----------------

    /** Cancelling a paid order: the shop owes the whole remaining amount back; recorded as an APPROVED refund awaiting proof. */
    Refund openForCancellation(Order order, OrderActor actor, String reason, Map<String, Object> metadata) {
        var amount = refundable(order.getId());
        if (amount.signum() <= 0) return null;
        var refund = newRefund(order, actor, amount, RefundStatus.APPROVED, reason);
        refund.approve(actor.userId(), Instant.now());
        refunds.save(refund);
        refundHistory(refund, null, RefundStatus.APPROVED, "REFUND_OPENED_ON_CANCEL", actor, reason, Map.of("amount", amount));
        metadata.put("refundId", refund.getId());
        metadata.put("refundAmount", amount);
        return refund;
    }

    /** Order → REFUND_REQUESTED: one open request at a time; amount defaults to everything still refundable. */
    Refund openRequest(Order order, OrderActor actor, BigDecimal requested, String reason, Map<String, Object> metadata) {
        if (refunds.findByOrderId(order.getId()).stream().anyMatch(r -> RefundStatus.OPEN.contains(r.getStatus()))) {
            throw new AuthException(HttpStatus.CONFLICT, "REFUND_ALREADY_OPEN", "มีคำขอคืนเงินที่รอดำเนินการอยู่แล้ว");
        }
        var refundable = refundable(order.getId());
        if (refundable.signum() <= 0) {
            throw new AuthException(HttpStatus.CONFLICT, "NOTHING_TO_REFUND", "ออเดอร์นี้ไม่มียอดที่คืนเงินได้");
        }
        var amount = requested == null ? refundable : requested;
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(List.of(new FieldViolation("amount", "INVALID", "ยอดคืนเงินต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง")));
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(refundable) > 0) {
            throw new ValidationException(List.of(new FieldViolation("amount", "EXCEEDS_REFUNDABLE", "ยอดคืนเงินเกินยอดที่คืนได้ ฿" + refundable)));
        }
        var refund = newRefund(order, actor, amount, RefundStatus.REQUESTED, reason);
        refunds.save(refund);
        refundHistory(refund, null, RefundStatus.REQUESTED, "REFUND_REQUESTED", actor, reason, Map.of("amount", amount));
        metadata.put("refundId", refund.getId());
        metadata.put("refundAmount", amount);
        return refund;
    }

    /** Order leaves REFUND_REQUESTED by rejection: every open request is rejected with the same reason. */
    void rejectOpen(Order order, OrderActor actor, String reason) {
        for (var refund : refunds.findByOrderId(order.getId())) {
            if (!RefundStatus.OPEN.contains(refund.getStatus())) continue;
            var from = refund.getStatus();
            refund.reject(actor.userId(), Instant.now(), reason);
            refund.stamp(actor.userId());
            refundHistory(refund, from, RefundStatus.REJECTED, "REFUND_REJECTED", actor, reason, Map.of());
        }
    }

    /** Order → REFUNDED only once the shop has proven refunds covering the whole captured amount. */
    void requireFullyRefunded(Order order) {
        var captured = captured(order.getId());
        var refunded = refunded(order.getId());
        if (captured.signum() > 0 && refunded.compareTo(captured) < 0) {
            throw new AuthException(HttpStatus.CONFLICT, "REFUND_PROOF_REQUIRED",
                "ต้องอัปโหลดหลักฐานการคืนเงินให้ครบ ฿" + captured + " ก่อน (คืนแล้ว ฿" + refunded + ")");
        }
    }

    /** orders.payment_status (and payments.status on a full refund) from captured / refunded totals. */
    public void syncPaymentStatus(Order order, OrderActor actor) {
        var captured = captured(order.getId());
        if (captured.signum() <= 0) return;
        var refunded = refunded(order.getId());
        if (refunded.compareTo(captured) >= 0) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            for (var payment : payments.findByOrderId(order.getId())) {
                if (!Payment.CAPTURED.equals(payment.getStatus())) continue;
                payment.setStatus(Payment.REFUNDED);
                payment.stamp(actor.userId());
                paymentHistory(payment, Payment.CAPTURED, Payment.REFUNDED, "PAYMENT_REFUNDED", actor, null, Map.of("refunded", refunded), null);
            }
        } else if (refunded.signum() > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        } else {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
    }

    private Refund newRefund(Order order, OrderActor actor, BigDecimal amount, RefundStatus status, String reason) {
        var payment = payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(order.getId(), Payment.CAPTURED).orElse(null);
        var refund = new Refund(order.getId(), payment == null ? null : payment.getId(), order.getShopId(), amount, status,
            refunds.findByOrderId(order.getId()).size() + 1, actor.userId(), actor.type(), reason);
        refund.stamp(actor.userId());
        return refund;
    }

    // ---------------- history / snapshot ----------------

    public void paymentHistory(Payment payment, String from, String to, String event, OrderActor actor, String reason,
                               Map<String, Object> metadata, UUID proofId) {
        var entry = new PaymentStatusHistory(payment.getId(), payment.getOrderId(), payment.getShopId(), from, to, event, actor, reason,
            metadata == null || metadata.isEmpty() ? null : OrderJson.write(metadata));
        entry.setProofId(proofId);
        paymentHistory.save(entry);
    }

    public void refundHistory(Refund refund, RefundStatus from, RefundStatus to, String event, OrderActor actor, String reason,
                              Map<String, Object> metadata) {
        refundHistory.save(new RefundStatusHistory(refund.getId(), refund.getOrderId(), refund.getShopId(),
            from == null ? null : from.name(), to.name(), event, actor, reason,
            metadata == null || metadata.isEmpty() ? null : OrderJson.write(metadata)));
    }

    /** Timeline entry for a money event that does not change the order status (from = to = current status). */
    public void orderEvent(Order order, OrderActor actor, String event, String reason, Map<String, Object> metadata) {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("event", event);
        if (metadata != null) meta.putAll(metadata);
        var entry = new OrderStatusHistory(order.getId(), order.getStatus(), order.getStatus(), actor.userId(), actor.type(), reason);
        entry.setMetadataJson(OrderJson.write(meta));
        entry.stamp(actor.userId());
        orderHistory.save(entry);
    }

    /** Upserts order_financial_snapshots from the order. */
    public void snapshot(Order order) {
        var snapshot = snapshots.findByOrderId(order.getId()).orElseGet(() -> new OrderFinancialSnapshot(order.getId(), order.getShopId()));
        snapshot.refresh(order);
        snapshot.stamp(null);
        snapshots.save(snapshot);
    }
}
