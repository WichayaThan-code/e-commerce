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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Direct transfer to the shop (bank transfer / PromptPay). The buyer submits slip + amount + transfer time + bank
 * reference while the order is PENDING_PAYMENT; the shop verifies (→ order PAID_AWAITING_MERCHANT_CONFIRM through
 * {@link OrderTransitionService}) or rejects with a reason (order stays pending, buyer resubmits). Every step holds the
 * order row lock, so two confirms / confirm + reject cannot both win.
 * <pre>
 * payments.status : (none) → PENDING → CAPTURED → REFUNDED
 *                            PENDING → FAILED (rejected; next slip = new attempt) | CANCELLED (admin cancelled order)
 * payment_proofs  : PENDING_REVIEW → VERIFIED | REJECTED (also when the buyer replaces it)
 * orders.payment_status : UNPAID → PENDING → PAID → PARTIALLY_REFUNDED → REFUNDED ; PENDING → FAILED → PENDING …
 * </pre>
 */
@Service
public class OrderPaymentService {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final int MAX_SLIPS_PER_ORDER = 10;
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._/#-]{3,119}");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentProofRepository proofs;
    private final PaymentStatusHistoryRepository paymentHistory;
    private final OrderMoneyLedger ledger;
    private final OrderTransitionService transitions;
    private final OrderQueryService queries;
    private final PaymentAccountService accounts;
    private final PaymentFileService files;

    public OrderPaymentService(OrderRepository orders, PaymentRepository payments, PaymentProofRepository proofs,
                               PaymentStatusHistoryRepository paymentHistory, OrderMoneyLedger ledger, OrderTransitionService transitions,
                               OrderQueryService queries, PaymentAccountService accounts, PaymentFileService files) {
        this.orders = orders;
        this.payments = payments;
        this.proofs = proofs;
        this.paymentHistory = paymentHistory;
        this.ledger = ledger;
        this.transitions = transitions;
        this.queries = queries;
        this.accounts = accounts;
        this.files = files;
    }

    // ---------------- reads ----------------

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentInfo info(OrderActor actor, UUID orderId, String fileBase) {
        var order = load(actor, orderId);
        boolean awaiting = order.getStatus() == OrderStatus.PENDING_PAYMENT;
        // receiving accounts only while this buyer still has to pay
        var accountList = awaiting ? accounts.activeForBuyer(order.getShopId()) : List.<PaymentDtos.PaymentAccountView>of();
        return new PaymentDtos.PaymentInfo(order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getPaymentStatus().name(),
            order.getGrandTotal(), order.getCurrency(), awaiting && !accountList.isEmpty(), accountList, views(actor, order, fileBase));
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentView> list(OrderActor actor, UUID orderId, String fileBase) {
        return views(actor, load(actor, orderId), fileBase);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> proofFile(OrderActor actor, UUID orderId, UUID proofId) {
        var order = load(actor, orderId);
        var proof = proofs.findById(proofId).filter(p -> p.getOrderId().equals(order.getId()))
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "ไม่พบไฟล์"));
        return files.serve(proof.getMediaFileId());
    }

    // ---------------- buyer: submit / resubmit slip ----------------

    @Transactional
    public PaymentDtos.PaymentView submitSlip(OrderActor buyer, UUID orderId, MultipartFile file, String rawAmount, String rawTransferredAt,
                                              String rawReference, String rawBank, String rawProvider, String fileBase) {
        var order = lock(buyer, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_NOT_AWAITING_PAYMENT", "ออเดอร์นี้ไม่อยู่ในสถานะรอชำระเงิน");
        }
        var active = accounts.activeProviders(order.getShopId());
        if (active.isEmpty()) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_ACCOUNT_NOT_CONFIGURED", "ร้านยังไม่ได้ตั้งค่าบัญชีรับเงิน");
        }
        var violations = new ArrayList<FieldViolation>();
        var amount = amount(rawAmount, violations);
        var transferredAt = transferredAt(rawTransferredAt, order, violations);
        var reference = PaymentAccountService.clean(rawReference);
        if (reference == null) violations.add(new FieldViolation("reference", "REQUIRED", "กรุณาระบุเลขอ้างอิงการโอน"));
        else if (!REFERENCE.matcher(reference).matches()) violations.add(new FieldViolation("reference", "INVALID", "เลขอ้างอิงต้องยาว 4-120 ตัว (ตัวอักษร ตัวเลข . _ / # -)"));
        var bank = PaymentAccountService.clean(rawBank);
        if (bank != null && bank.length() > 100) violations.add(new FieldViolation("bankName", "MAX_LENGTH", "ชื่อธนาคารยาวเกิน 100 ตัวอักษร"));
        var provider = PaymentAccountService.clean(rawProvider) == null ? (active.size() == 1 ? active.get(0) : "BANK_TRANSFER")
            : PaymentAccountService.clean(rawProvider).toUpperCase();
        if (!active.contains(provider)) violations.add(new FieldViolation("provider", "INVALID", "ช่องทางที่ร้านเปิดรับ: " + String.join(", ", active)));
        if (file == null || file.isEmpty()) violations.add(new FieldViolation("file", "REQUIRED", "กรุณาแนบไฟล์สลิป (JPG, PNG, WEBP หรือ PDF)"));
        if (!violations.isEmpty()) throw new ValidationException(violations);

