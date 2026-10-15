package com.thaishopfun.payment;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.order.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refund records for money the shop sends back itself (the platform never moves money).
 * <pre>
 * REQUESTED ──approve──▶ APPROVED ──upload proof──▶ PROOF_UPLOADED ──buyer confirms──▶ COMPLETED
 *     │                     │                            │  ▲
 *     └──────reject─────────┴──▶ REJECTED                buyer disputes ▶ DISPUTED ──shop re-uploads──┘ (admin may resolve → COMPLETED)
 * </pre>
 * Order integration: a buyer / admin refund request moves the order to REFUND_REQUESTED and opens a REQUESTED refund
 * (amount ≤ captured − other refunds; partial allowed). When the shop uploads proof:
 * full amount refunded → order REFUNDED (payment REFUNDED, stock per restock flag);
 * partial → order returns to the status before the request with payment_status PARTIALLY_REFUNDED (another request is
 * possible for the rest). Rejecting returns the order to its previous status. Cancelling a paid order opens an
 * APPROVED refund for the full amount (cannot be rejected; the shop must upload proof).
 */
@Service
public class RefundService {
    private final OrderRepository orders;
    private final RefundRepository refunds;
    private final RefundStatusHistoryRepository refundHistory;
    private final OrderMoneyLedger ledger;
    private final OrderTransitionService transitions;
    private final OrderQueryService queries;
    private final OrderPaymentService paymentsService;
    private final PaymentFileService files;

    public RefundService(OrderRepository orders, RefundRepository refunds, RefundStatusHistoryRepository refundHistory, OrderMoneyLedger ledger,
                         OrderTransitionService transitions, OrderQueryService queries, OrderPaymentService paymentsService, PaymentFileService files) {
        this.orders = orders;
        this.refunds = refunds;
        this.refundHistory = refundHistory;
        this.ledger = ledger;
        this.transitions = transitions;
        this.queries = queries;
        this.paymentsService = paymentsService;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public PaymentDtos.RefundSummary summary(OrderActor actor, UUID orderId, String fileBase) {
        var order = paymentsService.load(actor, orderId);
        return new PaymentDtos.RefundSummary(order.getId(), order.getStatus().name(), order.getPaymentStatus().name(),
            ledger.captured(order.getId()), ledger.refunded(order.getId()), ledger.refundable(order.getId()),
            refunds.findByOrderIdOrderByCreatedDateTimeAsc(order.getId()).stream().map(r -> view(actor, r, fileBase)).toList());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> proofFile(OrderActor actor, UUID orderId, UUID refundId) {
        var order = paymentsService.load(actor, orderId);
        return files.serve(refund(order, refundId).getProofMediaFileId());
    }

    /** The order's single open (REQUESTED / APPROVED) refund — used by the legacy order refund/approve|reject endpoints. */
    @Transactional(readOnly = true)
    public UUID openRefundId(OrderActor actor, UUID orderId) {
        var order = paymentsService.load(actor, orderId);
        return refunds.findByOrderId(order.getId()).stream().filter(r -> RefundStatus.OPEN.contains(r.getStatus())).map(Refund::getId).findFirst()
            .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "NO_OPEN_REFUND", "ไม่มีคำขอคืนเงินที่รอดำเนินการ"));
    }

    // ---------------- shop / admin ----------------

    @Transactional
    public PaymentDtos.RefundActionResponse approve(OrderActor actor, UUID orderId, UUID refundId, PaymentDtos.RefundActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.RefundActionRequest(null, null, null) : body;
        var order = paymentsService.lock(actor, orderId);
        var refund = refund(order, refundId);
        var reason = reason(req.reason(), actor.type() == OrderActorType.ADMIN);
        if (refund.getStatus() == RefundStatus.APPROVED) return response(false, actor, order, refund, fileBase);
        requireStatus(refund, Set.of(RefundStatus.REQUESTED));
        refund.approve(actor.userId(), Instant.now());
        refund.setRestock(req.restock() == null ? Boolean.TRUE : req.restock());
        refund.stamp(actor.userId());
        ledger.refundHistory(refund, RefundStatus.REQUESTED, RefundStatus.APPROVED, "REFUND_APPROVED", actor, reason, Map.of("restock", refund.getRestock()));
        ledger.orderEvent(order, actor, "REFUND_APPROVED", reason, Map.of("refundId", refund.getId(), "amount", refund.getAmount()));
        return response(true, actor, order, refund, fileBase);
    }

