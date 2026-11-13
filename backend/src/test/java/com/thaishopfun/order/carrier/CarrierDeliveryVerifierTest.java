package com.thaishopfun.order.carrier;

import com.thaishopfun.order.OrderActor;
import com.thaishopfun.order.Shipment;
import com.thaishopfun.order.ShipmentService;
import com.thaishopfun.order.ShipmentStatus;
import com.thaishopfun.order.ShipmentTimeline;
import com.thaishopfun.order.ShipmentTrackingEventRepository;
import com.thaishopfun.order.ShipmentTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierDeliveryVerifierTest {
    @Mock CarrierTrackingRegistry registry;
    @Mock ShipmentTrackingEventRepository events;
    @Mock CarrierTrackingProvider provider;

    private CarrierDeliveryVerifier verifier(boolean required) {
        return new CarrierDeliveryVerifier(registry, events, required);
    }

    private static Shipment shipment(String carrier, String tracking, ShipmentStatus status) {
        var row = new Shipment(UUID.randomUUID(), UUID.randomUUID(), null);
        row.setCarrierCode(carrier);
        row.setTrackingNumber(tracking);
        row.setStatus(status.name());
        return row;
    }

    @Test
    void skipsWhenDisabledOrSystemOrCarrierSource() {
        var v = verifier(false);
        assertDoesNotThrow(() -> v.requireDelivered(null, OrderActor.merchant(UUID.randomUUID(), UUID.randomUUID()), null));

        var required = verifier(true);
        assertDoesNotThrow(() -> required.requireDelivered(shipment("FLASH", "T1", ShipmentStatus.DISPATCHED),
            OrderActor.system(), null));
        assertDoesNotThrow(() -> required.requireDelivered(shipment("FLASH", "T1", ShipmentStatus.DISPATCHED),
            OrderActor.merchant(UUID.randomUUID(), UUID.randomUUID()),
            new ShipmentTimeline.Detail("CARRIER_WEBHOOK", null, null, null, null, null, null)));
    }

    @Test
    void rejectsWhenCarrierHasNotDelivered() {
        var shipment = shipment("FLASH", "FL123", ShipmentStatus.IN_TRANSIT);
        when(events.existsCarrierDeliveredConfirmation(shipment.getId())).thenReturn(false);
        when(registry.find("FLASH")).thenReturn(Optional.of(provider));
        when(provider.supportsPoll()).thenReturn(true);
        when(provider.poll(anyString())).thenReturn(List.of(update("FL123", "IN_TRANSIT")));

        var ex = assertThrows(ShipmentTransitionException.class,
            () -> verifier(true).requireDelivered(shipment, OrderActor.merchant(UUID.randomUUID(), UUID.randomUUID()), null));
        assertEquals("CARRIER_NOT_DELIVERED", ex.getCode());
    }

    @Test
    void allowsWhenPollReportsDelivered() {
        var shipment = shipment("FLASH", "FL999", ShipmentStatus.OUT_FOR_DELIVERY);
        when(events.existsCarrierDeliveredConfirmation(shipment.getId())).thenReturn(false);
        when(registry.find("FLASH")).thenReturn(Optional.of(provider));
        when(provider.supportsPoll()).thenReturn(true);
        when(provider.poll(anyString())).thenReturn(List.of(update("FL999", "DELIVERED")));

        assertDoesNotThrow(() -> verifier(true).requireDelivered(shipment,
            OrderActor.merchant(UUID.randomUUID(), UUID.randomUUID()), null));
    }

    @Test
    void allowsWhenPriorCarrierEventConfirmedDelivered() {
        var shipment = shipment("KERRY", "KE1", ShipmentStatus.DISPATCHED);
        when(events.existsCarrierDeliveredConfirmation(shipment.getId())).thenReturn(true);

        assertDoesNotThrow(() -> verifier(true).requireDelivered(shipment,
            OrderActor.admin(UUID.randomUUID()), null));
    }

    @Test
    void allowsManualConfirmWhenCarrierHasNoPollProvider() {
        var shipment = shipment("SPX", "TH261461497767E", ShipmentStatus.DISPATCHED);
        when(events.existsCarrierDeliveredConfirmation(shipment.getId())).thenReturn(false);
        when(registry.find("SPX")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> verifier(true).requireDelivered(shipment,
            OrderActor.merchant(UUID.randomUUID(), UUID.randomUUID()), null));
    }

    private static ShipmentService.CarrierUpdate update(String tracking, String status) {
        return new ShipmentService.CarrierUpdate("e-" + status, tracking, status, status, status, null, Instant.now(), new LinkedHashMap<>());
    }
}
