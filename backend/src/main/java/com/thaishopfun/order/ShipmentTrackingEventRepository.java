package com.thaishopfun.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, Long> {
    List<ShipmentTrackingEvent> findByShipmentIdOrderByEventAtAscIdAsc(UUID shipmentId);

    boolean existsByShipmentIdAndExternalEventId(UUID shipmentId, String externalEventId);

    /** True when a carrier webhook/poll already reported DELIVERED for this shipment. */
    @Query("""
        select case when count(e) > 0 then true else false end from ShipmentTrackingEvent e
        where e.shipmentId = :shipmentId
          and e.normalizedStatus = 'DELIVERED'
          and e.source in ('CARRIER_WEBHOOK', 'CARRIER_POLL')
        """)
    boolean existsCarrierDeliveredConfirmation(@Param("shipmentId") UUID shipmentId);
}