    @Transactional
    public PaymentDtos.RefundActionResponse reject(OrderActor actor, UUID orderId, UUID refundId, PaymentDtos.RefundActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.RefundActionRequest(null, null, null) : body;
        var order = paymentsService.lock(actor, orderId);
        var refund = refund(order, refundId);
        if (refund.getStatus() == RefundStatus.REJECTED) return response(false, actor, order, refund, fileBase);
        requireStatus(refund, RefundStatus.OPEN);
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            // cancellation refunds: the shop owes the money, it cannot decline
            throw new AuthException(HttpStatus.CONFLICT, "REFUND_CANNOT_REJECT", "คืนเงินจากการยกเลิกออเดอร์ที่ชำระแล้ว ปฏิเสธไม่ได้ — กรุณาโอนคืนและอัปโหลดหลักฐาน");
        }
        var reason = reason(req.reason(), true);
        // order back to the previous status; the ledger rejects this open refund with the same reason
        transitions.transition(order.getId(), actor, OrderTransitionService.Action.REJECT_REFUND,
            new OrderDtos.TransitionRequest(reason, null, null, null, null, null, null, null, null));
        return response(true, actor, order, refund, fileBase);
    }

    /** The shop transferred the money back itself and uploads the slip (also replaces a proof, e.g. after a dispute). */
    @Transactional
    public PaymentDtos.RefundActionResponse uploadProof(OrderActor actor, UUID orderId, UUID refundId, MultipartFile file, String rawReference,
                                                        String rawTransferredAt, String fileBase) {
        var order = paymentsService.lock(actor, orderId);
        var refund = refund(order, refundId);
        var from = refund.getStatus();
        requireStatus(refund, Set.of(RefundStatus.APPROVED, RefundStatus.PROOF_UPLOADED, RefundStatus.DISPUTED));
        var violations = new ArrayList<FieldViolation>();
        var reference = PaymentAccountService.clean(rawReference);
        if (reference != null && reference.length() > 120) violations.add(new FieldViolation("reference", "MAX_LENGTH", "เลขอ้างอิงยาวเกิน 120 ตัวอักษร"));
        var transferredAt = PaymentAccountService.clean(rawTransferredAt) == null ? Instant.now()
            : OrderPaymentService.transferredAt(rawTransferredAt, null, violations);
        if (file == null || file.isEmpty()) violations.add(new FieldViolation("file", "REQUIRED", "กรุณาแนบสลิปการคืนเงิน"));
        if (!violations.isEmpty()) throw new ValidationException(violations);
        var stored = files.store(order.getId(), order.getShopId(), actor.userId(), file, PaymentFileService.REFUND_PROOF);
        refund.proof(stored.getId(), reference, transferredAt, actor.userId(), Instant.now());
        refund.stamp(actor.userId());
        refunds.saveAndFlush(refund);
        var meta = new LinkedHashMap<String, Object>();
        meta.put("refundId", refund.getId());
        meta.put("amount", refund.getAmount());
        if (reference != null) meta.put("reference", reference);
        ledger.refundHistory(refund, from, RefundStatus.PROOF_UPLOADED, from == RefundStatus.APPROVED ? "REFUND_PROOF_UPLOADED" : "REFUND_PROOF_REPLACED",
            actor, null, meta);
        ledger.orderEvent(order, actor, "REFUND_PROOF_UPLOADED", null, meta);
        if (from == RefundStatus.APPROVED) settle(order, refund, actor);
        return response(true, actor, order, refund, fileBase);
    }

    /** Admin closes a dispute (or a proof the buyer never acknowledged) as completed. */
    @Transactional
    public PaymentDtos.RefundActionResponse adminResolve(OrderActor admin, UUID orderId, UUID refundId, PaymentDtos.RefundActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.RefundActionRequest(null, null, null) : body;
        var order = paymentsService.lock(admin, orderId);
        var refund = refund(order, refundId);
        var reason = reason(req.reason(), true);
        if (refund.getStatus() == RefundStatus.COMPLETED) return response(false, admin, order, refund, fileBase);
        var from = refund.getStatus();
        requireStatus(refund, Set.of(RefundStatus.PROOF_UPLOADED, RefundStatus.DISPUTED));
        refund.complete(Instant.now(), false, null);
        refund.stamp(admin.userId());
        ledger.refundHistory(refund, from, RefundStatus.COMPLETED, "REFUND_RESOLVED_BY_ADMIN", admin, reason, Map.of());
        ledger.orderEvent(order, admin, "REFUND_COMPLETED", reason, Map.of("refundId", refund.getId()));
        return response(true, admin, order, refund, fileBase);
    }

    // ---------------- buyer ----------------

    @Transactional
    public PaymentDtos.RefundActionResponse confirmReceived(OrderActor buyer, UUID orderId, UUID refundId, PaymentDtos.RefundActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.RefundActionRequest(null, null, null) : body;
        var order = paymentsService.lock(buyer, orderId);
        var refund = refund(order, refundId);
        if (refund.getStatus() == RefundStatus.COMPLETED) return response(false, buyer, order, refund, fileBase);
        var from = refund.getStatus();
        requireStatus(refund, Set.of(RefundStatus.PROOF_UPLOADED, RefundStatus.DISPUTED));
        var note = note(req.note(), false);
        refund.complete(Instant.now(), true, note);
        refund.stamp(buyer.userId());
        ledger.refundHistory(refund, from, RefundStatus.COMPLETED, "REFUND_RECEIPT_CONFIRMED", buyer, note, Map.of());
        ledger.orderEvent(order, buyer, "REFUND_RECEIPT_CONFIRMED", note, Map.of("refundId", refund.getId()));
        return response(true, buyer, order, refund, fileBase);
    }

    @Transactional
    public PaymentDtos.RefundActionResponse dispute(OrderActor buyer, UUID orderId, UUID refundId, PaymentDtos.RefundActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.RefundActionRequest(null, null, null) : body;
        var order = paymentsService.lock(buyer, orderId);
        var refund = refund(order, refundId);
        if (refund.getStatus() == RefundStatus.DISPUTED) return response(false, buyer, order, refund, fileBase);
        requireStatus(refund, Set.of(RefundStatus.PROOF_UPLOADED));
        var note = note(req.note() != null ? req.note() : req.reason(), true);
        refund.dispute(Instant.now(), note);
        refund.stamp(buyer.userId());
        ledger.refundHistory(refund, RefundStatus.PROOF_UPLOADED, RefundStatus.DISPUTED, "REFUND_DISPUTED", buyer, note, Map.of());
        ledger.orderEvent(order, buyer, "REFUND_DISPUTED", note, Map.of("refundId", refund.getId()));
        return response(true, buyer, order, refund, fileBase);
    }

    // ---------------- helpers ----------------

    /** First proof for a refund: update payment status and move the order (full → REFUNDED, partial → back). */
    private void settle(Order order, Refund refund, OrderActor actor) {
        var captured = ledger.captured(order.getId());
        var refunded = ledger.refunded(order.getId());
        boolean full = captured.signum() > 0 && refunded.compareTo(captured) >= 0;
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED) {
            if (full) {
                transitions.transition(order.getId(), actor, OrderTransitionService.Action.APPROVE_REFUND,
                    new OrderDtos.TransitionRequest("คืนเงินครบ ฿" + refunded, null, null, null, null, null, null, refund.getRestock(), null));
            } else {
                transitions.transition(order.getId(), actor, OrderTransitionService.Action.REJECT_REFUND,
                    new OrderDtos.TransitionRequest("คืนเงินบางส่วน ฿" + refund.getAmount(), null, null, null, null, null, null, null, null),
                    new OrderTransitionService.Internal(null, false, "PARTIAL_REFUND_COMPLETED"));
                syncAndSave(order, actor);
            }
        } else {
            syncAndSave(order, actor); // e.g. cancelled paid order: only the payment status moves
        }
    }

    private void syncAndSave(Order order, OrderActor actor) {
        ledger.syncPaymentStatus(order, actor);
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        ledger.snapshot(order);
    }

    private Refund refund(Order order, UUID refundId) {
        return refunds.findById(refundId).filter(r -> r.getOrderId().equals(order.getId()))
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "ไม่พบรายการคืนเงิน"));
    }

    private static void requireStatus(Refund refund, Set<RefundStatus> allowed) {
        if (!allowed.contains(refund.getStatus())) {
            throw new AuthException(HttpStatus.CONFLICT, "REFUND_INVALID_STATUS",
                "รายการคืนเงินอยู่ในสถานะ " + refund.getStatus() + " ทำรายการนี้ไม่ได้ (ต้องเป็น " + allowed.stream().map(Enum::name).sorted().toList() + ")");
        }
    }

    private static String reason(String raw, boolean required) {
        var reason = PaymentAccountService.clean(raw);
        if (required && reason == null) throw new ValidationException(List.of(new FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผล")));
        if (reason != null && reason.length() > 500) throw new ValidationException(List.of(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน 500 ตัวอักษร")));
        return reason;
    }

    private static String note(String raw, boolean required) {
        var note = PaymentAccountService.clean(raw);
        if (required && note == null) throw new ValidationException(List.of(new FieldViolation("note", "REQUIRED", "กรุณาระบุรายละเอียด")));
        if (note != null && note.length() > 500) throw new ValidationException(List.of(new FieldViolation("note", "MAX_LENGTH", "ข้อความยาวเกิน 500 ตัวอักษร")));
        return note;
    }

    private PaymentDtos.RefundActionResponse response(boolean changed, OrderActor actor, Order order, Refund refund, String fileBase) {
        return new PaymentDtos.RefundActionResponse(changed, view(actor, refund, fileBase), queries.detail(order, actor));
    }

    PaymentDtos.RefundView view(OrderActor actor, Refund r, String fileBase) {
        var history = refundHistory.findByRefundIdOrderByIdAsc(r.getId()).stream()
            .map(h -> new PaymentDtos.StatusEntry(h.getId(), h.getEvent(), h.getFromStatus(), h.getToStatus(), h.getActorType(),
                OrderPaymentService.hideStaff(actor, h.getActorType(), h.getActorUserId()), h.getReason(), OrderJson.read(h.getMetadataJson()), h.getCreatedDateTime()))
            .toList();
        boolean buyer = actor.type() == OrderActorType.BUYER;
        return new PaymentDtos.RefundView(r.getId(), r.getStatus().name(), r.getAmount(), r.getCurrency(), r.getReason(), r.getRequesterType(),
            buyer && !"BUYER".equals(r.getRequesterType()) ? null : r.getRequestedBy(), r.getCreatedDateTime(), r.getApprovedAt(), r.getRejectedAt(),
            r.getRejectReason(), r.getRestock(), r.getProofReference(), r.getProofTransferredAt(), r.getProofUploadedAt(),
            r.getProofMediaFileId() == null ? null : fileBase + "/refunds/" + r.getId() + "/proof-file", r.getCompletedAt(),
            r.getBuyerAckStatus(), r.getBuyerAckAt(), r.getBuyerAckNote(), history);
    }
}
