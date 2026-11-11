package com.thaishopfun.order.carrier;

import com.thaishopfun.order.OrderActor;
import com.thaishopfun.order.OrderActorType;
import com.thaishopfun.order.Shipment;
import com.thaishopfun.order.ShipmentService;
import com.thaishopfun.order.ShipmentStatus;
import com.thaishopfun.order.ShipmentTimeline;
import com.thaishopfun.order.ShipmentTrackingEventRepository;
import com.thaishopfun.order.ShipmentTransitionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Before a merchant/admin marks an order DELIVERED, ask the carrier (poll + prior webhook/poll events)
 * whether the parcel is actually delivered. SYSTEM / carrier-driven transitions skip this gate.
 */
@Component
public class CarrierDeliveryVerifier {
    private static final String CARRIER_WEBHOOK = "CARRIER_WEBHOOK";
    private static final String CARRIER_POLL = "CARRIER_POLL";

    private final CarrierTrackingRegistry registry;
    private final ShipmentTrackingEventRepository events;
    private final boolean required;

    public CarrierDeliveryVerifier(CarrierTrackingRegistry registry, ShipmentTrackingEventRepository events,
                                   @Value("${app.shipping.require-carrier-delivery-confirmation:true}") boolean required) {
        this.registry = registry;
        this.events = events;
        this.required = required;
    }

    public void requireDelivered(Shipment shipment, OrderActor actor, ShipmentTimeline.Detail incoming) {
        if (!required) return;
        if (actor == null || actor.type() == OrderActorType.SYSTEM) return;
        if (incoming != null && isCarrierSource(incoming.source())) return;
        if (shipment == null) {
            reject(ShipmentStatus.DISPATCHED, "ไม่พบข้อมูลพัสดุสำหรับตรวจกับขนส่ง");
            return;
        }
        if (events.existsCarrierDeliveredConfirmation(shipment.getId())) return;

        var tracking = shipment.getTrackingNumber();
        if (tracking == null || tracking.isBlank()) {
            reject(shipment.statusEnum(), "ยังไม่มีเลขพัสดุสำหรับตรวจกับขนส่ง");
            return;
        }

        var provider = registry.find(shipment.getCarrierCode()).filter(CarrierTrackingProvider::supportsPoll).orElse(null);
        if (provider != null) {
            for (ShipmentService.CarrierUpdate update : provider.poll(tracking)) {
                if (isDeliveredStatus(update.status())) return;
            }
        }

        reject(shipment.statusEnum(), "ตรวจกับขนส่งแล้วยังไม่พบว่าพัสดุส่งถึงแล้ว — ยังบันทึกส่งถึงลูกค้าไม่ได้");
    }

    private static boolean isCarrierSource(String source) {
        return CARRIER_WEBHOOK.equals(source) || CARRIER_POLL.equals(source);
    }

    private static boolean isDeliveredStatus(String status) {
        if (status == null || status.isBlank()) return false;
        try {
            return ShipmentStatus.parse(status) == ShipmentStatus.DELIVERED;
        } catch (IllegalArgumentException ignored) {
            return "DELIVERED".equalsIgnoreCase(status.trim());
        }
    }

    private static void reject(ShipmentStatus from, String message) {
        throw new ShipmentTransitionException("CARRIER_NOT_DELIVERED", message,
            from == null ? ShipmentStatus.DISPATCHED : from, ShipmentStatus.DELIVERED, List.of());
    }
}
