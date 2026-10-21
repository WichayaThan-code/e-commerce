package com.thaishopfun.order.carrier;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.order.ShipmentDtos;
import com.thaishopfun.order.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Carrier webhook inbox: provider must exist and be enabled (else 404), signature verified (else 401), then every
 * update is applied in its own transaction (one bad update does not undo the others). Repeats are DUPLICATE no-ops.
 */
@Service
public class CarrierWebhookService {
    private static final Logger log = LoggerFactory.getLogger(CarrierWebhookService.class);
    static final int MAX_UPDATES = 500;

    private final CarrierTrackingRegistry registry;
    private final ShipmentService shipments;

    public CarrierWebhookService(CarrierTrackingRegistry registry, ShipmentService shipments) {
        this.registry = registry;
        this.shipments = shipments;
    }

    public ShipmentDtos.WebhookAck handle(String carrier, Map<String, String> headers, byte[] body) {
        var provider = registry.webhook(carrier)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "CARRIER_WEBHOOK_NOT_CONFIGURED", "ไม่พบ endpoint"));
        java.util.List<ShipmentService.CarrierUpdate> updates;
        try {
            updates = provider.verifyWebhook(headers, body == null ? new byte[0] : body);
        } catch (CarrierTrackingProvider.WebhookSignatureException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID", "ลายเซ็น webhook ไม่ถูกต้อง");
        }
        if (updates == null || updates.size() > MAX_UPDATES) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "WEBHOOK_PAYLOAD_INVALID", "ข้อมูล webhook ไม่ถูกต้อง");
        }
        int applied = 0, duplicates = 0, recorded = 0, unmatched = 0, failed = 0;
        for (var update : updates) {
            try {
                switch (shipments.applyCarrierUpdate(provider.carrierCode(), update)) {
                    case APPLIED -> applied++;
                    case DUPLICATE -> duplicates++;
                    case RECORDED -> recorded++;
                    case UNMATCHED -> unmatched++;
                }
            } catch (RuntimeException exception) {
                // rolled back on its own; usually a concurrent change or an update the order cannot take
                log.warn("carrier update {} {} not applied: {}", provider.carrierCode(), update.externalEventId(), exception.getMessage());
                failed++;
            }
        }
        return new ShipmentDtos.WebhookAck(true, updates.size(), applied, duplicates, recorded, unmatched, failed);
    }
}
