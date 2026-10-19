package com.thaishopfun.payment.gateway;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.order.*;
import com.thaishopfun.payment.PaymentDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook inbox: verify signature → store once per (provider, event_id) (uq_payment_webhook_events_provider_event_id)
 * → process → mark PROCESSED / IGNORED / FAILED. A repeated delivery returns duplicate=true and does nothing.
 * Capture goes through OrderSystemService.paymentCaptured (SYSTEM actor, normal state machine, idempotent).
 */
@Service
public class PaymentWebhookService {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGatewayRegistry registry;
    private final PaymentWebhookEventRepository events;
    private final OrderSystemService system;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final TransactionTemplate tx;

    public PaymentWebhookService(PaymentGatewayRegistry registry, PaymentWebhookEventRepository events, OrderSystemService system,
                                 OrderRepository orders, PaymentRepository payments, TransactionTemplate tx) {
        this.registry = registry;
        this.events = events;
        this.system = system;
        this.orders = orders;
        this.payments = payments;
        this.tx = tx;
    }

    public PaymentDtos.WebhookAck handle(String provider, Map<String, String> headers, byte[] body) {
        var gateway = registry.webhook(provider)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "WEBHOOK_NOT_CONFIGURED", "ไม่พบ endpoint"));
        PaymentGateway.WebhookEvent event;
        try {
            event = gateway.verifyWebhook(headers, body == null ? new byte[0] : body);
        } catch (PaymentGateway.WebhookSignatureException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID", "ลายเซ็น webhook ไม่ถูกต้อง");
        }
        if (event == null || event.eventId() == null || event.eventId().isBlank() || event.type() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "ข้อมูล webhook ไม่ครบ");
        }
        var code = gateway.provider().toUpperCase();
        PaymentWebhookEvent row;
        try {
            row = tx.execute(status -> {
                if (events.findByProviderAndEventId(code, event.eventId()).isPresent()) return null;
                return events.saveAndFlush(new PaymentWebhookEvent(code, event.eventId(), event.type(), event.orderId(), event.safePayload()));
            });
        } catch (DataIntegrityViolationException raced) {
            row = null; // concurrent delivery of the same event won the insert
        }
        if (row == null) {
            var existing = events.findByProviderAndEventId(code, event.eventId()).map(PaymentWebhookEvent::getProcessStatus).orElse("RECEIVED");
            return new PaymentDtos.WebhookAck(true, true, existing);
        }
        String result;
        java.util.UUID shopId = null;
        java.util.UUID paymentId = null;
        if (!"payment.captured".equals(event.type()) || event.orderId() == null) {
            result = "IGNORED";
        } else {
            try {
                var outcome = system.paymentCaptured(event.orderId(), code, event.providerPaymentId(), event.amount());
                shopId = outcome.order().getShopId();
                paymentId = payments.findFirstByOrderIdAndStatusOrderByAttemptNoDesc(event.orderId(), Payment.CAPTURED).map(Payment::getId).orElse(null);
                result = "PROCESSED";
            } catch (RuntimeException exception) {
                log.warn("payment webhook {} {} not applied: {}", code, event.eventId(), exception.getMessage());
                shopId = orders.findById(event.orderId()).map(Order::getShopId).orElse(null);
                result = "FAILED";
            }
        }
        var finalResult = result;
        var finalShop = shopId;
        var finalPayment = paymentId;
        var id = row.getId();
        tx.executeWithoutResult(status -> events.findById(id).ifPresent(e -> {
            e.processed(finalResult, finalShop, finalPayment, Instant.now());
            events.save(e);
        }));
        return new PaymentDtos.WebhookAck(true, false, result);
    }
}
