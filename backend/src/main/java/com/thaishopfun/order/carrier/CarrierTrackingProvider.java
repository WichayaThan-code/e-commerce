package com.thaishopfun.order.carrier;

import com.thaishopfun.order.ShipmentService;

import java.util.List;
import java.util.Map;

/**
 * SPI for carrier tracking integrations (Kerry, Flash, Thailand Post, …), like PaymentGateway for payments. Each
 * implementation is a Spring bean found by {@link #carrierCode()} (a carriers.code). None ship today, so
 * POST /api/shipping/webhooks/{carrier} answers 404 CARRIER_WEBHOOK_NOT_CONFIGURED until one is registered and
 * {@link #webhookEnabled()} is true (disabled by default).
 * <p>Updates are applied as SYSTEM through ShipmentService (same shipment / order state machines, dedupe by
 * externalEventId per shipment). status = a shipments status (IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED,
 * DELIVERY_FAILED, RETURNED) or null for an informational scan.
 */
public interface CarrierTrackingProvider {
    String carrierCode();

    /** Whether the webhook endpoint is open for this carrier (configured + enabled). Default: closed. */
    default boolean webhookEnabled() { return false; }

    /**
     * Verify the signature over the raw body and parse it. Must throw {@link WebhookSignatureException} when the
     * signature is missing or wrong (nothing is processed then). safePayload must already be redacted (no PII / secrets).
     */
    List<ShipmentService.CarrierUpdate> verifyWebhook(Map<String, String> headers, byte[] body);

    /** Future polling (CARRIER_POLL): current events for a tracking number. Default: not supported. */
    default List<ShipmentService.CarrierUpdate> poll(String trackingNumber) { return List.of(); }

    class WebhookSignatureException extends RuntimeException {
        public WebhookSignatureException(String message) { super(message); }
    }
}
