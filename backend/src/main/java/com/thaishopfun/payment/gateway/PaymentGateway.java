package com.thaishopfun.payment.gateway;

import com.thaishopfun.order.Order;
import com.thaishopfun.order.Payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * SPI for payment channels. Each implementation is a Spring bean; {@link PaymentGatewayRegistry} looks them up by
 * {@link #provider()} (a ck_payments_provider value: OMISE, 2C2P, PROMPTPAY, BANK_TRANSFER, CUSTOM).
 * A gateway that money flows through must verify webhook signatures itself and report captures; the platform then
 * calls OrderSystemService.paymentCaptured, which is idempotent (repeated / duplicate events change nothing).
 */
public interface PaymentGateway {
    String provider();

    /** Whether POST /api/payments/webhooks/{provider} is open for this provider (configured + enabled). */
    boolean webhookEnabled();

    /** Start a payment for an order awaiting payment (redirect / QR / instructions). */
    PaymentIntent createPaymentIntent(Order order);

    /**
     * Verify the signature over the raw body and parse the event. Must throw {@link WebhookSignatureException}
     * when the signature is missing or wrong (nothing is stored or processed then).
     */
    WebhookEvent verifyWebhook(Map<String, String> headers, byte[] body);

    /** Ask the provider for the current status of a payment (reconciliation). */
    PaymentStatusResult queryStatus(Payment payment);

    record PaymentIntent(String provider, String intentId, BigDecimal amount, String currency, Map<String, Object> instructions) {}

    /**
     * Parsed, trusted event. type "payment.captured" captures; other types are stored and ignored.
     * safePayload must already be redacted (no card data / secrets) — it is stored in payment_webhook_events.payload_safe.
     */
    record WebhookEvent(String eventId, String type, UUID orderId, String providerPaymentId, BigDecimal amount, Map<String, Object> safePayload) {}

    record PaymentStatusResult(String status, String providerPaymentId, BigDecimal amount) {}

    class WebhookSignatureException extends RuntimeException {
        public WebhookSignatureException(String message) { super(message); }
    }
}