        // a bank reference can back one live slip per shop (rejected slips may be resubmitted)
        boolean usedElsewhere = proofs.findLiveByReference(order.getShopId(), reference).stream().anyMatch(p -> !p.getOrderId().equals(order.getId()))
            || payments.existsByProviderPaymentIdIgnoreCaseAndShopIdAndOrderIdNot(reference, order.getShopId(), order.getId());
        if (usedElsewhere) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_DUPLICATE", "เลขอ้างอิงการโอนนี้ถูกใช้กับออเดอร์อื่นแล้ว", "reference");
        }
        if (proofs.findByOrderIdOrderByCreatedDateTimeAsc(order.getId()).size() >= MAX_SLIPS_PER_ORDER) {
            throw new AuthException(HttpStatus.CONFLICT, "TOO_MANY_SLIPS", "ส่งสลิปได้ไม่เกิน " + MAX_SLIPS_PER_ORDER + " ครั้งต่อออเดอร์ กรุณาติดต่อร้าน");
        }

        var stored = files.store(order.getId(), order.getShopId(), buyer.userId(), file, PaymentFileService.PAYMENT_SLIP);
        var now = Instant.now();
        var pending = payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(order.getId(), Payment.PENDING);
        Payment payment;
        String event;
        if (pending.isPresent() && pending.get().getProvider().equals(provider)) {
            payment = pending.get();
            for (var old : proofs.findByPaymentIdAndStatus(payment.getId(), PaymentProof.PENDING_REVIEW)) {
                old.reject(buyer.userId(), now, "ผู้ซื้อส่งสลิปใหม่แทน");
                old.stamp(buyer.userId());
            }
            proofs.flush(); // the partial unique index must see the replaced slip as REJECTED first
            event = "SLIP_RESUBMITTED";
        } else {
            if (pending.isPresent()) { // switched channel: close the old attempt
                var old = pending.get();
                old.setStatus(Payment.CANCELLED);
                old.stamp(buyer.userId());
                for (var p : proofs.findByPaymentIdAndStatus(old.getId(), PaymentProof.PENDING_REVIEW)) p.reject(buyer.userId(), now, "ผู้ซื้อเปลี่ยนช่องทางชำระ");
                ledger.paymentHistory(old, Payment.PENDING, Payment.CANCELLED, "PAYMENT_REPLACED", buyer, null, Map.of(), null);
                proofs.flush();
            }
            payment = Payment.pending(order.getId(), order.getShopId(), provider, order.getGrandTotal(), nextAttempt(order.getId()));
            payment.stamp(buyer.userId());
            payments.save(payment);
            event = "SLIP_SUBMITTED";
        }
        boolean mismatch = amount.compareTo(order.getGrandTotal()) != 0;
        var proof = new PaymentProof(payment.getId(), order.getId(), order.getShopId(), buyer.userId(), stored.getId(), reference,
            amount, transferredAt, bank, mismatch);
        proof.stamp(buyer.userId());
        proofs.saveAndFlush(proof);

        var meta = new LinkedHashMap<String, Object>();
        meta.put("paymentId", payment.getId());
        meta.put("proofId", proof.getId());
        meta.put("amountDeclared", amount);
        meta.put("reference", reference);
        if (mismatch) meta.put("amountMismatch", true);
        ledger.paymentHistory(payment, event.equals("SLIP_SUBMITTED") ? null : Payment.PENDING, Payment.PENDING, event, buyer, null, meta, proof.getId());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.stamp(buyer.userId());
        orders.saveAndFlush(order);
        ledger.orderEvent(order, buyer, "PAYMENT_" + event, null, meta);
        ledger.snapshot(order);
        return view(buyer, payment, fileBase);
    }

    // ---------------- shop: verify / reject ----------------

    @Transactional
    public PaymentDtos.PaymentActionResponse confirm(OrderActor actor, UUID orderId, UUID paymentId, PaymentDtos.PaymentActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.PaymentActionRequest(null, null, null) : body;
        var order = lock(actor, orderId);
        checkVersion(order, req.expectedVersion());
        var payment = payment(order, paymentId);
        if (Payment.CAPTURED.equals(payment.getStatus()) || Payment.REFUNDED.equals(payment.getStatus())) {
            return response(false, actor, order, payment, fileBase); // repeated confirm
        }
        if (!Payment.PENDING.equals(payment.getStatus())) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING", "รายการชำระนี้ไม่ได้รอการตรวจสอบ (สถานะ " + payment.getStatus() + ")");
        }
        var proof = proofs.findByPaymentIdAndStatus(payment.getId(), PaymentProof.PENDING_REVIEW).stream()
            .max(Comparator.comparing(PaymentProof::getCreatedDateTime))
            .orElseThrow(() -> new AuthException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING", "ไม่มีสลิปที่รอตรวจสอบ"));
        var reason = PaymentAccountService.clean(req.reason());
        if (reason != null && reason.length() > 500) throw new ValidationException(List.of(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน 500 ตัวอักษร")));
        if (proof.isAmountMismatch()) {
            if (!Boolean.TRUE.equals(req.acceptAmountMismatch())) {
                throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH",
                    "ยอดในสลิป ฿" + proof.getAmountDeclared() + " ไม่ตรงกับยอดออเดอร์ ฿" + order.getGrandTotal()
                        + " — ปฏิเสธสลิป หรือยืนยันรับยอดนี้พร้อมเหตุผล (acceptAmountMismatch)");
            }
            if (reason == null) throw new ValidationException(List.of(new FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผลที่รับยอดไม่ตรง")));
        }
        transitions.transition(order.getId(), actor, OrderTransitionService.Action.MARK_PAID,
            new OrderDtos.TransitionRequest(reason, null, null, null, payment.getProvider(), null, null, null, null),
            new OrderTransitionService.Internal(proof.getAmountDeclared(), true, null));
        return response(true, actor, order, payment, fileBase);
    }

    @Transactional
    public PaymentDtos.PaymentActionResponse reject(OrderActor actor, UUID orderId, UUID paymentId, PaymentDtos.PaymentActionRequest body, String fileBase) {
        var req = body == null ? new PaymentDtos.PaymentActionRequest(null, null, null) : body;
        var order = lock(actor, orderId);
        checkVersion(order, req.expectedVersion());
        var payment = payment(order, paymentId);
        if (Payment.FAILED.equals(payment.getStatus())) return response(false, actor, order, payment, fileBase);
        if (!Payment.PENDING.equals(payment.getStatus())) {
            throw new AuthException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING", "รายการชำระนี้ไม่ได้รอการตรวจสอบ (สถานะ " + payment.getStatus() + ")");
        }
        var reason = PaymentAccountService.clean(req.reason());
        if (reason == null) throw new ValidationException(List.of(new FieldViolation("reason", "REQUIRED", "กรุณาระบุเหตุผลที่ปฏิเสธสลิป")));
        if (reason.length() > 500) throw new ValidationException(List.of(new FieldViolation("reason", "MAX_LENGTH", "เหตุผลยาวเกิน 500 ตัวอักษร")));
        var now = Instant.now();
        UUID proofId = null;
        for (var proof : proofs.findByPaymentIdAndStatus(payment.getId(), PaymentProof.PENDING_REVIEW)) {
            proof.reject(actor.userId(), now, reason);
            proof.stamp(actor.userId());
            proofId = proof.getId();
        }
        payment.fail("SLIP_REJECTED");
        payment.stamp(actor.userId());
        ledger.paymentHistory(payment, Payment.PENDING, Payment.FAILED, "PAYMENT_REJECTED", actor, reason, Map.of(), proofId);
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.stamp(actor.userId());
        orders.saveAndFlush(order);
        var meta = new LinkedHashMap<String, Object>();
        meta.put("paymentId", payment.getId());
        if (proofId != null) meta.put("proofId", proofId);
        ledger.orderEvent(order, actor, "PAYMENT_REJECTED", reason, meta);
        ledger.snapshot(order);
        return response(true, actor, order, payment, fileBase);
    }

    // ---------------- helpers ----------------

    private PaymentDtos.PaymentActionResponse response(boolean changed, OrderActor actor, Order order, Payment payment, String fileBase) {
        return new PaymentDtos.PaymentActionResponse(changed, view(actor, payment, fileBase), queries.detail(order, actor));
    }

    private List<PaymentDtos.PaymentView> views(OrderActor actor, Order order, String fileBase) {
        return payments.findByOrderIdOrderByAttemptNoAsc(order.getId()).stream().map(p -> view(actor, p, fileBase)).toList();
    }

    PaymentDtos.PaymentView view(OrderActor actor, Payment payment, String fileBase) {
        var proofViews = proofs.findByOrderIdOrderByCreatedDateTimeAsc(payment.getOrderId()).stream()
            .filter(p -> p.getPaymentId().equals(payment.getId()))
            .map(p -> new PaymentDtos.ProofView(p.getId(), p.getStatus(), p.getReferenceNo(), p.getAmountDeclared(), p.isAmountMismatch(),
                p.getBankName(), p.getTransferredAt(), p.getMediaFileId() == null ? null : fileBase + "/payments/proofs/" + p.getId() + "/file",
                null, p.getCreatedDateTime(), p.getVerifiedAt(), p.getRejectReason())).toList();
        var history = paymentHistory.findByOrderIdOrderByIdAsc(payment.getOrderId()).stream()
            .filter(h -> h.getPaymentId().equals(payment.getId()))
            .map(h -> new PaymentDtos.StatusEntry(h.getId(), h.getEvent(), h.getFromStatus(), h.getToStatus(), h.getActorType(),
                hideStaff(actor, h.getActorType(), h.getActorUserId()), h.getReason(), OrderJson.read(h.getMetadataJson()), h.getCreatedDateTime()))
            .toList();
        return new PaymentDtos.PaymentView(payment.getId(), payment.getProvider(), payment.getStatus(), payment.getAmount(), payment.getCurrency(),
            payment.getAttemptNo(), payment.getCreatedDateTime(), payment.getCapturedAt(), payment.getFailureCode(), proofViews, history);
    }

    static UUID hideStaff(OrderActor viewer, String actorType, UUID userId) {
        return viewer.type() == OrderActorType.BUYER && !"BUYER".equals(actorType) ? null : userId;
    }

    private int nextAttempt(UUID orderId) {
        return payments.findFirstByOrderIdOrderByAttemptNoDesc(orderId).map(p -> p.getAttemptNo() + 1).orElse(1);
    }

    private Payment payment(Order order, UUID paymentId) {
        return payments.findById(paymentId).filter(p -> p.getOrderId().equals(order.getId()))
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "ไม่พบรายการชำระเงิน"));
    }

    static void checkVersion(Order order, Integer expected) {
        if (expected != null && expected != order.getRowVersion()) {
            throw new AuthException(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT",
                "ออเดอร์ถูกแก้ไขไปแล้ว กรุณาโหลดใหม่ (version ปัจจุบัน " + order.getRowVersion() + ")");
        }
    }

    Order load(OrderActor actor, UUID orderId) {
        return orders.findById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
    }

    Order lock(OrderActor actor, UUID orderId) {
        return orders.lockById(orderId).filter(actor::canSee)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "ไม่พบออเดอร์"));
    }

    private static BigDecimal amount(String raw, List<FieldViolation> violations) {
        var value = PaymentAccountService.clean(raw);
        if (value == null) {
            violations.add(new FieldViolation("amount", "REQUIRED", "กรุณาระบุยอดที่โอน"));
            return null;
        }
        try {
            var amount = new BigDecimal(value.replace(",", ""));
            if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.stripTrailingZeros().scale() > 2) {
                violations.add(new FieldViolation("amount", "INVALID", "ยอดโอนต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง"));
                return null;
            }
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException exception) {
            violations.add(new FieldViolation("amount", "INVALID", "ยอดโอนต้องเป็นตัวเลข"));
            return null;
        }
    }

    /** ISO instant / offset date-time, or a local date-time read as Bangkok time; not in the future, not before the order. */
    static Instant transferredAt(String raw, Order order, List<FieldViolation> violations) {
        var value = PaymentAccountService.clean(raw);
        if (value == null) {
            violations.add(new FieldViolation("transferredAt", "REQUIRED", "กรุณาระบุวันเวลาที่โอน"));
            return null;
        }
        Instant at;
        try {
            at = OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException first) {
            try {
                at = LocalDateTime.parse(value).atZone(BANGKOK).toInstant();
            } catch (DateTimeParseException second) {
                violations.add(new FieldViolation("transferredAt", "INVALID", "รูปแบบวันเวลาไม่ถูกต้อง (เช่น 2026-10-14T15:30)"));
                return null;
            }
        }
        if (at.isAfter(Instant.now().plus(Duration.ofMinutes(10)))) {
            violations.add(new FieldViolation("transferredAt", "FUTURE", "เวลาโอนอยู่ในอนาคต"));
        } else if (order != null && order.getPlacedAt() != null && at.isBefore(order.getPlacedAt().minus(Duration.ofHours(1)))) {
            violations.add(new FieldViolation("transferredAt", "BEFORE_ORDER", "เวลาโอนก่อนสั่งซื้อ"));
        }
        return at;
    }
}
