package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {
    List<ShipmentTrackingEvent> findByShipmentIdOrderByEventAtAscIdAsc(UUID shipmentId);

    boolean existsByShipmentIdAndExternalEventId(UUID shipmentId, String externalEventId);
}
